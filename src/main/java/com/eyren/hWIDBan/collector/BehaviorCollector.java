package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BehaviorCollector {

    private final Main plugin;
    private final Map<UUID, BehaviorSession> sessions = new ConcurrentHashMap<>();

    public BehaviorCollector(Main plugin) {
        this.plugin = plugin;
    }

    public BehaviorSession start(Player player) {
        BehaviorSession s = new BehaviorSession();
        sessions.put(player.getUniqueId(), s);
        return s;
    }

    public BehaviorSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void stop(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void clear() {
        sessions.clear();
    }

    public static class BehaviorSession {
        private long clicks;
        private long movements;
        private long inventoryActions;
        private final long startTime = System.currentTimeMillis();

        public void incrementClicks() { clicks++; }
        public void incrementMovements() { movements++; }
        public void incrementInventory() { inventoryActions++; }

        public double averageCPS() {
            long secs = Math.max(1, (System.currentTimeMillis() - startTime) / 1000);
            return (double) clicks / secs;
        }

        public long clicks() { return clicks; }
        public long movements() { return movements; }
        public long inventoryActions() { return inventoryActions; }
    }
}
