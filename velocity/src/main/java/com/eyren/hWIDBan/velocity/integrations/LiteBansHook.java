package com.eyren.hWIDBan.velocity.integrations;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.util.FingerprintUtil;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.proxy.Player;
import litebans.api.Entry;
import litebans.api.Events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integrazione LiteBans → HWIDBan per Velocity.
 *
 * Stessa logica del Paper plugin: quando LiteBans (proxy version) registra un ban,
 * propaga l'azione sul DB HWID. Se Paper e Velocity condividono lo stesso DB MySQL,
 * basta una sola integrazione attiva — l'altra è ridondante ma innocua (idempotente).
 */
public class LiteBansHook {

    private final VelocityPlugin plugin;
    private boolean              registered;

    public LiteBansHook(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    public void tryRegister() {
        if (!plugin.getVelocityConfig().getBoolean("integrations.litebans.enabled", true)) {
            plugin.getLogger().info("[LiteBans] Integration disabled in config.");
            return;
        }

        if (plugin.getServer().getPluginManager().getPlugin("litebans").isEmpty()) {
            plugin.getLogger().info("[LiteBans] Plugin not detected on Velocity — skipping integration.");
            return;
        }

        try {
            Class.forName("litebans.api.Events");
            Class.forName("litebans.api.Entry");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warn("[LiteBans] Plugin found but API missing: {}", e.getMessage());
            return;
        }

        Events.get().register(new Events.Listener() {
            @Override public void entryAdded(Entry entry)   { handleAdd(entry); }
            @Override public void entryRemoved(Entry entry) { handleRemove(entry); }
        });
        registered = true;
        plugin.getLogger().info("[LiteBans] Integration enabled — proxy bans propagate to HWID.");
    }

    public boolean isRegistered() { return registered; }

    /* ─── ENTRY ADDED ─────────────────────────────────────────────────── */

    private void handleAdd(Entry entry) {
        List<String> allowed = plugin.getVelocityConfig().getStringList(
                "integrations.litebans.types", List.of("ban", "ipban"));

        String type = entry.getType() != null ? entry.getType().toLowerCase() : "";
        if (!allowed.contains(type)) return;

        String uuidStr = entry.getUuid();
        if (uuidStr == null || uuidStr.isEmpty()) {
            plugin.getLogger().info("[LiteBans→HWID] Entry {} without UUID — skipping HWID ban.", type);
            return;
        }

        String name = resolveName(uuidStr);
        String reasonPrefix = plugin.getVelocityConfig().getString(
                "integrations.litebans.reason-prefix", "[LiteBans] ");
        String reason = reasonPrefix + (entry.getReason() != null ? entry.getReason() : "Banned");
        String executor = entry.getExecutorName() != null ? entry.getExecutorName() : "CONSOLE";

        long durationSec = entry.getDuration();
        boolean syncDuration = plugin.getVelocityConfig().getBoolean(
                "integrations.litebans.sync-duration", true);
        long durationMs = (!syncDuration || durationSec <= 0)
                ? 0L
                : TimeUnit.SECONDS.toMillis(durationSec);

        plugin.getServer().getScheduler().buildTask(plugin,
                () -> performHwidBan(uuidStr, name, reason, executor, durationMs)).schedule();
    }

    private void performHwidBan(String uuidStr, String name, String reason, String by, long durationMs) {
        UUID uuid;
        try { uuid = UUID.fromString(uuidStr); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warn("[LiteBans→HWID] Invalid UUID: {}", uuidStr);
            return;
        }

        Optional<Player> online = plugin.getServer().getPlayer(uuid);

        // Bypass check: skip se il player online ha hwidban.bypass
        if (online.isPresent() && online.get().hasPermission("hwidban.bypass")) {
            plugin.getLogger().info("[LiteBans→HWID] {} has hwidban.bypass — skipping HWID ban.", name);
            return;
        }

        String fingerprint;

        if (online.isPresent()) {
            // Genera fingerprint live dai dati di connessione
            Player target = online.get();
            String ip = target.getRemoteAddress().getAddress().getHostAddress();
            int    mask = plugin.getVelocityConfig().getInt("fingerprint.subnet-mask", 20);
            int    prot = target.getProtocolVersion().getProtocol();
            GeoIPClient.GeoData geo = plugin.getGeoIP().lookup(ip);
            String raw  = FingerprintUtil.buildRaw(ip, mask, geo, prot);
            fingerprint = FingerprintUtil.sha256(raw);
            plugin.getDb().saveFingerprint(uuidStr, fingerprint, raw);
        } else {
            List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuidStr);
            if (fps.isEmpty()) {
                String placeholder = "litebans-anchor-" + uuidStr;
                boolean ok = plugin.getBanManager().ban(placeholder, name, uuidStr, reason, by, durationMs);
                if (ok) plugin.getLogger().info("[LiteBans→HWID] UUID-anchor ban created for {} (no FP history)", name);
                return;
            }
            fingerprint = fps.get(0);
        }

        boolean ok = plugin.getBanManager().ban(fingerprint, name, uuidStr, reason, by, durationMs);
        if (ok) {
            plugin.getLogger().info("[LiteBans→HWID] Banned {} (fp={}..., dur={})",
                    name,
                    fingerprint.substring(0, Math.min(8, fingerprint.length())),
                    durationMs == 0 ? "PERM" : durationMs + "ms");
        }
    }

    /* ─── ENTRY REMOVED ───────────────────────────────────────────────── */

    private void handleRemove(Entry entry) {
        if (!plugin.getVelocityConfig().getBoolean("integrations.litebans.auto-unban", true)) return;

        String type = entry.getType() != null ? entry.getType().toLowerCase() : "";
        if (!"ban".equals(type) && !"ipban".equals(type)) return;

        String uuidStr = entry.getUuid();
        if (uuidStr == null) return;

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            int unbanned = 0;
            String placeholder = "litebans-anchor-" + uuidStr;
            if (plugin.getDb().isBanned(placeholder)
                    && plugin.getBanManager().unban(placeholder)) unbanned++;

            List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuidStr);
            for (String fp : fps) {
                if (plugin.getDb().isBanned(fp)
                        && plugin.getBanManager().unban(fp)) unbanned++;
            }
            if (unbanned > 0) {
                plugin.getLogger().info("[LiteBans→HWID] Unbanned {} HWID record(s) for {}",
                        unbanned, resolveName(uuidStr));
            }
        }).schedule();
    }

    /* ─── HELPERS ────────────────────────────────────────────────────── */

    private String resolveName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            Optional<Player> online = plugin.getServer().getPlayer(uuid);
            if (online.isPresent()) return online.get().getUsername();
        } catch (Exception ignored) {}
        // Fallback: cerca nello storico ban
        String fromDb = plugin.getDb().findNameByUuid(uuidStr);
        if (fromDb != null) return fromDb;
        return uuidStr.length() >= 8 ? uuidStr.substring(0, 8) : uuidStr;
    }
}
