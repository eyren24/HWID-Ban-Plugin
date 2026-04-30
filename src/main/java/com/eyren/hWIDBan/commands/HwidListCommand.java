package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HwidListCommand implements CommandExecutor {

    private final Main plugin;

    public HwidListCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.list")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        int limit = 10;
        if (args.length >= 1) {
            try { limit = Math.min(50, Math.max(1, Integer.parseInt(args[0]))); } catch (NumberFormatException ignored) {}
        }
        final int finalLimit = limit;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BanEntry> entries = plugin.getDatabaseManager().listBans(finalLimit);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (entries.isEmpty()) {
                    plugin.getMessageUtil().send(sender, "list.empty");
                    return;
                }
                sender.sendMessage(plugin.getMessageUtil().get("list.header"));
                for (BanEntry entry : entries) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("player", entry.playerName() == null ? "?" : entry.playerName());
                    ph.put("fingerprint", entry.fingerprint());
                    ph.put("fpshort", entry.fingerprint().substring(0, 12));
                    ph.put("reason", entry.reason() == null ? "-" : entry.reason());
                    ph.put("admin", entry.bannedBy() == null ? "CONSOLE" : entry.bannedBy());
                    sender.sendMessage(plugin.getMessageUtil().get("list.line", ph));
                }
            });
        });
        return true;
    }
}
