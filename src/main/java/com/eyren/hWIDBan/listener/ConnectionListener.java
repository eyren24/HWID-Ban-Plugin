package com.eyren.hWIDBan.listener;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

    private final Main plugin;

    public ConnectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getBehaviorCollector().start(player);

        long delayTicks = Math.max(20L, plugin.getConfig().getLong("fingerprint.join-delay-ticks", 60L));

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!player.isOnline()) return;
            FingerprintData data = plugin.getFingerprintGenerator().generate(player);

            if (plugin.getConfig().getBoolean("settings.auto-register-fingerprint", true)) {
                plugin.getDatabaseManager().saveFingerprint(
                        player.getUniqueId().toString(),
                        data.hash(),
                        data.rawString());
            }

            if (player.hasPermission("hwidban.bypass")) return;

            plugin.getBanManager().maybeAutoAltBan(data.hash(), player);

            if (plugin.getBanManager().isWatchMode()) {
                if (plugin.getDatabaseManager().isBanned(data.hash())) {
                    plugin.getLogger().warning("[WATCH-MODE] " + player.getName()
                            + " ha fingerprint bannato: " + data.hash());
                }
                return;
            }

            if (plugin.getDatabaseManager().isBanned(data.hash())) {
                BanEntry entry = plugin.getDatabaseManager().getBan(data.hash());
                String kickMsg = plugin.getBanManager().buildKickMessage(entry);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.kick(
                            com.eyren.hWIDBan.util.Colors.toComponent(kickMsg));
                });
            }

            if (plugin.getConfigManager().debug()) {
                plugin.getLogger().info("Fingerprint di " + player.getName() + ": " + data.hash());
                plugin.getLogger().info("Raw: " + data.rawString());
            }
        }, delayTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        plugin.getBehaviorCollector().stop(p);
        plugin.getClientCollector().clear(p);
        plugin.getScreenCollector().clear(p);
        plugin.getNetworkCollector().clear(p);
    }
}
