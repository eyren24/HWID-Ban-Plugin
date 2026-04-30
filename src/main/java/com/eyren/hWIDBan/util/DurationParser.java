package com.eyren.hWIDBan.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([smhdwMy])");

    private DurationParser() {}

    public static long parseMillis(String input) {
        if (input == null) return -1;
        input = input.trim();
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent") || input.equals("0")) return 0;
        Matcher m = TOKEN.matcher(input);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            char unit = m.group(2).charAt(0);
            switch (unit) {
                case 's': total += TimeUnit.SECONDS.toMillis(n); break;
                case 'm': total += TimeUnit.MINUTES.toMillis(n); break;
                case 'h': total += TimeUnit.HOURS.toMillis(n); break;
                case 'd': total += TimeUnit.DAYS.toMillis(n); break;
                case 'w': total += TimeUnit.DAYS.toMillis(n * 7); break;
                case 'M': total += TimeUnit.DAYS.toMillis(n * 30); break;
                case 'y': total += TimeUnit.DAYS.toMillis(n * 365); break;
                default: return -1;
            }
        }
        return any ? total : -1;
    }

    public static String format(long millis) {
        if (millis <= 0) return "permanent";
        long secs = millis / 1000;
        long days = secs / 86400; secs %= 86400;
        long hours = secs / 3600; secs %= 3600;
        long mins = secs / 60; secs %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        if (sb.length() == 0) sb.append(secs).append("s");
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    public static String remaining(long expiresAt) {
        if (expiresAt <= 0) return "permanent";
        long delta = expiresAt - System.currentTimeMillis();
        if (delta <= 0) return "expired";
        return format(delta);
    }
}
