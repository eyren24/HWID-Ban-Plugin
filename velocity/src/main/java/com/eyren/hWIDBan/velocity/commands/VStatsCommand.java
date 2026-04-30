package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.DatabaseManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * /hvstats — statistiche globali del database HWIDBan.
 */
public class VStatsCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VStatsCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        if (!plugin.getBanManager().hasPermission(src, "hwidban.stats")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            DatabaseManager.Stats s = plugin.getDb().getStats();
            send(src, plugin.getVelocityConfig().getString("messages.stats.header",
                    "&8--- &6HWIDBan Statistics &8---"));
            send(src, plugin.getVelocityConfig().getString("messages.stats.total-bans",
                    "&7Total bans: &f%total%").replace("%total%", String.valueOf(s.totalBans)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.active-bans",
                    "&7Active bans: &a%active%").replace("%active%", String.valueOf(s.activeBans)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.bans-24h",
                    "&7Bans 24h: &e%count%").replace("%count%", String.valueOf(s.bans24h)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.bans-7d",
                    "&7Bans 7d: &e%count%").replace("%count%", String.valueOf(s.bans7d)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.fingerprints",
                    "&7Fingerprints: &f%count%").replace("%count%", String.valueOf(s.totalFingerprints)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.players",
                    "&7Unique players: &f%count%").replace("%count%", String.valueOf(s.uniquePlayers)));
            send(src, plugin.getVelocityConfig().getString("messages.stats.whitelist",
                    "&7Whitelist entries: &f%count%").replace("%count%", String.valueOf(s.whitelistSize)));

            // Stato integrazioni
            send(src, "&7Watch mode: " + (plugin.getBanManager().isWatchMode() ? "&eON" : "&aOFF"));
            send(src, "&7Discord:    " + (plugin.getDiscordWebhook().enabled() ? "&aON" : "&7off"));
            send(src, "&7Redis sync: " + (plugin.getRedisSync().enabled()      ? "&aON" : "&7off"));
            send(src, "&7LiteBans:   " + (plugin.getLiteBansHook() != null && plugin.getLiteBansHook().isRegistered() ? "&aON" : "&7off"));
        }).schedule();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.stats");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
