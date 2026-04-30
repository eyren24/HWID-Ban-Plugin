package com.eyren.hWIDBan.velocity.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client HTTP leggero per ip-api.com con cache TTL.
 * Usa java.net.http.HttpClient (Java 11+) e Gson (shaded).
 */
public class GeoIPClient {

    private final String apiUrl;
    private final int    timeoutSeconds;
    private final long   cacheTtlMs;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GeoIPClient(String apiUrl, int timeoutSeconds, long cacheTtlMs) {
        this.apiUrl         = apiUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.cacheTtlMs     = cacheTtlMs;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /** Risultato di una lookup GeoIP. Tutti i campi sono non-null (stringa vuota se mancanti). */
    public record GeoData(String country, String city, String isp, String asn) {
        public static final GeoData EMPTY   = new GeoData("", "", "", "");
        public static final GeoData LOCAL   = new GeoData("LOCAL", "LOCAL", "LOCAL", "LOCAL");
    }

    /**
     * Risolve i dati geografici dell'IP.
     * Bloccante – va chiamato sempre da un thread asincrono.
     */
    public GeoData lookup(String ip) {
        if (ip == null || FingerprintUtil.isPrivate(ip)) return GeoData.LOCAL;

        CacheEntry cached = cache.get(ip);
        if (cached != null && !cached.isExpired(cacheTtlMs)) return cached.data;

        try {
            String url = apiUrl.replace("{ip}", ip);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                GeoData data = parse(resp.body());
                cache.put(ip, new CacheEntry(data));
                return data;
            }
        } catch (Exception ignored) {}

        return GeoData.EMPTY;
    }

    private static GeoData parse(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String country = str(obj, "country");
            String city    = str(obj, "city");
            String isp     = str(obj, "isp");
            String as      = str(obj, "as"); // "AS3269 Telecom Italia S.p.A."
            // Estrai solo il numero ASN (es. "AS3269")
            String asn = as.isEmpty() ? "" : as.split(" ")[0];
            return new GeoData(country, city, isp, asn);
        } catch (Exception e) {
            return GeoData.EMPTY;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private record CacheEntry(GeoData data, long ts) {
        CacheEntry(GeoData data) { this(data, System.currentTimeMillis()); }
        boolean isExpired(long ttlMs) { return System.currentTimeMillis() - ts > ttlMs; }
    }
}
