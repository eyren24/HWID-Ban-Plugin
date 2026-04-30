package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /hvunban <player|fingerprint>
 * Accetta nome player (cerca via storia FP) o hash SHA-256 completo (64 hex).
 */
public class VUnbanCommand implements SimpleCommand {

    private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");

    private final VelocityPlugin plugin;

    public VUnbanCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.unban")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvunban <player|fingerprint>");
            return;
        }

        String input = args[0];
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            String fingerprint;

            if (HASH.matcher(input.toLowerCase()).matches()) {
                // Diretto via hash
                fingerprint = input.toLowerCase();
            } else {
                // Per nome — cerca un player online o nello storico FP
                String uuid = plugin.getServer().getPlayer(input)
                        .map(p -> p.getUniqueId().toString())
                        .orElse(null);

                if (uuid == null) {
                    // Nessun player online con quel nome → cerca via DB attivo
                    BanEntry hit = plugin.getDb().listBans(500).stream()
                            .filter(b -> input.equalsIgnoreCase(b.playerName()))
                            .findFirst().orElse(null);
                    if (hit == null) {
                        send(src, "&e[HWIDBan] '" + input + "' has no active ban (or player not found).");
                        return;
                    }
                    fingerprint = hit.fingerprint();
                } else {
                    List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuid);
                    fingerprint = fps.stream()
                            .map(plugin.getDb()::getBan)
                            .filter(b -> b != null && !b.isExpired())
                            .map(BanEntry::fingerprint)
                            .findFirst().orElse(null);
                    if (fingerprint == null) {
                        send(src, "&e[HWIDBan] '" + input + "' is not banned.");
                        return;
                    }
                }
            }

            boolean ok = plugin.getBanManager().unban(fingerprint);
            send(src, ok
                    ? "&a[HWIDBan] Unbanned &e" + input + "&a (fp: &7" + fingerprint.substring(0, 12) + "...&a)"
                    : "&e[HWIDBan] '" + input + "' is not banned.");
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.unban"))
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
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.unban");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
