package com.eyren.hWIDBan.config;

import com.eyren.hWIDBan.Main;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class ConfigManager {

    private final Main plugin;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        plugin.reloadConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public boolean isSourceEnabled(String key) {
        return plugin.getConfig().getBoolean("fingerprint.sources." + key, true);
    }

    public int subnetMask() {
        return plugin.getConfig().getInt("fingerprint.subnet-mask", 20);
    }

    public boolean debug() {
        return plugin.getConfig().getBoolean("settings.debug", false);
    }
}
