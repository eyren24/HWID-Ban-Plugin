package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * /hvpurge fingerprints <giorni>
 * /hvpurge expired
 */
public class VPurgeCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VPurgeCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.purge")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvpurge fingerprints <days> | expired");
            return;
        }

        String sub = args[0].toLowerCase();
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            if ("expired".equals(sub)) {
                int n = plugin.getDb().purgeExpiredBans();
                send(src, plugin.getVelocityConfig().getString("messages.purge.expired",
                        "&a[HWIDBan] Purged &f%count% &aexpired bans.")
                        .replace("%count%", String.valueOf(n)));
            } else if ("fingerprints".equals(sub)) {
                if (args.length < 2) {
                    send(src, "&c[HWIDBan] Usage: /hvpurge fingerprints <days>");
                    return;
                }
                int days;
                try { days = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    send(src, "&c[HWIDBan] Days must be a number.");
                    return;
                }
                long ms = TimeUnit.DAYS.toMillis(days);
                int n  = plugin.getDb().purgeOldFingerprints(ms);
                send(src, plugin.getVelocityConfig().getString("messages.purge.fingerprints",
                        "&a[HWIDBan] Purged &f%count% &afingerprints older than &f%days%d&a.")
                        .replace("%count%", String.valueOf(n))
                        .replace("%days%", String.valueOf(days)));
            } else {
                send(src, "&c[HWIDBan] Usage: /hvpurge fingerprints <days> | expired");
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.purge")) return List.of();
        String[] args = invocation.arguments();
        if (args.length <= 1) return List.of("fingerprints", "expired");
        if (args.length == 2 && "fingerprints".equalsIgnoreCase(args[0])) return List.of("7", "30", "60", "90", "180", "365");
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.purge");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
