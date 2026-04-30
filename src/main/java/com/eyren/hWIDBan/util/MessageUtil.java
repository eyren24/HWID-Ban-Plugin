package com.eyren.hWIDBan.util;

import com.eyren.hWIDBan.Main;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MessageUtil {

    private final Main plugin;

    public MessageUtil(Main plugin) {
        this.plugin = plugin;
    }

    public String get(String path) {
        String msg = plugin.getConfig().getString("messages." + path, "&cmissing: " + path);
        String prefix = plugin.getConfig().getString("settings.prefix", "");
        return Colors.process(msg.replace("%prefix%", prefix));
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = plugin.getConfig().getString("messages." + path, "&cmissing: " + path);
        String prefix = plugin.getConfig().getString("settings.prefix", "");
        msg = msg.replace("%prefix%", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return Colors.process(msg);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }

    public String colorize(String input) {
        return Colors.process(input);
    }
}
