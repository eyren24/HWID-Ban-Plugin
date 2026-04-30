package com.eyren.hWIDBan.sync;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.database.BanEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

/**
 * Redis PubSub sincronizzazione cross-server.
 *
 * Formato messaggio JSON (compatibile con HWIDBan-Velocity):
 *   {"type":"BAN"|"UNBAN", "node":"<uuid>", "fingerprint":"...",
 *    "playerName":"...", "playerUuid":"...", "reason":"...", "bannedBy":"...",
 *    "bannedAt":<ms>, "expiresAt":<ms>}
 *
 * I messaggi originati dal nodo locale sono ignorati (loop prevention via nodeId).
 */
public class RedisSync {

    private final Main plugin;
    private final String nodeId = UUID.randomUUID().toString();

    private JedisPool pool;
    private String channel;
    private Thread subscriberThread;
    private volatile boolean running;

    public RedisSync(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return running && pool != null;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("sync.redis.enabled", false)) return;
        try {
            String host = plugin.getConfig().getString("sync.redis.host", "localhost");
            int port = plugin.getConfig().getInt("sync.redis.port", 6379);
            String pass = plugin.getConfig().getString("sync.redis.password", "");
            this.channel = plugin.getConfig().getString("sync.redis.channel", "hwidban-sync");

            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(8);
            cfg.setMaxIdle(4);
            cfg.setMinIdle(1);
            if (pass == null || pass.isEmpty()) {
                pool = new JedisPool(cfg, host, port);
            } else {
                pool = new JedisPool(cfg, host, port, 2000, pass);
            }
            running = true;
            subscriberThread = new Thread(this::subscribe, "HWIDBan-Redis-Subscriber");
            subscriberThread.setDaemon(true);
            subscriberThread.start();
            plugin.getLogger().info("[Redis] Connected to " + host + ":" + port + " channel=" + channel + " nodeId=" + nodeId);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Redis] Cannot start: " + t.getMessage());
        }
    }

    private void subscribe() {
        while (running) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        handleMessage(message);
                    }
                }, channel);
            } catch (Throwable t) {
                if (running) {
                    plugin.getLogger().warning("[Redis] Subscribe lost: " + t.getMessage() + " — retry in 5s");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /**
     * Riceve un messaggio dal canale Redis.
     * BAN  → kick il player se online localmente con quel UUID
     * UNBAN → niente da fare (il login successivo passerà)
     */
    private void handleMessage(String message) {
        try {
            JsonObject o = JsonParser.parseString(message).getAsJsonObject();
            String origin = o.has("node") ? o.get("node").getAsString() : "";
            if (nodeId.equals(origin)) return; // ignora i propri messaggi

            String type = o.has("type") ? o.get("type").getAsString() : "";
            if (plugin.getConfigManager().debug()) {
                plugin.getLogger().info("[Redis] recv " + type + " from node=" + origin.substring(0, 8));
            }

            if ("BAN".equals(type)) {
                String uuidStr = o.has("playerUuid") && !o.get("playerUuid").isJsonNull()
                        ? o.get("playerUuid").getAsString() : null;
                if (uuidStr == null) return;
                // Esegui sul main thread per il kick
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Player p = Bukkit.getPlayer(UUID.fromString(uuidStr));
                        if (p != null) {
                            BanEntry entry = plugin.getDatabaseManager().getActiveBanByUUID(uuidStr);
                            if (entry != null) {
                                p.kick(com.eyren.hWIDBan.util.Colors.toComponent(
                                        plugin.getBanManager().buildKickMessage(entry)));
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }
            // UNBAN: nessuna azione necessaria — il prossimo login passerà perché
            // getActiveBanByUUID ritorna null sul DB sincronizzato.
        } catch (Throwable t) {
            plugin.getLogger().warning("[Redis] Parse error: " + t.getMessage());
        }
    }

    public void publishBan(BanEntry entry) {
        if (pool == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "BAN");
        o.addProperty("node", nodeId);
        o.addProperty("fingerprint", entry.fingerprint());
        o.addProperty("playerName", entry.playerName());
        o.addProperty("playerUuid", entry.playerUuid());
        o.addProperty("reason", entry.reason());
        o.addProperty("bannedBy", entry.bannedBy());
        o.addProperty("bannedAt", entry.bannedAt());
        o.addProperty("expiresAt", entry.expiresAt());
        publish(o.toString());
    }

    public void publishUnban(String fingerprint) {
        if (pool == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "UNBAN");
        o.addProperty("node", nodeId);
        o.addProperty("fingerprint", fingerprint);
        publish(o.toString());
    }

    private void publish(String msg) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, msg);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Redis] Publish error: " + t.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null) pool.close();
    }
}
