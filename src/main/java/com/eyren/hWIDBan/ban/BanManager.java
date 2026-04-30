package com.eyren.hWIDBan.ban;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import com.eyren.hWIDBan.util.Colors;
import com.eyren.hWIDBan.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {

    private final Main plugin;
    private final ConcurrentHashMap<String, AttemptTracker> attempts = new ConcurrentHashMap<>();

    public BanManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean ban(Player target, String reason, String bannedBy) {
        FingerprintData data = plugin.getFingerprintGenerator().generate(target);
        return ban(data.hash(), target.getName(), target.getUniqueId().toString(), reason, bannedBy, 0L);
    }

    public boolean ban(String fingerprint, String playerName, String uuid,
                       String reason, String bannedBy, long durationMillis) {
        BanEntry existing = plugin.getDatabaseManager().getBan(fingerprint);
        if (existing != null && !existing.isExpired()) return false;
        if (existing != null) plugin.getDatabaseManager().removeBan(fingerprint);

        String finalReason = reason == null || reason.isEmpty()
                ? plugin.getConfig().getString("messages.ban.reason-default", "banned")
                : reason;
        long now = System.currentTimeMillis();
        long expires = durationMillis > 0 ? now + durationMillis : 0L;
        BanEntry entry = new BanEntry(fingerprint, playerName, uuid, finalReason, bannedBy, now, expires);
        boolean added = plugin.getDatabaseManager().addBan(entry);
        if (added) {
            plugin.getRedisSync().publishBan(entry);
            kickMatchingPlayers(fingerprint, entry);
            audit("BAN", entry);
            plugin.getDiscordWebhook().sendBan(entry, "BAN");
        }
        return added;
    }

    public boolean unban(String fingerprint) {
        BanEntry existing = plugin.getDatabaseManager().getBan(fingerprint);
        boolean ok = plugin.getDatabaseManager().removeBan(fingerprint);
        if (ok) {
            plugin.getRedisSync().publishUnban(fingerprint);
            if (existing != null) {
                audit("UNBAN", existing);
                plugin.getDiscordWebhook().sendBan(existing, "UNBAN");
            }
        }
        return ok;
    }

    public List<String> fingerprintsOf(String uuid) {
        return plugin.getDatabaseManager().getFingerprintsForPlayer(uuid);
    }

    public void kickMatchingPlayers(String fingerprint, BanEntry entry) {
        if (isWatchMode()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("hwidban.bypass")) continue;
                FingerprintData fp = plugin.getFingerprintGenerator().generate(online);
                if (fingerprint.equals(fp.hash())) {
                    online.kick(Colors.toComponent(buildKickMessage(entry)));
                }
            }
        });
    }

    public String buildKickMessage(BanEntry entry) {
        Map<String, String> ph = new HashMap<>();
        ph.put("reason", entry.reason() == null ? "-" : entry.reason());
        ph.put("fingerprint", entry.fingerprint());
        ph.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.bannedAt())));
        ph.put("admin", entry.bannedBy() == null ? "CONSOLE" : entry.bannedBy());
        ph.put("duration", entry.isPermanent() ? "permanent" : DurationParser.remaining(entry.expiresAt()));
        ph.put("expires", entry.isPermanent()
                ? "never"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.expiresAt())));
        String key = entry.isPermanent() ? "messages.kick.banned" : "messages.kick.tempbanned";
        String msg = plugin.getConfig().getString(key, plugin.getConfig().getString("messages.kick.banned", "You are banned."));
        for (Map.Entry<String, String> e : ph.entrySet()) {
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        }
        return Colors.process(msg);
    }

    public void broadcast(String messageKey, Map<String, String> placeholders) {
        String msg = plugin.getMessageUtil().get(messageKey, placeholders);
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("hwidban.notify")) p.sendMessage(msg);
        }
    }

    public void audit(String type, BanEntry entry) {
        plugin.getLogger().info("[AUDIT] " + type + " fp=" + entry.fingerprint()
                + " player=" + entry.playerName()
                + " uuid=" + entry.playerUuid()
                + " by=" + entry.bannedBy()
                + " expires=" + (entry.isPermanent() ? "PERM" : entry.expiresAt())
                + " reason=" + entry.reason());
    }

    public boolean isWatchMode() {
        return plugin.getConfig().getBoolean("security.watch-mode", false);
    }

    public boolean isRateLimited(String ip) {
        if (!plugin.getConfig().getBoolean("security.rate-limit.enabled", true)) return false;
        int max = plugin.getConfig().getInt("security.rate-limit.max-attempts", 10);
        long window = plugin.getConfig().getLong("security.rate-limit.window-seconds", 60) * 1000L;
        AttemptTracker tracker = attempts.get(ip);
        if (tracker == null) return false;
        tracker.prune(window);
        return tracker.count() >= max;
    }

    public void recordAttempt(String ip) {
        if (!plugin.getConfig().getBoolean("security.rate-limit.enabled", true)) return;
        attempts.computeIfAbsent(ip, k -> new AttemptTracker()).add();
    }

    public boolean hasPermission(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission("hwidban.admin");
    }

    public void maybeAutoAltBan(String fingerprint, Player player) {
        if (!plugin.getConfig().getBoolean("security.auto-alt.enabled", false)) return;
        int threshold = plugin.getConfig().getInt("security.auto-alt.threshold", 4);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = plugin.getDatabaseManager().countSharedAccounts(fingerprint);
            if (count >= threshold) {
                String reason = plugin.getConfig().getString("security.auto-alt.reason",
                        "Auto-ban: multi-account abuse (%count% accounts)").replace("%count%", String.valueOf(count));
                long duration = DurationParser.parseMillis(
                        plugin.getConfig().getString("security.auto-alt.duration", "perm"));
                ban(fingerprint, player.getName(), player.getUniqueId().toString(),
                        reason, "CONSOLE-AUTO", Math.max(duration, 0L));
            }
        });
    }

    private static class AttemptTracker {
        private final java.util.Deque<Long> times = new java.util.ArrayDeque<>();

        synchronized void add() {
            times.addLast(System.currentTimeMillis());
        }

        synchronized void prune(long windowMs) {
            long cutoff = System.currentTimeMillis() - windowMs;
            while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
        }

        synchronized int count() {
            return times.size();
        }
    }
}
