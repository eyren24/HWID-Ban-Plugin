package com.eyren.hWIDBan.velocity.commands;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * /hvhelp — elenca tutti i comandi disponibili.
 */
public class VHelpCommand implements SimpleCommand {

    private final VelocityPlugin plugin;

    public VHelpCommand(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        send(src, "&8&m─────── &6&lHWIDBan-Velocity Help &8&m───────");
        line(src, "hwidban.ban",       "/hvban <player> [reason]",            "Ban permanente HWID");
        line(src, "hwidban.tempban",   "/hvtempban <player> <1d2h> [reason]", "Ban temporaneo");
        line(src, "hwidban.unban",     "/hvunban <player|hash>",              "Rimuovi un ban");
        line(src, "hwidban.check",     "/hvcheck <player>",                   "Mostra fingerprint");
        line(src, "hwidban.list",      "/hvlist [limit]",                     "Lista ultimi ban");
        line(src, "hwidban.alt",       "/hvalt <player>",                     "Trova alt account");
        line(src, "hwidban.history",   "/hvhistory <player> [limit]",         "Cronologia FP");
        line(src, "hwidban.info",      "/hvinfo <player>",                    "Info complete");
        line(src, "hwidban.stats",     "/hvstats",                            "Statistiche globali");
        line(src, "hwidban.purge",     "/hvpurge fingerprints <giorni>",      "Elimina FP vecchi");
        line(src, "hwidban.purge",     "/hvpurge expired",                    "Elimina ban scaduti");
        line(src, "hwidban.whitelist", "/hvwhitelist add <type> <value>",     "Aggiungi whitelist");
        line(src, "hwidban.whitelist", "/hvwhitelist remove <type> <value>",  "Rimuovi whitelist");
        line(src, "hwidban.whitelist", "/hvwhitelist list",                   "Lista whitelist");
        line(src, "hwidban.reload",    "/hvreload",                           "Ricarica config");
        send(src, "&8&m──────────────────────────────────────");
        send(src, "&7Tipi whitelist: &fIP, SUBNET, ISP, ASN, COUNTRY");
        send(src, "&7Durate: &f30s 5m 2h 1d 7d perm");
    }

    private void line(CommandSource src, String perm, String usage, String desc) {
        if (!plugin.getBanManager().hasPermission(src, perm)) return;
        send(src, "&8• &e" + usage + " &7— &f" + desc);
    }

    private void send(CommandSource src, String legacy) {
        src.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }
}
