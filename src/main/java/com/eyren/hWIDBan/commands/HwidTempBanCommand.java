package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import com.eyren.hWIDBan.util.DurationParser;
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

public class HwidTempBanCommand implements TabExecutor {

    private final Main plugin;

    public HwidTempBanCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.tempban")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        // usage: /hwidtempban <player> <duration> [reason]
        if (args.length < 2) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " <player> <1d2h30m|perm> [reason]"));
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

        long durationMs = DurationParser.parseMillis(args[1]);
        if (durationMs < 0) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " <player> <1d2h30m|perm> [reason]"));
            return true;
        }

        StringBuilder reason = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (reason.length() > 0) reason.append(' ');
            reason.append(args[i]);
        }
        String finalReason = reason.length() == 0
                ? plugin.getConfig().getString("messages.ban.reason-default", "banned")
                : reason.toString();
        final long finalDuration = durationMs;
        final String durationStr = DurationParser.format(durationMs);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FingerprintData data = plugin.getFingerprintGenerator().generate(target);
            // Save fingerprint BEFORE banning so PreLoginListener UUID-check and
            // fingerprint-history checks both work on the very next login attempt
            plugin.getDatabaseManager().saveFingerprint(
                    target.getUniqueId().toString(), data.hash(), data.rawString());
            boolean ok = plugin.getBanManager().ban(
                    data.hash(), target.getName(), target.getUniqueId().toString(),
                    finalReason, sender.getName(), finalDuration);

            Map<String, String> ph = new HashMap<>();
            ph.put("player", target.getName());
            ph.put("fingerprint", data.hash());
            ph.put("admin", sender.getName());
            ph.put("reason", finalReason);
            ph.put("duration", durationStr);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ok) { plugin.getMessageUtil().send(sender, "ban.already-banned"); return; }
                plugin.getMessageUtil().send(sender, "tempban.success", ph);
                plugin.getBanManager().broadcast("tempban.broadcast", ph);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.tempban")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> r = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) r.add(p.getName());
            return r;
        }
        if (args.length == 2) return List.of("1h", "1d", "7d", "30d", "perm");
        return Collections.emptyList();
    }
}
