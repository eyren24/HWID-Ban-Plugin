package com.eyren.hWIDBan.listener;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.collector.GeoIPCollector;
import com.eyren.hWIDBan.database.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;
import java.util.UUID;

public class PreLoginListener implements Listener {

    private final Main plugin;

    public PreLoginListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        UUID uuid = event.getUniqueId();
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : "0.0.0.0";
        int mask = plugin.getConfigManager().subnetMask();
        String subnet = plugin.getIpCollector().getSubnet(ip, mask);
        GeoIPCollector.GeoData geo = plugin.getGeoIPCollector().lookup(ip);

        if (plugin.getWhitelistManager().isWhitelisted(ip, subnet, geo)) return;

        // OP bypass: ops.json è letto in modo sicuro anche in async
        if (Bukkit.getOfflinePlayer(uuid).isOp()) return;

        if (plugin.getBanManager().isRateLimited(ip)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageUtil().colorize(plugin.getConfig()
                            .getString("messages.kick.rate-limited", "&cToo many login attempts.")));
            return;
        }
        plugin.getBanManager().recordAttempt(ip);

        if (plugin.getBanManager().isWatchMode()) return;

        // Primary check: direct UUID lookup in bans table (works even when fingerprint history is empty)
        BanEntry directBan = plugin.getDatabaseManager().getActiveBanByUUID(uuid.toString());
        if (directBan != null && !directBan.isExpired()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    plugin.getBanManager().buildKickMessage(directBan));
            return;
        }

        // Secondary check: fingerprint history → ban lookup
        List<String> history = plugin.getDatabaseManager().getFingerprintsForPlayer(uuid.toString());
        for (String fp : history) {
            BanEntry entry = plugin.getDatabaseManager().getBan(fp);
            if (entry != null && !entry.isExpired()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        plugin.getBanManager().buildKickMessage(entry));
                return;
            }
        }

        BanEntry match = plugin.getDatabaseManager().findBanByRawContains(subnet);
        if (match != null && !match.isExpired()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    plugin.getBanManager().buildKickMessage(match));
        }
    }
}
