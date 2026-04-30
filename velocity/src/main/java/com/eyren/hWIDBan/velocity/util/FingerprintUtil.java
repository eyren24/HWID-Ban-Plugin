package com.eyren.hWIDBan.velocity.util;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class FingerprintUtil {

    private FingerprintUtil() {}

    /**
     * Calcola la subnet dell'IP con la maschera specificata (CIDR /mask).
     * Esempio: 93.44.134.55 /20 → 93.44.128.0/20
     */
    public static String getSubnet(String ip, int mask) {
        if (ip == null || ip.isEmpty()) return "0.0.0.0/0";
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4) { // IPv4
                int ipInt   = ByteBuffer.wrap(bytes).getInt();
                int maskInt = mask == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - mask));
                int netInt  = ipInt & maskInt;
                byte[] net  = ByteBuffer.allocate(4).putInt(netInt).array();
                return InetAddress.getByAddress(net).getHostAddress() + "/" + mask;
            }
        } catch (Exception ignored) {}
        return ip;
    }

    /**
     * SHA-256 dell'input, restituito come stringa hex lowercase a 64 caratteri.
     */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "error-" + input.hashCode();
        }
    }

    /**
     * Genera il fingerprint per Velocity (subset semplificato senza dati in-game).
     * Sorgenti: subnet, ISP, ASN, paese, città, versione protocollo.
     */
    public static String generate(String ip, int subnetMask, GeoIPClient.GeoData geo, int protocol) {
        String subnet = getSubnet(ip, subnetMask);
        String raw = String.join("|",
                subnet,
                geo.isp(),
                geo.asn(),
                geo.country(),
                geo.city(),
                String.valueOf(protocol));
        return sha256(raw) + "|raw=" + raw; // usa buildRaw() per il raw string
    }

    /** Restituisce il raw string usato per l'hash (senza prefisso). */
    public static String buildRaw(String ip, int subnetMask, GeoIPClient.GeoData geo, int protocol) {
        return String.join("|",
                getSubnet(ip, subnetMask),
                geo.isp(),
                geo.asn(),
                geo.country(),
                geo.city(),
                String.valueOf(protocol));
    }

    /** Controlla se un IP è locale/privato (nessuna chiamata GeoIP necessaria). */
    public static boolean isPrivate(String ip) {
        return ip.startsWith("127.")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("172.16.")
                || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1");
    }
}
