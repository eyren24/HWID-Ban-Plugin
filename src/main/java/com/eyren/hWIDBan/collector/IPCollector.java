package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;

public class IPCollector {

    private final Main plugin;

    public IPCollector(Main plugin) {
        this.plugin = plugin;
    }

    public String getIP(Player player) {
        InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) return "0.0.0.0";
        return addr.getAddress().getHostAddress();
    }

    public String getSubnet(String ip, int mask) {
        if (ip == null || ip.isEmpty()) return "0.0.0.0/" + mask;
        try {
            if (ip.contains(":")) {
                return ip + "/" + mask;
            }
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return ip + "/" + mask;
            long ipLong = 0;
            for (int i = 0; i < 4; i++) {
                ipLong = (ipLong << 8) | Integer.parseInt(parts[i]);
            }
            long maskLong = mask == 0 ? 0 : (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
            long network = ipLong & maskLong;
            return String.format("%d.%d.%d.%d/%d",
                    (network >> 24) & 0xFF,
                    (network >> 16) & 0xFF,
                    (network >> 8) & 0xFF,
                    network & 0xFF,
                    mask);
        } catch (Exception e) {
            return ip + "/" + mask;
        }
    }
}
