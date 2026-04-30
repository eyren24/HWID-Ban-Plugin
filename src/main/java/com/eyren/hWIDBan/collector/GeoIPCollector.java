package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class GeoIPCollector {

    private final Main plugin;
    private final ConcurrentHashMap<String, CachedGeo> cache = new ConcurrentHashMap<>();

    public GeoIPCollector(Main plugin) {
        this.plugin = plugin;
    }

    public GeoData lookup(String ip) {
        if (!plugin.getConfig().getBoolean("geoip.enabled", true)) {
            return GeoData.empty();
        }
        long ttl = plugin.getConfig().getLong("geoip.cache-ttl-minutes", 60) * 60_000L;
        CachedGeo cached = cache.get(ip);
        if (cached != null && System.currentTimeMillis() - cached.time < ttl) {
            return cached.data;
        }
        GeoData data = fetch(ip);
        cache.put(ip, new CachedGeo(data, System.currentTimeMillis()));
        return data;
    }

    private GeoData fetch(String ip) {
        String urlTemplate = plugin.getConfig().getString("geoip.api-url",
                "http://ip-api.com/json/%ip%?fields=status,country,countryCode,city,isp,as,asname,query");
        int timeout = plugin.getConfig().getInt("geoip.timeout-ms", 2000);
        String url = urlTemplate.replace("%ip%", ip);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestProperty("User-Agent", "HWIDBan-Plugin");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                return new GeoData(
                        getString(json, "country"),
                        getString(json, "city"),
                        getString(json, "isp"),
                        getString(json, "as")
                );
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().debug()) {
                plugin.getLogger().warning("GeoIP lookup failed for " + ip + ": " + e.getMessage());
            }
            return GeoData.empty();
        }
    }

    private String getString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    public static class GeoData {
        private final String country;
        private final String city;
        private final String isp;
        private final String asn;

        public GeoData(String country, String city, String isp, String asn) {
            this.country = country;
            this.city = city;
            this.isp = isp;
            this.asn = asn;
        }

        public String country() { return country; }
        public String city() { return city; }
        public String isp() { return isp; }
        public String asn() { return asn; }

        public static GeoData empty() {
            return new GeoData("", "", "", "");
        }
    }

    private static class CachedGeo {
        final GeoData data;
        final long time;
        CachedGeo(GeoData data, long time) { this.data = data; this.time = time; }
    }
}
