package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HwidReloadCommand implements CommandExecutor {

    private final Main plugin;

    public HwidReloadCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getBanManager().hasPermission(sender, "hwidban.reload")) {
            plugin.getMessageUtil().send(sender, "no-permission");
            return true;
        }
        plugin.getConfigManager().reload();
        plugin.getMessageUtil().send(sender, "reload-success");
        return true;
    }
}
