package com.eyren.hWIDBan.ban;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.collector.GeoIPCollector;

import java.util.List;
import java.util.Locale;

public class WhitelistManager {

    public enum Type {
        IP, SUBNET, ISP, ASN, COUNTRY;

        public static Type fromString(String s) {
            if (s == null) return null;
            try { return Type.valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        }
    }

    private final Main plugin;

    public WhitelistManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean add(Type type, String value, String addedBy) {
        return plugin.getDatabaseManager().addWhitelist(type.name(), value, addedBy);
    }

    public boolean remove(Type type, String value) {
        return plugin.getDatabaseManager().removeWhitelist(type.name(), value);
    }

    public List<String[]> list() {
        return plugin.getDatabaseManager().listWhitelist();
    }

    public boolean isWhitelisted(String ip, String subnet, GeoIPCollector.GeoData geo) {
        if (plugin.getDatabaseManager().isWhitelisted("IP", ip)) return true;
        if (plugin.getDatabaseManager().isWhitelisted("SUBNET", subnet)) return true;
        if (geo != null) {
            if (geo.isp() != null && plugin.getDatabaseManager().isWhitelisted("ISP", geo.isp())) return true;
            if (geo.asn() != null && plugin.getDatabaseManager().isWhitelisted("ASN", geo.asn())) return true;
            if (geo.country() != null && plugin.getDatabaseManager().isWhitelisted("COUNTRY", geo.country())) return true;
        }
        return false;
    }
}
