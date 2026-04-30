package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * /hvreload — ricarica config.yml senza riavviare il proxy.
 */
public class VReloadCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VReloadCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        if (!plugin.getBanManager().hasPermission(src, "hwidban.reload")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }

        plugin.getVelocityConfig().reload();
        send(src, plugin.getVelocityConfig().getString("messages.reload.success",
                "&a[HWIDBan] Configuration reloaded."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.reload");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
