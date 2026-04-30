package com.eyren.hWIDBan.velocity.sync;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;

/**
 * Redis PubSub per sincronizzare ban/unban tra più proxy + backend.
 * Pubblica su un canale; gli altri istanze ricevono e applicano l'azione (kick player se online).
 *
 * Filtraggio loop:
 * Ogni messaggio ha un nodeId univoco. L'istanza che pubblica ignora i propri messaggi.
 */
public class RedisSync {

    private final VelocityPlugin plugin;
    private final String         nodeId = UUID.randomUUID().toString();

    private JedisPool pool;
    private Thread    subscriberThread;
    private JedisPubSub subscriber;
    private boolean   enabled;
    private String    channel;

    public RedisSync(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() { return enabled; }

    public void start() {
        enabled = plugin.getVelocityConfig().getBoolean("sync.redis.enabled", false);
        if (!enabled) return;

        String host    = plugin.getVelocityConfig().getString("sync.redis.host", "localhost");
        int    port    = plugin.getVelocityConfig().getInt("sync.redis.port", 6379);
        String pass    = plugin.getVelocityConfig().getString("sync.redis.password", "");
        channel        = plugin.getVelocityConfig().getString("sync.redis.channel", "hwidban-sync");

        try {
            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(8);
            cfg.setMaxIdle(4);
            cfg.setMinIdle(1);

            DefaultJedisClientConfig.Builder cb = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(5000)
                    .socketTimeoutMillis(5000);
            if (pass != null && !pass.isEmpty()) cb.password(pass);
            JedisClientConfig clientConfig = cb.build();
            pool = new JedisPool(cfg, new HostAndPort(host, port), clientConfig);

            // Subscribe thread (bloccante – richiede thread dedicato)
            subscriber = new JedisPubSub() {
                @Override public void onMessage(String ch, String msg) { handle(msg); }
            };
            subscriberThread = new Thread(() -> {
                while (enabled && !Thread.currentThread().isInterrupted()) {
                    try (var jedis = pool.getResource()) {
                        jedis.subscribe(subscriber, channel);
                    } catch (Exception e) {
                        plugin.getLogger().warn("[Redis] Subscribe lost: {} — retry in 5s", e.getMessage());
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
                    }
                }
            }, "HWIDBan-Redis-Sub");
            subscriberThread.setDaemon(true);
            subscriberThread.start();

            plugin.getLogger().info("[Redis] Connected to {}:{} channel={} nodeId={}", host, port, channel, nodeId);
        } catch (Exception e) {
            plugin.getLogger().error("[Redis] Connection failed: {}", e.getMessage(), e);
            enabled = false;
        }
    }

    public void publishBan(BanEntry entry) {
        if (!enabled || pool == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "BAN");
        o.addProperty("node", nodeId);
        o.addProperty("fingerprint", entry.fingerprint());
        o.addProperty("playerName",  entry.playerName());
        o.addProperty("playerUuid",  entry.playerUuid());
        o.addProperty("reason",      entry.reason());
        o.addProperty("bannedBy",    entry.bannedBy());
        o.addProperty("bannedAt",    entry.bannedAt());
        o.addProperty("expiresAt",   entry.expiresAt());
        publish(o.toString());
    }

    public void publishUnban(String fingerprint) {
        if (!enabled || pool == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("type", "UNBAN");
        o.addProperty("node", nodeId);
        o.addProperty("fingerprint", fingerprint);
        publish(o.toString());
    }

    private void publish(String msg) {
        try (var jedis = pool.getResource()) {
            jedis.publish(channel, msg);
        } catch (Exception e) {
            plugin.getLogger().warn("[Redis] Publish error: {}", e.getMessage());
        }
    }

    /** Riceve un messaggio dal canale Redis. */
    private void handle(String msg) {
        try {
            JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
            String node = o.has("node") ? o.get("node").getAsString() : "";
            if (nodeId.equals(node)) return; // ignora i propri messaggi

            String type = o.has("type") ? o.get("type").getAsString() : "";
            if ("BAN".equals(type)) {
                String uuid = o.has("playerUuid") && !o.get("playerUuid").isJsonNull()
                        ? o.get("playerUuid").getAsString() : null;
                if (uuid == null) return;
                // Kick il player se online sul questo proxy
                try {
                    plugin.getServer().getPlayer(UUID.fromString(uuid)).ifPresent(p -> {
                        BanEntry entry = plugin.getDb().getActiveBanByUUID(uuid);
                        if (entry != null) p.disconnect(plugin.getBanManager().buildKickMessage(entry));
                    });
                } catch (Exception ignored) {}
            }
            // UNBAN non richiede azione: il player non è più nel DB, i prossimi join sono ammessi.
        } catch (Exception e) {
            plugin.getLogger().warn("[Redis] Handle error: {}", e.getMessage());
        }
    }

    public void shutdown() {
        enabled = false;
        try { if (subscriber != null && subscriber.isSubscribed()) subscriber.unsubscribe(); } catch (Exception ignored) {}
        try { if (subscriberThread != null) subscriberThread.interrupt(); } catch (Exception ignored) {}
        try { if (pool != null) pool.close(); } catch (Exception ignored) {}
    }
}
