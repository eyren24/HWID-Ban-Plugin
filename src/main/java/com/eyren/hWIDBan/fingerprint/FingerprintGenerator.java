package com.eyren.hWIDBan.fingerprint;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.collector.GeoIPCollector;
import com.eyren.hWIDBan.config.ConfigManager;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FingerprintGenerator {

    private final Main plugin;

    public FingerprintGenerator(Main plugin) {
        this.plugin = plugin;
    }

    public FingerprintData generate(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        FingerprintData data = new FingerprintData();

        String ip = plugin.getIpCollector().getIP(player);
        String subnet = plugin.getIpCollector().getSubnet(ip, cm.subnetMask());

        if (cm.isSourceEnabled("ip")) data.put("ip", ip);
        if (cm.isSourceEnabled("subnet")) data.put("subnet", subnet);

        GeoIPCollector.GeoData geo = plugin.getGeoIPCollector().lookup(ip);
        if (cm.isSourceEnabled("isp")) data.put("isp", geo.isp());
        if (cm.isSourceEnabled("asn")) data.put("asn", geo.asn());
        if (cm.isSourceEnabled("country")) data.put("country", geo.country());
        if (cm.isSourceEnabled("city")) data.put("city", geo.city());

        if (cm.isSourceEnabled("client-brand")) data.put("brand", plugin.getClientCollector().getBrand(player));
        if (cm.isSourceEnabled("mc-version")) data.put("version", plugin.getClientCollector().getVersion(player));
        if (cm.isSourceEnabled("language")) data.put("lang", plugin.getClientCollector().getLocale(player));

        if (cm.isSourceEnabled("ping-pattern")) data.put("ping", plugin.getNetworkCollector().getPingPattern(player));
        if (cm.isSourceEnabled("packet-timing")) data.put("timing", plugin.getNetworkCollector().getPacketTiming(player));

        if (cm.isSourceEnabled("screen-resolution")) data.put("screen", plugin.getScreenCollector().getResolution(player));
        if (cm.isSourceEnabled("operating-system")) data.put("os", plugin.getClientCollector().getOS(player));

        if (cm.isSourceEnabled("uuid")) data.put("uuid", player.getUniqueId().toString());

        data.hash(sha256(data.rawString()));
        return data;
    }

    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
