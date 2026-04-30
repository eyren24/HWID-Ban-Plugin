package com.eyren.hWIDBan.collector;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenCollector {

    private final Main plugin;
    private final Map<UUID, String> resolutions = new ConcurrentHashMap<>();

    public ScreenCollector(Main plugin) {
        this.plugin = plugin;
    }

    public void setResolution(Player player, String resolution) {
        if (resolution != null) resolutions.put(player.getUniqueId(), resolution);
    }

    public String getResolution(Player player) {
        return resolutions.getOrDefault(player.getUniqueId(), "unknown");
    }

    public void clear(Player player) {
        resolutions.remove(player.getUniqueId());
    }
}
