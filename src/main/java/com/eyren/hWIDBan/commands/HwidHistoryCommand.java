package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.DatabaseManager.FingerprintRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HwidHistoryCommand implements TabExecutor {

    private final Main plugin;

    public HwidHistoryCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.history")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage", Map.of("usage", "/" + label + " <player> [limit]"));
            return true;
        }

        int limit = 10;
        if (args.length >= 2) {
            try { limit = Math.min(30, Math.max(1, Integer.parseInt(args[1]))); } catch (NumberFormatException ignored) {}
        }
        final int finalLimit = limit;
        final String targetName = args[0];

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            List<FingerprintRecord> records = plugin.getDatabaseManager()
                    .getFingerprintHistory(op.getUniqueId().toString(), finalLimit);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> h = new HashMap<>();
                h.put("player", op.getName() != null ? op.getName() : targetName);
                h.put("count", String.valueOf(records.size()));
                sender.sendMessage(plugin.getMessageUtil().get("history.header", h));

                if (records.isEmpty()) {
                    sender.sendMessage(plugin.getMessageUtil().get("history.empty"));
                    return;
                }
                for (FingerprintRecord r : records) {
                    boolean banned = plugin.getDatabaseManager().isBanned(r.fingerprint);
                    Map<String, String> lp = new HashMap<>();
                    lp.put("fpshort", r.fingerprint.substring(0, 12));
                    lp.put("fingerprint", r.fingerprint);
                    lp.put("first-seen", r.firstSeen > 0 ? sdf.format(new Date(r.firstSeen)) : "?");
                    lp.put("last-seen", sdf.format(new Date(r.lastSeen)));
                    lp.put("banned", banned ? "&c[BANNATO]" : "&a[OK]");
                    sender.sendMessage(plugin.getMessageUtil().get("history.line", lp));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.history")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> r = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) r.add(p.getName());
            return r;
        }
        return Collections.emptyList();
    }
}
