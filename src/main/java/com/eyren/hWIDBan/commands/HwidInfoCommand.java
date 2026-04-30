package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import com.eyren.hWIDBan.fingerprint.FingerprintData;
import com.eyren.hWIDBan.util.DurationParser;
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

public class HwidInfoCommand implements TabExecutor {

    private final Main plugin;

    public HwidInfoCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.info")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            plugin.getMessageUtil().send(sender, "invalid-usage", Map.of("usage", "/" + label + " <player>"));
            return true;
        }

        String targetName = args[0];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Player online = Bukkit.getPlayerExact(targetName);
            OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayer(targetName);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            FingerprintData fpData = online != null
                    ? plugin.getFingerprintGenerator().generate(online)
                    : null;

            List<String> allFps = plugin.getDatabaseManager()
                    .getFingerprintsForPlayer(op.getUniqueId().toString());

            BanEntry activeBan = null;
            for (String fp : allFps) {
                BanEntry e = plugin.getDatabaseManager().getBan(fp);
                if (e != null && !e.isExpired()) { activeBan = e; break; }
            }
            final BanEntry ban = activeBan;
            final FingerprintData fp = fpData;
            final String name = op.getName() != null ? op.getName() : targetName;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> h = Map.of("player", name);
                sender.sendMessage(plugin.getMessageUtil().get("info.header", h));

                info(sender, "UUID", op.getUniqueId().toString());
                info(sender, "Online", online != null ? "&aYes" : "&cNo");
                info(sender, "Fingerprint", fp != null ? fp.hash().substring(0, 16) + "..." : "&7(not online)");
                info(sender, "Stored FPs", String.valueOf(allFps.size()));

                if (ban != null) {
                    info(sender, "Stato", "&cBANNATO");
                    info(sender, "Motivo", ban.reason());
                    info(sender, "Admin", ban.bannedBy());
                    info(sender, "Data ban", sdf.format(new Date(ban.bannedAt())));
                    info(sender, "Scadenza", ban.isPermanent()
                            ? "&4PERMANENTE"
                            : DurationParser.remaining(ban.expiresAt()));
                } else {
                    info(sender, "Stato", "&aNon bannato");
                }
                if (op.getLastPlayed() > 0)
                    info(sender, "Ultimo join", sdf.format(new Date(op.getLastPlayed())));
            });
        });
        return true;
    }

    private void info(CommandSender sender, String field, String value) {
        Map<String, String> lp = new HashMap<>();
        lp.put("field", field);
        lp.put("value", value == null ? "&7-" : value);
        sender.sendMessage(plugin.getMessageUtil().get("info.line", lp));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.info")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> r = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) r.add(p.getName());
            return r;
        }
        return Collections.emptyList();
    }
}
