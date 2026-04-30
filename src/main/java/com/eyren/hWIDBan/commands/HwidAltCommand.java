package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.DatabaseManager;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
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
import java.util.UUID;

public class HwidAltCommand implements TabExecutor {

    private final Main plugin;

    public HwidAltCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.alt")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage", Map.of("usage", "/" + label + " <player>"));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String targetName = args[0];
            Player online = Bukkit.getPlayerExact(targetName);
            String fingerprint;

            if (online != null) {
                FingerprintData data = plugin.getFingerprintGenerator().generate(online);
                fingerprint = data.hash();
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
                List<String> fps = plugin.getDatabaseManager().getFingerprintsForPlayer(op.getUniqueId().toString());
                if (fps.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getMessageUtil().send(sender, "player-not-found", Map.of("player", targetName)));
                    return;
                }
                fingerprint = fps.get(0);
            }

            List<String[]> alts = plugin.getDatabaseManager().findAlts(fingerprint, 20);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> h = new HashMap<>();
                h.put("player", targetName);
                h.put("fingerprint", fingerprint);
                h.put("fpshort", fingerprint.substring(0, 12));
                h.put("count", String.valueOf(alts.size()));
                sender.sendMessage(plugin.getMessageUtil().get("alt.header", h));

                if (alts.isEmpty()) {
                    sender.sendMessage(plugin.getMessageUtil().get("alt.empty"));
                    return;
                }
                for (String[] alt : alts) {
                    String uuid = alt[0];
                    long lastSeen = Long.parseLong(alt[1]);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    String name = op.getName() != null ? op.getName() : uuid.substring(0, 8) + "...";
                    boolean isBanned = !plugin.getDatabaseManager().getFingerprintsForPlayer(uuid).stream()
                            .filter(fp -> plugin.getDatabaseManager().isBanned(fp)).findFirst().isEmpty();
                    Map<String, String> lp = new HashMap<>();
                    lp.put("player", name);
                    lp.put("uuid", uuid);
                    lp.put("last-seen", sdf.format(new Date(lastSeen)));
                    lp.put("banned", isBanned ? "&c✖" : "&a✔");
                    sender.sendMessage(plugin.getMessageUtil().get("alt.line", lp));
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.alt")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> r = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) r.add(p.getName());
            return r;
        }
        return Collections.emptyList();
    }
}
