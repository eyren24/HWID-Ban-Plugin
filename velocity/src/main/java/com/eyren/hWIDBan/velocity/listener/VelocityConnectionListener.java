package com.eyren.hWIDBan.velocity.listener;

import com.eyren.hWIDBan.velocity.VelocityPlugin;
import com.eyren.hWIDBan.velocity.database.BanEntry;
import com.eyren.hWIDBan.velocity.util.FingerprintUtil;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gate di sicurezza a livello proxy (Velocity).
 *
 *   PreLoginEvent  (async)  → rate limit IP
 *   PostLoginEvent (async)  → whitelist → UUID ban → FP history → subnet → save FP → auto-alt
 *   ServerPreConnectEvent   → kick se in pendingKicks
 *   DisconnectEvent         → cleanup
 */
public class VelocityConnectionListener {

    private final VelocityPlugin plugin;
    private final Map<UUID, BanEntry> pendingKicks = new ConcurrentHashMap<>();

    public VelocityConnectionListener(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    /* ─── STAGE 1: PreLoginEvent — rate limit ────────────────────────── */

    @Subscribe(order = PostOrder.FIRST)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

            if (plugin.getBanManager().isRateLimited(ip)) {
                String msg = plugin.getVelocityConfig().getString(
                        "messages.kick.rate-limited", "&cToo many login attempts. Please wait.");
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(msg)));
                return;
            }
            plugin.getBanManager().recordAttempt(ip);
        });
    }

    /* ─── STAGE 2: PostLoginEvent — UUID + whitelist + FP history ───── */

    @Subscribe(order = PostOrder.FIRST)
    public EventTask onPostLogin(PostLoginEvent event) {
        return EventTask.async(() -> {
            Player player = event.getPlayer();
            String uuid   = player.getUniqueId().toString();
            String ip     = player.getRemoteAddress().getAddress().getHostAddress();

            int mask = plugin.getVelocityConfig().getInt("fingerprint.subnet-mask", 20);
            String subnet = FingerprintUtil.getSubnet(ip, mask);
            GeoIPClient.GeoData geo = plugin.getGeoIP().lookup(ip);

            // Whitelist → bypass totale
            if (plugin.getWhitelistManager().isWhitelisted(ip, subnet, geo)) return;

            // Permesso bypass
            if (player.hasPermission("hwidban.bypass")) return;

            if (plugin.getBanManager().isWatchMode()) {
                captureFingerprint(player, ip, mask, geo);
                plugin.getLogger().info("[WATCH] {} (UUID={}) ip={}", player.getUsername(), uuid, ip);
                return;
            }

            // 1) UUID diretto
            BanEntry directBan = plugin.getDb().getActiveBanByUUID(uuid);
            if (directBan != null && !directBan.isExpired()) {
                pendingKicks.put(player.getUniqueId(), directBan);
                return;
            }

            // 2) Fingerprint history → ban per quel fingerprint
            List<String> fps = plugin.getDb().getFingerprintsForPlayer(uuid);
            for (String fp : fps) {
                BanEntry ban = plugin.getDb().getBan(fp);
                if (ban != null && !ban.isExpired()) {
                    pendingKicks.put(player.getUniqueId(), ban);
                    return;
                }
            }

            // 3) Subnet match — un fingerprint bannato che condivide la stessa subnet
            BanEntry subnetMatch = plugin.getDb().findBanByRawContains(subnet);
            if (subnetMatch != null && !subnetMatch.isExpired()) {
                pendingKicks.put(player.getUniqueId(), subnetMatch);
                return;
            }

            // Non bannato → salva fingerprint + auto-alt detection
            String fingerprint = captureFingerprint(player, ip, mask, geo);
            if (fingerprint != null) {
                plugin.getBanManager().maybeAutoAltBan(fingerprint, player.getUsername(), uuid);
            }
        });
    }

    /* ─── STAGE 3: ServerPreConnectEvent — esegui kick ───────────────── */

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        BanEntry ban = pendingKicks.remove(event.getPlayer().getUniqueId());
        if (ban == null) return;
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        Component msg = plugin.getBanManager().buildKickMessage(ban);
        event.getPlayer().disconnect(msg);
    }

    /* ─── CLEANUP ────────────────────────────────────────────────────── */

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pendingKicks.remove(event.getPlayer().getUniqueId());
    }

    /* ─── FP CAPTURE ─────────────────────────────────────────────────── */

    private String captureFingerprint(Player player, String ip, int mask, GeoIPClient.GeoData geo) {
        try {
            int protocol = player.getProtocolVersion().getProtocol();
            String raw   = FingerprintUtil.buildRaw(ip, mask, geo, protocol);
            String hash  = FingerprintUtil.sha256(raw);
            plugin.getDb().saveFingerprint(player.getUniqueId().toString(), hash, raw);

            if (plugin.getVelocityConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().info("[DEBUG] {} fp={} raw={}", player.getUsername(), hash, raw);
            }
            return hash;
        } catch (Exception e) {
            plugin.getLogger().warn("[VelocityFP] Error for {}: {}", player.getUsername(), e.getMessage());
            return null;
        }
    }
}
