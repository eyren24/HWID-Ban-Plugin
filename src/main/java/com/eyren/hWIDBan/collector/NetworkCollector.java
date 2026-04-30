package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkCollector {

    private final Main plugin;
    private final Map<UUID, Deque<Long>> packetTimings = new ConcurrentHashMap<>();

    public NetworkCollector(Main plugin) {
        this.plugin = plugin;
    }

    public String getPingPattern(Player player) {
        int ping = getPing(player);
        int bucket = ping / 10;
        return String.valueOf(bucket);
    }

    public int getPing(Player player) {
        try {
            Method m = Player.class.getMethod("getPing");
            Object o = m.invoke(player);
            if (o instanceof Integer) return (int) o;
        } catch (Throwable ignored) {}
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return (int) handle.getClass().getField("ping").get(handle);
        } catch (Throwable ignored) {}
        return 0;
    }

    public void recordPacket(Player player) {
        int max = plugin.getConfig().getInt("fingerprint.packet-timing-samples", 10);
        Deque<Long> deque = packetTimings.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            if (!deque.isEmpty()) {
                long delta = now - deque.peekLast();
                if (delta > 0 && delta < 1000) {
                    deque.addLast(now);
                    while (deque.size() > max + 1) deque.pollFirst();
                } else {
                    deque.clear();
                    deque.addLast(now);
                }
            } else {
                deque.addLast(now);
            }
        }
    }

    public String getPacketTiming(Player player) {
        Deque<Long> deque = packetTimings.get(player.getUniqueId());
        if (deque == null) return "0";
        synchronized (deque) {
            if (deque.size() < 2) return "0";
            StringBuilder sb = new StringBuilder();
            Long prev = null;
            for (Long t : deque) {
                if (prev != null) {
                    long delta = (t - prev) / 10;
                    if (sb.length() > 0) sb.append('|');
                    sb.append(delta);
                }
                prev = t;
            }
            return sb.toString();
        }
    }

    public void clear(Player player) {
        packetTimings.remove(player.getUniqueId());
    }
}
