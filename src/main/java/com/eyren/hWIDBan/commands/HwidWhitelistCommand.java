package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.ban.WhitelistManager;
import com.eyren.hWIDBan.ban.WhitelistManager.Type;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HwidWhitelistCommand implements TabExecutor {

    private static final List<String> TYPES = Arrays.asList("IP", "SUBNET", "ISP", "ASN", "COUNTRY");
    private static final List<String> SUBS  = Arrays.asList("add", "remove", "list");

    private final Main plugin;

    public HwidWhitelistCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.whitelist")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            usage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add":
                if (args.length < 3) { usage(sender, label); return true; }
                handleAdd(sender, args);
                break;
            case "remove":
                if (args.length < 3) { usage(sender, label); return true; }
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                usage(sender, label);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        Type type = Type.fromString(args[1]);
        if (type == null) {
            plugin.getMessageUtil().send(sender, "whitelist.invalid-type",
                    Map.of("valid", String.join(", ", TYPES)));
            return;
        }
        String value = args[2];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = plugin.getWhitelistManager().add(type, value, sender.getName());
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().send(sender, ok ? "whitelist.added" : "whitelist.error",
                            Map.of("type", type.name(), "value", value)));
        });
    }

    private void handleRemove(CommandSender sender, String[] args) {
        Type type = Type.fromString(args[1]);
        if (type == null) {
            plugin.getMessageUtil().send(sender, "whitelist.invalid-type",
                    Map.of("valid", String.join(", ", TYPES)));
            return;
        }
        String value = args[2];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = plugin.getWhitelistManager().remove(type, value);
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageUtil().send(sender, ok ? "whitelist.removed" : "whitelist.not-found",
                            Map.of("type", type.name(), "value", value)));
        });
    }

    private void handleList(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String[]> entries = plugin.getWhitelistManager().list();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<String, String> hph = new HashMap<>();
                hph.put("count", String.valueOf(entries.size()));
                sender.sendMessage(plugin.getMessageUtil().get("whitelist.header", hph));
                if (entries.isEmpty()) {
                    sender.sendMessage(plugin.getMessageUtil().get("whitelist.empty"));
                    return;
                }
                for (String[] e : entries) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("type", e[0]);
                    ph.put("value", e[1]);
                    ph.put("admin", e[2] == null ? "?" : e[2]);
                    ph.put("date", sdf.format(new Date(Long.parseLong(e[3]))));
                    sender.sendMessage(plugin.getMessageUtil().get("whitelist.line", ph));
                }
            });
        });
    }

    private void usage(CommandSender sender, String label) {
        plugin.getMessageUtil().send(sender, "invalid-usage",
                Map.of("usage", "/" + label + " <add|remove|list> [type] [value]"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.whitelist")) return Collections.emptyList();
        if (args.length == 1) return filter(SUBS, args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) return filter(TYPES, args[1]);
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String partial) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }
}
