package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.util.FingerprintUtil;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * /hvban <player> [reason]
 * Banna un player online generando il fingerprint dai dati di connessione.
 */
public class VBanCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VBanCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.ban")) {
            send(src, plugin.getVelocityConfig().getString(
                    "messages.no-permission", "&c[HWIDBan] You don't have permission."));
            return;
        }
        if (args.length < 1) {
            send(src, plugin.getVelocityConfig().getString("messages.ban.invalid-usage",
                    "&c[HWIDBan] Usage: %usage%").replace("%usage%", "/hvban <player> [reason]"));
            return;
        }

        String targetName = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : plugin.getVelocityConfig().getString("ban.default-reason", "Banned by administrator");
        String by = (src instanceof ConsoleCommandSource) ? "CONSOLE"
                : (src instanceof Player ? ((Player) src).getUsername() : "UNKNOWN");

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);

            String uuid;
            String fp;
            String displayName;

            if (opt.isPresent()) {
                // ONLINE: genera fingerprint live
                Player target = opt.get();
                if (target.hasPermission("hwidban.bypass")) {
                    send(src, plugin.getVelocityConfig().getString("messages.no-permission",
                            "&c[HWIDBan] You don't have permission."));
                    return;
                }
                uuid = target.getUniqueId().toString();
                String ip = target.getRemoteAddress().getAddress().getHostAddress();
                GeoIPClient.GeoData geo = plugin.getGeoIP().lookup(ip);
                int mask = plugin.getVelocityConfig().getInt("fingerprint.subnet-mask", 20);
                int prot = target.getProtocolVersion().getProtocol();
                String raw = FingerprintUtil.buildRaw(ip, mask, geo, prot);
                fp = FingerprintUtil.sha256(raw);
                displayName = target.getUsername();
                plugin.getDb().saveFingerprint(uuid, fp, raw);
            } else {
                // OFFLINE: cerca in DB (bans table per name → uuid → ultimo FP)
                com.eyren.hWIDBan.velocity.database.BanEntry hist = plugin.getDb().findBanByName(targetName);
                if (hist != null && hist.playerUuid() != null) {
                    uuid = hist.playerUuid();
                } else {
                    send(src, plugin.getVelocityConfig().getString("messages.ban.not-found",
                            "&c[HWIDBan] Player '%player%' not found (must be online or have FP history).")
                            .replace("%player%", targetName));
                    return;
                }
                java.util.List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuid);
                if (fps.isEmpty()) {
                    // Nessun FP storico: usa anchor su UUID (PreLoginListener.getActiveBanByUUID lo cattura)
                    fp = "manual-anchor-" + uuid;
                } else {
                    fp = fps.get(0); // più recente
                }
                displayName = targetName;
            }

            boolean ok = plugin.getBanManager().ban(fp, displayName, uuid, reason, by, 0L);
            if (ok) {
                send(src, plugin.getVelocityConfig().getString("messages.ban.success",
                        "&a[HWIDBan] Banned &e%player% &afor: &f%reason%")
                        .replace("%player%", displayName).replace("%reason%", reason));
            } else {
                send(src, plugin.getVelocityConfig().getString("messages.ban.already",
                        "&e[HWIDBan] %player% is already banned.")
                        .replace("%player%", displayName));
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.ban"))
            return List.of();
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> r = new ArrayList<>();
            for (Player p : plugin.getServer().getAllPlayers()) {
                if (p.getUsername().toLowerCase().startsWith(partial)) r.add(p.getUsername());
            }
            return r;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.ban");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
