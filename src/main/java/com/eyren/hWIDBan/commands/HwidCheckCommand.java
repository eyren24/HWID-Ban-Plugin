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

public class HwidCheckCommand implements TabExecutor {

    private final Main plugin;

    public HwidCheckCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.check")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " <player>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FingerprintData data = plugin.getFingerprintGenerator().generate(target);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> header = new HashMap<>();
                header.put("player", target.getName());
                sender.sendMessage(plugin.getMessageUtil().get("check.header", header));

                for (Map.Entry<String, String> e : data.fields().entrySet()) {
                    Map<String, String> line = new HashMap<>();
                    line.put("field", e.getKey());
                    line.put("value", e.getValue());
                    sender.sendMessage(plugin.getMessageUtil().get("check.line", line));
                }

                Map<String, String> footer = new HashMap<>();
                footer.put("fingerprint", data.hash());
                sender.sendMessage(plugin.getMessageUtil().get("check.footer", footer));
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.check")) return Collections.emptyList();
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
