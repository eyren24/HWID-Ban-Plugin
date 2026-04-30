package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HwidUnbanCommand implements TabExecutor {

    private final Main plugin;

    public HwidUnbanCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.unban")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage",
                    Map.of("usage", "/" + label + " <player|fingerprint>"));
            return true;
        }

        String input = args[0];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean removedAny = false;
            if (input.length() == 64 && input.matches("[0-9a-fA-F]{64}")) {
                removedAny = plugin.getBanManager().unban(input.toLowerCase());
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(input);
                if (!op.hasPlayedBefore() && Bukkit.getPlayerExact(input) == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", input)));
                    return;
                }
                List<String> fps = plugin.getBanManager().fingerprintsOf(op.getUniqueId().toString());
                for (String fp : fps) {
                    if (plugin.getBanManager().unban(fp)) removedAny = true;
                }
            }
            final boolean finalRemoved = removedAny;
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().send(sender, finalRemoved ? "unban.success" : "unban.not-banned"));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.unban")) return Collections.emptyList();
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
