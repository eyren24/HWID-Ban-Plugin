package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HwidBanCommand implements TabExecutor {

    private final Main plugin;

    public HwidBanCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.ban")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " <player> [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        if (target.hasPermission("hwidban.bypass")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }

        StringBuilder reason = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (reason.length() > 0) reason.append(' ');
            reason.append(args[i]);
        }
        String finalReason = reason.length() == 0
                ? plugin.getConfig().getString("messages.ban.reason-default", "banned")
                : reason.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FingerprintData data = plugin.getFingerprintGenerator().generate(target);
            // Save fingerprint BEFORE banning so PreLoginListener UUID-check and
            // fingerprint-history checks both work on the very next login attempt
            plugin.getDatabaseManager().saveFingerprint(
                    target.getUniqueId().toString(), data.hash(), data.rawString());
            boolean ok = plugin.getBanManager().ban(data.hash(), target.getName(),
                    target.getUniqueId().toString(), finalReason, sender.getName(), 0L);

            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("fingerprint", data.hash());
            ph.put("admin", sender.getName());
            ph.put("reason", finalReason);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ok) {
                    plugin.getMessageUtil().send(sender, "ban.already-banned");
                    return;
                }
                plugin.getMessageUtil().send(sender, "ban.success", ph);
                plugin.getBanManager().broadcast("ban.broadcast", ph);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.ban")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) names.add(p.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}
