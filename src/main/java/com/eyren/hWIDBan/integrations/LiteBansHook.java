package com.eyren.hWIDBan.integrations;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import litebans.api.Entry;
import litebans.api.Events;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integrazione LiteBans → HWIDBan.
 *
 * Quando LiteBans registra una voce (/ban, /tempban, /ipban), si aggancia a {@link Events}
 * e propaga automaticamente l'azione sul sistema HWID:
 *   - Player online  → genera fingerprint live → ban HWID
 *   - Player offline → cerca ultimo fingerprint salvato → ban HWID
 *   - Nessun FP storico → crea ban "ancora" su placeholder fingerprint legato all'UUID
 *     così il check {@code getActiveBanByUUID()} al prossimo login lo blocca comunque
 *
 * Quando LiteBans rimuove un ban (/unban) → rimuove anche il ban HWID per tutti
 * i fingerprint dell'UUID + il placeholder.
 *
 * La logica esistente di check al login (PreLoginListener) NON è modificata.
 */
public class LiteBansHook {

    private final Main plugin;
    private boolean    registered;

    public LiteBansHook(Main plugin) {
        this.plugin = plugin;
    }

    public void tryRegister() {
        if (!plugin.getConfig().getBoolean("integrations.litebans.enabled", true)) {
            plugin.getLogger().info("[LiteBans] Integration disabled in config.");
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("LiteBans")) {
            plugin.getLogger().info("[LiteBans] Plugin not detected — skipping integration.");
            return;
        }

        try {
            Class.forName("litebans.api.Events");
            Class.forName("litebans.api.Entry");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[LiteBans] Plugin found but API missing: " + e.getMessage());
            return;
        }

        Events.get().register(new Events.Listener() {
            @Override public void entryAdded(Entry entry)   { handleAdd(entry); }
            @Override public void entryRemoved(Entry entry) { handleRemove(entry); }
        });
        registered = true;
        plugin.getLogger().info("[LiteBans] Integration enabled — /ban, /tempban will trigger HWID ban automatically.");
    }

    public boolean isRegistered() { return registered; }

    /* ─── ENTRY ADDED → BAN HWID ──────────────────────────────────────── */

    private void handleAdd(Entry entry) {
        // Tipi di entry da propagare all'HWID. Default: ban + ipban.
        List<String> allowed = plugin.getConfig().getStringList("integrations.litebans.types");
        if (allowed.isEmpty()) allowed = List.of("ban", "ipban");
        String type = entry.getType() != null ? entry.getType().toLowerCase() : "";
        if (!allowed.contains(type)) return;

        String uuidStr = entry.getUuid();
        if (uuidStr == null || uuidStr.isEmpty()) {
            plugin.getLogger().info("[LiteBans→HWID] Entry " + type + " without UUID — skipping HWID ban.");
            return;
        }

        // Risolvi il nome del player via Bukkit (LiteBans Entry non lo espone)
        String name = resolveName(uuidStr);

        String reasonPrefix = plugin.getConfig().getString(
                "integrations.litebans.reason-prefix", "[LiteBans] ");
        String reason = reasonPrefix + (entry.getReason() != null ? entry.getReason() : "Banned");
        String executor = entry.getExecutorName() != null ? entry.getExecutorName() : "CONSOLE";

        // Durata: getDuration() è in secondi. ≤0 = permanente.
        long durationSec = entry.getDuration();
        boolean syncDuration = plugin.getConfig().getBoolean(
                "integrations.litebans.sync-duration", true);
        long durationMs = (!syncDuration || durationSec <= 0)
                ? 0L
                : TimeUnit.SECONDS.toMillis(durationSec);

        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> performHwidBan(uuidStr, name, reason, executor, durationMs));
    }

    private void performHwidBan(String uuidStr, String name, String reason, String by, long durationMs) {
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[LiteBans→HWID] Invalid UUID: " + uuidStr);
            return;
        }

        // Bypass: skip se il target ha hwidban.bypass o è OP
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.hasPermission("hwidban.bypass")) {
            plugin.getLogger().info("[LiteBans→HWID] " + name + " has hwidban.bypass — skipping HWID ban.");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.isOp()) {
            plugin.getLogger().info("[LiteBans→HWID] " + name + " is OP — skipping HWID ban.");
            return;
        }

        String fingerprint;

        if (online != null) {
            // Player online: genera fingerprint live e lo salva
            FingerprintData data = plugin.getFingerprintGenerator().generate(online);
            fingerprint = data.hash();
            plugin.getDatabaseManager().saveFingerprint(uuidStr, fingerprint, data.rawString());
        } else {
            // Player offline: usa l'ultimo fingerprint registrato
            List<String> fps = plugin.getDatabaseManager().getFingerprintsForPlayer(uuidStr);
            if (fps.isEmpty()) {
                // Nessun FP storico: crea ban "ancora" su placeholder hash legato all'UUID
                // Il PreLoginListener controlla anche getActiveBanByUUID() → questo lo blocca
                String placeholder = "litebans-anchor-" + uuidStr;
                boolean ok = plugin.getBanManager().ban(placeholder, name, uuidStr, reason, by, durationMs);
                if (ok) plugin.getLogger().info("[LiteBans→HWID] Created UUID-anchor ban for " + name + " (no FP history yet)");
                return;
            }
            fingerprint = fps.get(0); // più recente
        }

        boolean ok = plugin.getBanManager().ban(fingerprint, name, uuidStr, reason, by, durationMs);
        if (ok) {
            plugin.getLogger().info("[LiteBans→HWID] Banned " + name + " (fp="
                    + fingerprint.substring(0, Math.min(8, fingerprint.length()))
                    + "..., dur=" + (durationMs == 0 ? "PERM" : durationMs + "ms") + ")");
        }
    }

    /* ─── ENTRY REMOVED → UNBAN HWID ──────────────────────────────────── */

    private void handleRemove(Entry entry) {
        if (!plugin.getConfig().getBoolean("integrations.litebans.auto-unban", true)) return;

        String type = entry.getType() != null ? entry.getType().toLowerCase() : "";
        if (!"ban".equals(type) && !"ipban".equals(type)) return;

        String uuidStr = entry.getUuid();
        if (uuidStr == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int unbanned = 0;
            // Anchor placeholder
            String placeholder = "litebans-anchor-" + uuidStr;
            if (plugin.getDatabaseManager().isBanned(placeholder)
                    && plugin.getBanManager().unban(placeholder)) unbanned++;

            // Fingerprint reali per l'UUID
            List<String> fps = plugin.getDatabaseManager().getFingerprintsForPlayer(uuidStr);
            for (String fp : fps) {
                if (plugin.getDatabaseManager().isBanned(fp)
                        && plugin.getBanManager().unban(fp)) unbanned++;
            }
            if (unbanned > 0) {
                plugin.getLogger().info("[LiteBans→HWID] Unbanned " + unbanned
                        + " HWID record(s) for " + resolveName(uuidStr));
            }
        });
    }

    /* ─── HELPERS ────────────────────────────────────────────────────── */

    /** Risolve il nome player da UUID. Online → Player; offline → OfflinePlayer (cache); fallback → UUID corto. */
    private String resolveName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) return online.getName();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null) return op.getName();
        } catch (Exception ignored) {}
        return uuidStr.length() >= 8 ? uuidStr.substring(0, 8) : uuidStr;
    }
}
