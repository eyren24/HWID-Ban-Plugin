package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.ban.WhitelistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * /hvwhitelist add <type> <value>
 * /hvwhitelist remove <type> <value>
 * /hvwhitelist list
 */
public class VWhitelistCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VWhitelistCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (!plugin.getBanManager().hasPermission(src, "hwidban.whitelist")) {
            send(src, "&c[HWIDBan] You don't have permission.");
            return;
        }
        if (args.length < 1) {
            send(src, "&c[HWIDBan] Usage: /hvwhitelist <add|remove|list> [type] [value]");
            return;
        }

        String action = args[0].toLowerCase();
        String by = (src instanceof ConsoleCommandSource) ? "CONSOLE"
                : (src instanceof Player ? ((Player) src).getUsername() : "UNKNOWN");

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            switch (action) {
                case "add":
                    if (args.length < 3) { send(src, "&c[HWIDBan] Usage: /hvwhitelist add <IP|SUBNET|ISP|ASN|COUNTRY> <value>"); return; }
                    WhitelistManager.Type ta = WhitelistManager.Type.from(args[1]);
                    if (ta == null) { send(src, plugin.getVelocityConfig().getString(
                            "messages.whitelist.invalid-type", "&c[HWIDBan] Invalid type.")); return; }
                    String va = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    boolean okA = plugin.getWhitelistManager().add(ta, va, by);
                    send(src, okA
                            ? plugin.getVelocityConfig().getString("messages.whitelist.added",
                                "&a[HWIDBan] Added &f%type% %value% &ato whitelist.")
                                .replace("%type%", ta.name()).replace("%value%", va)
                            : "&c[HWIDBan] Failed to add to whitelist.");
                    break;

                case "remove":
                    if (args.length < 3) { send(src, "&c[HWIDBan] Usage: /hvwhitelist remove <IP|SUBNET|ISP|ASN|COUNTRY> <value>"); return; }
                    WhitelistManager.Type tr = WhitelistManager.Type.from(args[1]);
                    if (tr == null) { send(src, plugin.getVelocityConfig().getString(
                            "messages.whitelist.invalid-type", "&c[HWIDBan] Invalid type.")); return; }
                    String vr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    boolean okR = plugin.getWhitelistManager().remove(tr, vr);
                    send(src, okR
                            ? plugin.getVelocityConfig().getString("messages.whitelist.removed",
                                "&a[HWIDBan] Removed &f%type% %value% &afrom whitelist.")
                                .replace("%type%", tr.name()).replace("%value%", vr)
                            : plugin.getVelocityConfig().getString("messages.whitelist.not-found",
                                "&e[HWIDBan] No whitelist entry for &f%type% %value%&e.")
                                .replace("%type%", tr.name()).replace("%value%", vr));
                    break;

                case "list":
                    List<String[]> all = plugin.getWhitelistManager().list();
                    send(src, plugin.getVelocityConfig().getString("messages.whitelist.header",
                            "&8--- &6Whitelist Entries &7(%count%) &8---")
                            .replace("%count%", String.valueOf(all.size())));
                    if (all.isEmpty()) {
                        send(src, plugin.getVelocityConfig().getString(
                                "messages.whitelist.empty", "&7No whitelist entries."));
                        return;
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    String tmpl = plugin.getVelocityConfig().getString("messages.whitelist.line",
                            "&8• &7[%type%] &f%value% &7by &f%admin%");
                    for (String[] e : all) {
                        send(src, tmpl
                                .replace("%type%",  e[0])
                                .replace("%value%", e[1])
                                .replace("%admin%", e[2] != null ? e[2] : "CONSOLE")
                                .replace("%date%",  sdf.format(new Date(Long.parseLong(e[3])))));
                    }
                    break;

                default:
                    send(src, "&c[HWIDBan] Usage: /hvwhitelist <add|remove|list> [type] [value]");
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!plugin.getBanManager().hasPermission(invocation.source(), "hwidban.whitelist")) return List.of();
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String p = args.length == 0 ? "" : args[0].toLowerCase();
            List<String> r = new ArrayList<>();
            for (String a : new String[]{"add", "remove", "list"})
                if (a.startsWith(p)) r.add(a);
            return r;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            String p = args[1].toUpperCase();
            List<String> r = new ArrayList<>();
            for (WhitelistManager.Type t : WhitelistManager.Type.values())
                if (t.name().startsWith(p)) r.add(t.name());
            return r;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return plugin.getBanManager().hasPermission(invocation.source(), "hwidban.whitelist");
    }

    private static void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
