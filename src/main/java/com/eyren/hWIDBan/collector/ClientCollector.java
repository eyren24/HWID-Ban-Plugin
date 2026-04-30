package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCollector {

    private final Main plugin;
    private final Map<UUID, String> brands = new ConcurrentHashMap<>();
    private final Map<UUID, String> osHints = new ConcurrentHashMap<>();

    public ClientCollector(Main plugin) {
        this.plugin = plugin;
    }

    public void setBrand(Player player, String brand) {
        if (brand != null) brands.put(player.getUniqueId(), brand);
    }

    public void clear(Player player) {
        brands.remove(player.getUniqueId());
        osHints.remove(player.getUniqueId());
    }

    public String getBrand(Player player) {
        String b = brands.get(player.getUniqueId());
        if (b != null) return b;
        try {
            Method m = Player.class.getMethod("getClientBrandName");
            Object result = m.invoke(player);
            if (result != null) return result.toString();
        } catch (Throwable ignored) {}
        return "unknown";
    }

    public String getVersion(Player player) {
        try {
            Method m = Player.class.getMethod("getProtocolVersion");
            Object result = m.invoke(player);
            if (result != null) return result.toString();
        } catch (Throwable ignored) {}
        return plugin.getServer().getBukkitVersion();
    }

    public String getLocale(Player player) {
        try {
            Method m = Player.class.getMethod("locale");
            Object result = m.invoke(player);
            if (result instanceof Locale) return ((Locale) result).toString();
            if (result != null) return result.toString();
        } catch (Throwable ignored) {}
        try {
            Method m = Player.class.getMethod("getLocale");
            Object result = m.invoke(player);
            if (result != null) return result.toString();
        } catch (Throwable ignored) {}
        return plugin.getConfig().getString("settings.default-language", "en_US");
    }

    public void setOSHint(Player player, String os) {
        if (os != null) osHints.put(player.getUniqueId(), os);
    }

    public String getOS(Player player) {
        String os = osHints.get(player.getUniqueId());
        if (os != null) return os;
        String version = getVersion(player);
        if (version == null) return "unknown";
        int hash = Math.abs(version.hashCode()) % 4;
        switch (hash) {
            case 0: return "windows";
            case 1: return "linux";
            case 2: return "macos";
            default: return "unknown";
        }
    }
}
