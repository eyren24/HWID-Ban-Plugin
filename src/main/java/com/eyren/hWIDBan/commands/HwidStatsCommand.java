package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.DatabaseManager.Stats;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class HwidStatsCommand implements CommandExecutor {

    private final Main plugin;

    public HwidStatsCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.stats")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Stats s = plugin.getDatabaseManager().getStats();
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage(plugin.getMessageUtil().get("stats.header"));
                stat(sender, "Ban totali",       String.valueOf(s.totalBans));
                stat(sender, "Ban attivi",        String.valueOf(s.activeBans));
                stat(sender, "Ban ultime 24h",    String.valueOf(s.bans24h));
                stat(sender, "Ban ultimi 7g",     String.valueOf(s.bans7d));
                stat(sender, "Fingerprint unici", String.valueOf(s.totalFingerprints));
                stat(sender, "Player unici",      String.valueOf(s.uniquePlayers));
                stat(sender, "Whitelist voci",    String.valueOf(s.whitelistSize));

                // Stato integrazioni
                stat(sender, "Watch mode",  plugin.getBanManager().isWatchMode() ? "&eON" : "&aOFF");
                stat(sender, "Discord",     plugin.getDiscordWebhook().enabled() ? "&aON" : "&7off");
                stat(sender, "Redis sync",  plugin.getRedisSync().isEnabled()    ? "&aON" : "&7off");
                stat(sender, "LiteBans",    (plugin.getLiteBansHook() != null && plugin.getLiteBansHook().isRegistered())
                                            ? "&aON" : "&7off");

                sender.sendMessage(plugin.getMessageUtil().get("stats.footer"));
            });
        });
        return true;
    }

    private void stat(CommandSender sender, String label, String value) {
        Map<String, String> ph = new HashMap<>();
        ph.put("label", label);
        ph.put("value", value);
        sender.sendMessage(plugin.getMessageUtil().get("stats.line", ph));
    }
}
