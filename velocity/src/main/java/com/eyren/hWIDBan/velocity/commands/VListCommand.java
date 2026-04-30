package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.util.DurationParser;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * /hvlist [limit]
 * Lista gli ultimi ban registrati (default 10, max 50).
 */
public class VListCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VListCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.list")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }

        int limit = 10;
        if (args.length >= 1) {
            try { limit = Math.min(50, Math.max(1, Integer.parseInt(args[0]))); }
            catch (NumberFormatException ignored) {}
        }
        final int finalLimit = limit;

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            List<BanEntry> bans = plugin.getDb().listBans(finalLimit);
            send(src, plugin.getVelocityConfig().getString("messages.list.header",
                    "&8--- &6HWIDBan List &7(last %count%) &8---")
                    .replace("%count%", String.valueOf(bans.size())));

            if (bans.isEmpty()) {
                send(src, plugin.getVelocityConfig().getString(
                        "messages.list.empty", "&7No bans found."));
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String tmpl = plugin.getVelocityConfig().getString("messages.list.line",
                    "&7%date% &e%player% &7fp:&8%fpshort% &7by:&f%admin% %status%");

            for (BanEntry b : bans) {
                String status = b.isPermanent()
                        ? "&c[PERM]"
                        : (b.isExpired()
                            ? "&8[EXPIRED]"
                            : "&6[" + DurationParser.remaining(b.expiresAt()) + "]");
                String line = tmpl
                        .replace("%date%",    sdf.format(new Date(b.bannedAt())))
                        .replace("%player%",  b.playerName() != null ? b.playerName() : "?")
                        .replace("%fpshort%", b.fingerprint().substring(0, Math.min(12, b.fingerprint().length())))
                        .replace("%admin%",   b.bannedBy() != null ? b.bannedBy() : "CONSOLE")
                        .replace("%status%",  status);
                send(src, line);
            }
        }).schedule();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.list");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
