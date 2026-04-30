package com.eyren.hWIDBan.velocity.ban;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.util.DurationParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {

    private final VelocityPlugin plugin;
    private final ConcurrentHashMap<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    public BanManager(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /* ─── BAN / UNBAN ────────────────────────────────────────────────── */

    public boolean ban(String fingerprint, String playerName, String uuid,
                       String reason, String bannedBy, long durationMs) {
        BanEntry existing = plugin.getDb().getBan(fingerprint);
        if (existing != null && !existing.isExpired()) return false;
        if (existing != null) plugin.getDb().removeBan(fingerprint);

        long now     = System.currentTimeMillis();
        long expires = durationMs > 0 ? now + durationMs : 0L;
        BanEntry entry = new BanEntry(fingerprint, playerName, uuid, reason, bannedBy, now, expires);

        boolean added = plugin.getDb().addBan(entry);
        if (added) {
            // Kick if online sul questo proxy
            kickOnlinePlayer(uuid, buildKickMessage(entry));
            // Sync verso altri proxy + backend
            if (plugin.getRedisSync() != null) plugin.getRedisSync().publishBan(entry);
            // Notifica Discord
            if (plugin.getDiscordWebhook() != null) plugin.getDiscordWebhook().sendBan(entry, "BAN");
            // Audit + broadcast
            audit("BAN", entry);
            broadcast("messages.ban.broadcast", placeholders(entry, durationMs));
        }
        return added;
    }

    public boolean unban(String fingerprint) {
        BanEntry existing = plugin.getDb().getBan(fingerprint);
        boolean ok = plugin.getDb().removeBan(fingerprint);
        if (ok) {
            if (plugin.getRedisSync() != null) plugin.getRedisSync().publishUnban(fingerprint);
            if (existing != null) {
                if (plugin.getDiscordWebhook() != null) plugin.getDiscordWebhook().sendBan(existing, "UNBAN");
                audit("UNBAN", existing);
                broadcast("messages.unban.broadcast", placeholders(existing, 0));
            }
        }
        return ok;
    }

    private void kickOnlinePlayer(String uuid, Component msg) {
        try {
            plugin.getServer().getPlayer(UUID.fromString(uuid)).ifPresent(p -> p.disconnect(msg));
        } catch (Exception ignored) {}
    }

    /* ─── AUTO-ALT ───────────────────────────────────────────────────── */

    public void maybeAutoAltBan(String fingerprint, String playerName, String uuid) {
        if (!plugin.getVelocityConfig().getBoolean("security.auto-alt.enabled", false)) return;
        int threshold = plugin.getVelocityConfig().getInt("security.auto-alt.threshold", 4);

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            int count = plugin.getDb().countSharedAccounts(fingerprint);
            if (count >= threshold) {
                String reason = plugin.getVelocityConfig().getString("security.auto-alt.reason",
                        "Auto: %count% accounts").replace("%count%", String.valueOf(count));
                long duration = DurationParser.parseMillis(
                        plugin.getVelocityConfig().getString("security.auto-alt.duration", "perm"));
                ban(fingerprint, playerName, uuid, reason, "CONSOLE-AUTO", Math.max(duration, 0L));
            }
        }).schedule();
    }

    /* ─── KICK MESSAGE ───────────────────────────────────────────────── */

    public Component buildKickMessage(BanEntry entry) {
        String key  = entry.isPermanent() ? "messages.kick.banned" : "messages.kick.tempbanned";
        String tmpl = plugin.getVelocityConfig().getString(key, "&cYou are banned.\n&7Reason: %reason%");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        tmpl = tmpl
                .replace("%reason%",      safe(entry.reason()))
                .replace("%fingerprint%", safe(entry.fingerprint()))
                .replace("%admin%",       safe(entry.bannedBy(), "CONSOLE"))
                .replace("%date%",        sdf.format(new Date(entry.bannedAt())))
                .replace("%duration%",    entry.isPermanent() ? "permanent" : DurationParser.remaining(entry.expiresAt()))
                .replace("%expires%",     entry.isPermanent() ? "never" : sdf.format(new Date(entry.expiresAt())));

        return LegacyComponentSerializer.legacyAmpersand().deserialize(tmpl);
    }

    /* ─── BROADCAST ──────────────────────────────────────────────────── */

    public void broadcast(String key, Map<String, String> ph) {
        String tmpl = plugin.getVelocityConfig().getString(key, "");
        if (tmpl.isEmpty()) return;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            tmpl = tmpl.replace("%" + e.getKey() + "%", e.getValue());
        }
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(tmpl);
        // Console
        plugin.getServer().getConsoleCommandSource().sendMessage(msg);
        // Tutti i player con permesso notify
        for (Player p : plugin.getServer().getAllPlayers()) {
            if (p.hasPermission("hwidban.notify")) p.sendMessage(msg);
        }
    }

    private Map<String, String> placeholders(BanEntry e, long durationMs) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player",      safe(e.playerName(), "?"));
        ph.put("uuid",        safe(e.playerUuid(), "?"));
        ph.put("fingerprint", e.fingerprint());
        ph.put("admin",       safe(e.bannedBy(), "CONSOLE"));
        ph.put("reason",      safe(e.reason(), "-"));
        ph.put("duration",    e.isPermanent() ? "permanent" : DurationParser.format(durationMs));
        return ph;
    }

    /* ─── AUDIT ──────────────────────────────────────────────────────── */

    public void audit(String type, BanEntry entry) {
        plugin.getLogger().info("[AUDIT] {} fp={} player={} uuid={} by={} expires={} reason={}",
                type, entry.fingerprint(), entry.playerName(), entry.playerUuid(),
                entry.bannedBy(),
                entry.isPermanent() ? "PERM" : entry.expiresAt(),
                entry.reason());
    }

    /* ─── RATE LIMITING ──────────────────────────────────────────────── */

    public boolean isRateLimited(String ip) {
        if (!plugin.getVelocityConfig().getBoolean("security.rate-limit.enabled", true)) return false;
        int  max    = plugin.getVelocityConfig().getInt("security.rate-limit.max-attempts", 10);
        long window = plugin.getVelocityConfig().getLong("security.rate-limit.window-seconds", 60) * 1000L;
        AttemptTracker t = attempts.get(ip);
        if (t == null) return false;
        t.prune(window);
        return t.count() >= max;
    }

    public void recordAttempt(String ip) {
        if (!plugin.getVelocityConfig().getBoolean("security.rate-limit.enabled", true)) return;
        attempts.computeIfAbsent(ip, k -> new AttemptTracker()).add();
    }

    public boolean isWatchMode() {
        return plugin.getVelocityConfig().getBoolean("security.watch-mode", false);
    }

    /* ─── PERMISSION ─────────────────────────────────────────────────── */

    public boolean hasPermission(CommandSource src, String node) {
        // Console ha tutti i permessi
        if (src instanceof ConsoleCommandSource) return true;
        return src.hasPermission(node) || src.hasPermission("hwidban.admin");
    }

    /* ─── HELPERS ────────────────────────────────────────────────────── */

    private static String safe(String s)             { return s != null ? s : "-"; }
    private static String safe(String s, String def) { return s != null && !s.isEmpty() ? s : def; }

    private static class AttemptTracker {
        private final java.util.Deque<Long> times = new java.util.ArrayDeque<>();
        synchronized void add()             { times.addLast(System.currentTimeMillis()); }
        synchronized void prune(long winMs) {
            long cut = System.currentTimeMillis() - winMs;
            while (!times.isEmpty() && times.peekFirst() < cut) times.pollFirst();
        }
        synchronized int count() { return times.size(); }
    }
}
