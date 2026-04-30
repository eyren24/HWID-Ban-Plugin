package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.util.DurationParser;
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
 * /hvinfo <player>
 * Mostra info complete: UUID, online, FP count, ban status, expires.
 */
public class VInfoCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VInfoCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.info")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvinfo <player>");
            return;
        }

        String targetName = args[0];
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            String uuid;
            boolean online;
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);
            if (opt.isPresent()) {
                uuid   = opt.get().getUniqueId().toString();
                online = true;
            } else {
                BanEntry b = plugin.getDb().findBanByName(targetName);
                if (b == null || b.playerUuid() == null) {
                    send(src, "&c[HWIDBan] Player '" + targetName + "' not online and has no ban history.");
                    return;
                }
                uuid   = b.playerUuid();
                online = false;
            }

            int fpCount = plugin.getDb().getFingerprintsForPlayer(uuid).size();
            BanEntry activeBan = plugin.getDb().getActiveBanByUUID(uuid);

            send(src, plugin.getVelocityConfig().getString("messages.info.header",
                    "&8--- &6Player Info: &e%player% &8---").replace("%player%", targetName));
            send(src, plugin.getVelocityConfig().getString("messages.info.online",
                    "&7Online: &a%online%").replace("%online%", online ? "yes" : "no"));
            send(src, plugin.getVelocityConfig().getString("messages.info.uuid",
                    "&7UUID: &f%uuid%").replace("%uuid%", uuid));
            send(src, plugin.getVelocityConfig().getString("messages.info.fp-count",
                    "&7Fingerprints: &f%count%").replace("%count%", String.valueOf(fpCount)));

            if (activeBan != null && !activeBan.isExpired()) {
                send(src, plugin.getVelocityConfig().getString("messages.info.ban-status",
                        "&7Ban Status: %status%").replace("%status%",
                        activeBan.isPermanent() ? "&c&lPERMANENT" : "&6TEMPORARY"));
                send(src, plugin.getVelocityConfig().getString("messages.info.ban-reason",
                        "&7Reason: &f%reason%").replace("%reason%",
                        activeBan.reason() != null ? activeBan.reason() : "-"));
                send(src, plugin.getVelocityConfig().getString("messages.info.ban-by",
                        "&7Banned by: &f%admin%").replace("%admin%",
                        activeBan.bannedBy() != null ? activeBan.bannedBy() : "CONSOLE"));
                if (!activeBan.isPermanent()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String exp = sdf.format(new Date(activeBan.expiresAt()))
                            + " (" + DurationParser.remaining(activeBan.expiresAt()) + " left)";
                    send(src, plugin.getVelocityConfig().getString("messages.info.ban-expires",
                            "&7Expires: &f%expires%").replace("%expires%", exp));
                }
            } else {
                send(src, plugin.getVelocityConfig().getString("messages.info.ban-status",
                        "&7Ban Status: %status%").replace("%status%", "&aclean"));
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.info")) return List.of();
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
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.info");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
