package com.eyren.hWIDBan.listener;

import com.eyren.hWIDBan.Main;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

public class PluginChannelListener implements PluginMessageListener {

    private static final String BRAND_CHANNEL = "minecraft:brand";

    private final Main plugin;

    public PluginChannelListener(Main plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BRAND_CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BRAND_CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int length = readVarInt(in);
            byte[] buf = new byte[length];
            in.readFully(buf);
            String brand = new String(buf, StandardCharsets.UTF_8);
            plugin.getClientCollector().setBrand(player, brand);
            if (plugin.getConfigManager().debug()) {
                plugin.getLogger().info("Brand di " + player.getName() + ": " + brand);
            }
        } catch (Throwable t) {
            if (plugin.getConfigManager().debug()) {
                plugin.getLogger().warning("Impossibile leggere brand channel: " + t.getMessage());
            }
        }
    }

    private int readVarInt(DataInputStream in) throws Exception {
        int value = 0;
        int position = 0;
        byte current;
        while (true) {
            current = in.readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new RuntimeException("VarInt too big");
        }
        return value;
    }
}
