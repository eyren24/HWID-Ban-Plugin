package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.database.DatabaseManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * /hvhistory <player> [limit]
 * Mostra la cronologia fingerprint di un account.
 */
public class VHistoryCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VHistoryCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.history")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvhistory <player> [limit]");
            return;
        }

        String targetName = args[0];
        int limit = 10;
        if (args.length >= 2) {
            try { limit = Math.min(50, Math.max(1, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        final int finalLimit = limit;

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            String uuid;
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);
            if (opt.isPresent()) {
                uuid = opt.get().getUniqueId().toString();
            } else {
                BanEntry b = plugin.getDb().findBanByName(targetName);
                if (b == null || b.playerUuid() == null) {
                    send(src, "&c[HWIDBan] Player '" + targetName + "' not online and has no ban history.");
                    return;
                }
                uuid = b.playerUuid();
            }

            List<DatabaseManager.FingerprintRecord> hist = plugin.getDb().getFingerprintHistory(uuid, finalLimit);
            send(src, plugin.getVelocityConfig().getString("messages.history.header",
                    "&8--- &6Fingerprint History of &e%player% &8---")
                    .replace("%player%", targetName));

            if (hist.isEmpty()) {
                send(src, plugin.getVelocityConfig().getString("messages.history.empty",
                        "&7No fingerprint history found."));
                return;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            String tmpl = plugin.getVelocityConfig().getString("messages.history.line",
                    "&7%date% &8• &b%fpshort% &7last:&f%last-seen%");

            for (DatabaseManager.FingerprintRecord r : hist) {
                send(src, tmpl
                        .replace("%date%",      sdf.format(new Date(r.firstSeen)))
                        .replace("%fpshort%",   r.fingerprint.substring(0, Math.min(12, r.fingerprint.length())))
                        .replace("%last-seen%", sdf.format(new Date(r.lastSeen))));
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.history")) return List.of();
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> r = new ArrayList<>();
            for (Player p : plugin.getServer().getAllPlayers())
                if (p.getUsername().toLowerCase().startsWith(partial)) r.add(p.getUsername());
            return r;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.history");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
