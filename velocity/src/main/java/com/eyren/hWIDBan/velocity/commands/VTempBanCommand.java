package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.util.DurationParser;
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
 * /hvtempban <player> <duration> [reason]
 * Duration: 1d2h30m | perm | 0
 */
public class VTempBanCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VTempBanCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.tempban")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 2) {
            send(src, "&c[HWIDBan] Usage: /hvtempban <player> <1d2h30m|perm> [reason]");
            return;
        }

        String targetName = args[0];
        long durationMs = DurationParser.parseMillis(args[1]);
        if (durationMs < 0) {
            send(src, "&c[HWIDBan] Invalid duration. Examples: 1h, 2d12h, 30d, perm");
            return;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : plugin.getVelocityConfig().getString("ban.default-reason", "Banned");
        String by = (src instanceof ConsoleCommandSource) ? "CONSOLE"
                : (src instanceof Player ? ((Player) src).getUsername() : "UNKNOWN");
        String durStr = DurationParser.format(durationMs);

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);
            String uuid, fp, displayName;

            if (opt.isPresent()) {
                Player target = opt.get();
                if (target.hasPermission("hwidban.bypass")) {
                    send(src, "&c[HWIDBan] You don't have permission.");
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
                // OFFLINE
                com.eyren.hWIDBan.velocity.database.BanEntry hist = plugin.getDb().findBanByName(targetName);
                if (hist != null && hist.playerUuid() != null) {
                    uuid = hist.playerUuid();
                } else {
                    send(src, "&c[HWIDBan] Player '" + targetName + "' not online and no FP history.");
                    return;
                }
                java.util.List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuid);
                fp = fps.isEmpty() ? "manual-anchor-" + uuid : fps.get(0);
                displayName = targetName;
            }

            boolean ok = plugin.getBanManager().ban(fp, displayName, uuid, reason, by, durationMs);
            if (ok) {
                send(src, "&a[HWIDBan] TempBanned &e" + displayName
                        + " &afor &f" + durStr + "&a — reason: &f" + reason);
            } else {
                send(src, "&e[HWIDBan] " + displayName + " is already banned.");
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.tempban"))
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
        if (args.length == 2) return List.of("1h", "1d", "7d", "30d", "perm");
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.tempban");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
