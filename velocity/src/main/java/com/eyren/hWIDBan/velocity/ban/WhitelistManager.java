package com.eyren.hWIDBan.velocity.ban;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;

import java.util.List;

/**
 * Whitelist a 5 livelli: IP, SUBNET, ISP, ASN, COUNTRY.
 * I valori sono salvati nel DB (tabella hwidban_whitelist).
 */
public class WhitelistManager {

    public enum Type {
        IP, SUBNET, ISP, ASN, COUNTRY;

        public static Type from(String s) {
            if (s == null) return null;
            try { return Type.valueOf(s.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    private final VelocityPlugin plugin;

    public WhitelistManager(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Controlla se l'utente passa la whitelist.
     * Restituisce true se ALMENO UNA condizione fa match.
     */
    public boolean isWhitelisted(String ip, String subnet, GeoIPClient.GeoData geo) {
        if (plugin.getDb().isWhitelisted(Type.IP.name(),      ip))           return true;
        if (plugin.getDb().isWhitelisted(Type.SUBNET.name(),  subnet))       return true;
        if (geo != null) {
            if (plugin.getDb().isWhitelisted(Type.ISP.name(),     geo.isp()))     return true;
            if (plugin.getDb().isWhitelisted(Type.ASN.name(),     geo.asn()))     return true;
            if (plugin.getDb().isWhitelisted(Type.COUNTRY.name(), geo.country())) return true;
        }
        return false;
    }

    public boolean add(Type type, String value, String addedBy) {
        return plugin.getDb().addWhitelist(type.name(), value, addedBy);
    }

    public boolean remove(Type type, String value) {
        return plugin.getDb().removeWhitelist(type.name(), value);
    }

    public List<String[]> list() {
        return plugin.getDb().listWhitelist();
    }
}
