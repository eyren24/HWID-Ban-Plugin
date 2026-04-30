package com.eyren.hWIDBan.velocity.util;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Webhook Discord async per notifiche di ban / unban.
 * Tutti gli invii avvengono su un thread pool dedicato — non blocca mai l'event loop.
 */
public class DiscordWebhook {

    private final VelocityPlugin plugin;
    private final HttpClient http;
    private final java.util.concurrent.Executor exec =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HWIDBan-Discord");
                t.setDaemon(true);
                return t;
            });

    public DiscordWebhook(VelocityPlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enabled() {
        return plugin.getVelocityConfig().getBoolean("discord.enabled", false)
                && !plugin.getVelocityConfig().getString("discord.webhook-url", "").isEmpty();
    }

    /** type = "BAN" oppure "UNBAN" */
    public void sendBan(BanEntry entry, String type) {
        if (!enabled()) return;

        CompletableFuture.runAsync(() -> {
            try {
                String url = plugin.getVelocityConfig().getString("discord.webhook-url", "");
                String username = plugin.getVelocityConfig().getString("discord.username", "HWIDBan");
                String avatar = plugin.getVelocityConfig().getString("discord.avatar-url", "");

                JsonObject payload = new JsonObject();
                payload.addProperty("username", username);
                if (!avatar.isEmpty()) payload.addProperty("avatar_url", avatar);

                JsonObject embed = new JsonObject();
                embed.addProperty("title", "BAN".equals(type) ? "🔨 Player Banned" : "✅ Player Unbanned");
                embed.addProperty("color", "BAN".equals(type) ? 0xE74C3C : 0x2ECC71);
                embed.addProperty("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        .format(new Date()));

                JsonArray fields = new JsonArray();
                fields.add(field("Player",   safe(entry.playerName(),  "?"),    true));
                fields.add(field("UUID",     safe(entry.playerUuid(),  "?"),    true));
                fields.add(field("Admin",    safe(entry.bannedBy(),    "CONSOLE"), true));
                fields.add(field("Reason",   safe(entry.reason(),      "-"),    false));
                fields.add(field("Fingerprint", "`" + entry.fingerprint() + "`", false));
                if (!entry.isPermanent() && "BAN".equals(type)) {
                    fields.add(field("Expires",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.expiresAt())),
                            false));
                }
                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "HWIDBan-Velocity");
                embed.add("footer", footer);

                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                payload.add("embeds", embeds);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) {
                    plugin.getLogger().warn("[Discord] Webhook HTTP {} — body: {}", resp.statusCode(), resp.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warn("[Discord] Webhook error: {}", e.getMessage());
            }
        }, exec);
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("value", value);
        o.addProperty("inline", inline);
        return o;
    }

    private static String safe(String s, String def) {
        return s != null && !s.isEmpty() ? s : def;
    }
}
