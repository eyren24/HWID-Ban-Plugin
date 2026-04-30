package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.util.FingerprintUtil;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /hvalt <player>
 * Trova alt account che condividono lo stesso fingerprint.
 */
public class VAltCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VAltCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.alt")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvalt <player>");
            return;
        }

        String targetName = args[0];
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            String fingerprint;
            String uuid;

            // Online: usa connection data
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);
            if (opt.isPresent()) {
                Player target = opt.get();
                uuid = target.getUniqueId().toString();
                String ip = target.getRemoteAddress().getAddress().getHostAddress();
                GeoIPClient.GeoData geo = plugin.getGeoIP().lookup(ip);
                int mask = plugin.getVelocityConfig().getInt("fingerprint.subnet-mask", 20);
                int prot = target.getProtocolVersion().getProtocol();
                fingerprint = FingerprintUtil.sha256(FingerprintUtil.buildRaw(ip, mask, geo, prot));
            } else {
                // Offline: cerca in bans table
                BanEntry b = plugin.getDb().findBanByName(targetName);
                if (b == null) {
                    send(src, "&c[HWIDBan] Player '" + targetName + "' not online and has no ban history.");
                    return;
                }
                fingerprint = b.fingerprint();
                uuid = b.playerUuid();
            }

            List<String[]> alts = plugin.getDb().findAlts(fingerprint, 20);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            send(src, plugin.getVelocityConfig().getString("messages.alt.header",
                    "&8--- &6Alts of &e%player% &7(fp:&8%fpshort%&7) &8--- &7%count% found")
                    .replace("%player%",  targetName)
                    .replace("%fpshort%", fingerprint.substring(0, Math.min(12, fingerprint.length())))
                    .replace("%count%",   String.valueOf(alts.size())));

            if (alts.isEmpty()) {
                send(src, plugin.getVelocityConfig().getString("messages.alt.empty", "&7No alts found."));
                return;
            }

            String tmpl = plugin.getVelocityConfig().getString("messages.alt.line",
                    "&8• &e%player% &7uuid:&8%uuid% &7last:&f%last-seen% banned:%banned%");

            for (String[] alt : alts) {
                String altUuid = alt[0];
                long lastSeen = Long.parseLong(alt[1]);

                // Cerca nome: online → bans table → fallback uuid corto
                String altName = plugin.getServer().getPlayer(UUID.fromString(altUuid))
                        .map(Player::getUsername)
                        .orElseGet(() -> {
                            String name = plugin.getDb().findNameByUuid(altUuid);
                            return name != null ? name : altUuid.substring(0, 8) + "...";
                        });

                boolean banned = plugin.getDb().getFingerprintsForPlayer(altUuid).stream()
                        .anyMatch(plugin.getDb()::isBanned);

                send(src, tmpl
                        .replace("%player%",    altName)
                        .replace("%uuid%",      altUuid)
                        .replace("%last-seen%", sdf.format(new Date(lastSeen)))
                        .replace("%banned%",    banned ? "&c✖" : "&a✔"));
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.alt")) return List.of();
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
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.alt");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
