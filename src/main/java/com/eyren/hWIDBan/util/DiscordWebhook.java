package com.eyren.hWIDBan.util;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DiscordWebhook {

    private final Main plugin;

    public DiscordWebhook(Main plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false)
                && !plugin.getConfig().getString("discord.webhook-url", "").isEmpty();
    }

    public void sendBan(BanEntry entry, String type) {
        if (!enabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", "HWID " + type);
                embed.addProperty("color", "BAN".equals(type) ? 0xFF3030 : 0x30FF60);
                embed.addProperty("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));

                JsonArray fields = new JsonArray();
                fields.add(field("Player", entry.playerName() == null ? "-" : entry.playerName(), true));
                fields.add(field("UUID", entry.playerUuid() == null ? "-" : entry.playerUuid(), true));
                fields.add(field("Admin", entry.bannedBy() == null ? "CONSOLE" : entry.bannedBy(), true));
                fields.add(field("Reason", entry.reason() == null ? "-" : entry.reason(), false));
                fields.add(field("Fingerprint", "`" + entry.fingerprint() + "`", false));
                if (!entry.isPermanent()) {
                    fields.add(field("Expires in", DurationParser.remaining(entry.expiresAt()), true));
                }
                embed.add("fields", fields);

                JsonArray embeds = new JsonArray();
                embeds.add(embed);

                JsonObject payload = new JsonObject();
                payload.addProperty("username", plugin.getConfig().getString("discord.username", "HWIDBan"));
                String avatar = plugin.getConfig().getString("discord.avatar-url", "");
                if (!avatar.isEmpty()) payload.addProperty("avatar_url", avatar);
                payload.add("embeds", embeds);

                post(payload.toString());
            } catch (Throwable t) {
                if (plugin.getConfigManager().debug()) {
                    plugin.getLogger().warning("Discord webhook error: " + t.getMessage());
                }
            }
        });
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }

    private void post(String json) throws Exception {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "HWIDBan-Plugin");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code >= 400 && plugin.getConfigManager().debug()) {
            plugin.getLogger().warning("Discord webhook HTTP " + code);
        }
        conn.disconnect();
    }
}
