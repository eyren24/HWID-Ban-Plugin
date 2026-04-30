package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HwidPurgeCommand implements CommandExecutor {

    private final Main plugin;

    public HwidPurgeCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.purge")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }

        // /hwidpurge fingerprints <days>
        // /hwidpurge expired
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " fingerprints <giorni> | expired"));
            return true;
        }

        String sub = args[0].toLowerCase();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if ("expired".equals(sub)) {
                int removed = plugin.getDatabaseManager().purgeExpiredBans();
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageUtil().send(sender, "purge.expired",
                                Map.of("count", String.valueOf(removed))));
            } else if ("fingerprints".equals(sub)) {
                if (args.length < 2) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessageUtil().send(sender, "invalid-usage",
                                    Map.of("usage", "/" + label + " fingerprints <giorni>")));
                    return;
                }
                int days;
                try { days = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessageUtil().send(sender, "invalid-usage",
                                    Map.of("usage", "/" + label + " fingerprints <giorni>")));
                    return;
                }
                long millis = TimeUnit.DAYS.toMillis(days);
                int removed = plugin.getDatabaseManager().purgeOldFingerprints(millis);
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageUtil().send(sender, "purge.fingerprints",
                                Map.of("count", String.valueOf(removed), "days", String.valueOf(days))));
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageUtil().send(sender, "invalid-usage",
                                Map.of("usage", "/" + label + " fingerprints <giorni> | expired")));
            }
        });
        return true;
    }
}
