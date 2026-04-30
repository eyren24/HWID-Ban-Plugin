package com.eyren.hWIDBan.util;

import org.bukkit.Bukkit;

public final class VersionUtil {

    private static final String SERVER_VERSION = Bukkit.getBukkitVersion();
    private static final int MAJOR;
    private static final int MINOR;

    static {
        int major = 1, minor = 20;
        try {
            String clean = SERVER_VERSION.split("-")[0];
            String[] parts = clean.split("\\.");
            if (parts.length >= 2) major = Integer.parseInt(parts[0]);
            if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        MAJOR = major;
        MINOR = minor;
    }

    private VersionUtil() {}

    public static int major() { return MAJOR; }
    public static int minor() { return MINOR; }

    public static boolean atLeast(int minorVersion) {
        return MAJOR > 1 || (MAJOR == 1 && MINOR >= minorVersion);
    }

    public static String raw() { return SERVER_VERSION; }
}
