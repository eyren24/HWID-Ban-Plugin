package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.util.FingerprintUtil;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /hvcheck <player>
 * Mostra il fingerprint del player online (con breakdown dei campi).
 */
public class VCheckCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VCheckCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.check")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvcheck <player>");
            return;
        }

        String targetName = args[0];
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            Optional<Player> opt = plugin.getServer().getPlayer(targetName);
            if (opt.isEmpty()) {
                send(src, plugin.getVelocityConfig().getString("messages.check.not-found",
                        "&c[HWIDBan] Player must be online to check fingerprint."));
                return;
            }
            Player target = opt.get();
            String ip = target.getRemoteAddress().getAddress().getHostAddress();
            int mask  = plugin.getVelocityConfig().getInt("fingerprint.subnet-mask", 20);
            int prot  = target.getProtocolVersion().getProtocol();
            GeoIPClient.GeoData geo = plugin.getGeoIP().lookup(ip);

            String subnet = FingerprintUtil.getSubnet(ip, mask);
            String raw    = FingerprintUtil.buildRaw(ip, mask, geo, prot);
            String hash   = FingerprintUtil.sha256(raw);

            send(src, plugin.getVelocityConfig().getString("messages.check.header",
                    "&8--- &6Fingerprint of &e%player% &8---")
                    .replace("%player%", target.getUsername()));
            String line = plugin.getVelocityConfig().getString("messages.check.line", "&8• &7%key%: &f%value%");
            send(src, line.replace("%key%", "ip").replace("%value%", ip));
            send(src, line.replace("%key%", "subnet").replace("%value%", subnet));
            send(src, line.replace("%key%", "isp").replace("%value%", safe(geo.isp())));
            send(src, line.replace("%key%", "asn").replace("%value%", safe(geo.asn())));
            send(src, line.replace("%key%", "country").replace("%value%", safe(geo.country())));
            send(src, line.replace("%key%", "city").replace("%value%", safe(geo.city())));
            send(src, line.replace("%key%", "protocol").replace("%value%", String.valueOf(prot)));
            send(src, plugin.getVelocityConfig().getString("messages.check.hash",
                    "&8• &7Hash: &b%hash%").replace("%hash%", hash));
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.check")) return List.of();
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
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.check");
    }

    private static String safe(String s) { return s != null && !s.isEmpty() ? s : "-"; }
    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
