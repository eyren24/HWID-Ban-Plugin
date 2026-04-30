package com.eyren.hWIDBan.commands;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.util.Colors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /hwidhelp — elenca tutti i comandi disponibili e il loro permesso.
 * Mostra solo quelli a cui il sender ha accesso (UX-friendly).
 */
public class HwidHelpCommand implements CommandExecutor {

    private final Main plugin;

    public HwidHelpCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        send(sender, "&8&m─────────── &6&lHWIDBan Help &8&m───────────");
        line(sender, "hwidban.ban",       "/hwidban <player> [reason]",          "Ban permanente HWID");
        line(sender, "hwidban.tempban",   "/hwidtempban <player> <1d2h> [reason]","Ban temporaneo");
        line(sender, "hwidban.unban",     "/hwidunban <player|hash>",            "Rimuovi un ban");
        line(sender, "hwidban.check",     "/hwidcheck <player>",                  "Mostra il fingerprint");
        line(sender, "hwidban.list",      "/hwidlist [limit]",                    "Lista ultimi ban");
        line(sender, "hwidban.alt",       "/hwidalt <player>",                    "Trova alt account");
        line(sender, "hwidban.history",   "/hwidhistory <player> [limit]",        "Cronologia FP");
        line(sender, "hwidban.info",      "/hwidinfo <player>",                   "Info complete");
        line(sender, "hwidban.stats",     "/hwidstats",                           "Statistiche globali");
        line(sender, "hwidban.purge",     "/hwidpurge fingerprints <giorni>",     "Elimina FP vecchi");
        line(sender, "hwidban.purge",     "/hwidpurge expired",                   "Elimina ban scaduti");
        line(sender, "hwidban.whitelist", "/hwidwhitelist add <type> <value>",    "Aggiungi whitelist");
        line(sender, "hwidban.whitelist", "/hwidwhitelist remove <type> <value>", "Rimuovi whitelist");
        line(sender, "hwidban.whitelist", "/hwidwhitelist list",                  "Lista whitelist");
        line(sender, "hwidban.reload",    "/hwidreload",                          "Ricarica config");
        send(sender, "&8&m──────────────────────────────────");
        send(sender, "&7Tipi whitelist: &fIP, SUBNET, ISP, ASN, COUNTRY");
        send(sender, "&7Durate: &f30s 5m 2h 1d 7d perm");
        return true;
    }

    private void line(CommandSender sender, String perm, String usage, String desc) {
        if (!plugin.getBanManager().hasPermission(sender, perm)) return;
        send(sender, "&8• &e" + usage + " &7— &f" + desc);
    }

    private void send(CommandSender sender, String msg) {
        sender.sendMessage(Colors.process(msg));
    }
}
