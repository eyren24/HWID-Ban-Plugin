package com.eyren.hWIDBan.velocity;

import com.eyren.hWIDBan.velocity.ban.BanManager;
import com.eyren.hWIDBan.velocity.ban.WhitelistManager;
import com.eyren.hWIDBan.velocity.commands.*;
import com.eyren.hWIDBan.velocity.database.DatabaseManager;
import com.eyren.hWIDBan.velocity.integrations.LiteBansHook;
import com.eyren.hWIDBan.velocity.listener.VelocityConnectionListener;
import com.eyren.hWIDBan.velocity.sync.RedisSync;
import com.eyren.hWIDBan.velocity.util.DiscordWebhook;
import com.eyren.hWIDBan.velocity.util.GeoIPClient;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
        id          = "hwidban-velocity",
        name        = "HWIDBan-Velocity",
        version     = "${project.version}",
        description = "Proxy-level HWID ban gate — full feature parity with Paper plugin",
        authors     = {"Eyren"}
)
public class VelocityPlugin {

    private final ProxyServer server;
    private final Logger      logger;
    private final Path        dataDirectory;

    private VelocityConfig    config;
    private DatabaseManager   db;
    private BanManager        banManager;
    private WhitelistManager  whitelistManager;
    private GeoIPClient       geoIP;
    private DiscordWebhook    discordWebhook;
    private RedisSync         redisSync;
    private LiteBansHook      liteBansHook;

    @Inject
    public VelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server        = server;
        this.logger        = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try { Files.createDirectories(dataDirectory); }
        catch (IOException e) { logger.error("Cannot create data directory", e); }

        // Config
        config = new VelocityConfig(dataDirectory, logger);
        config.load();

        // Database
        db = new DatabaseManager(config, dataDirectory, logger);
        if (!db.initialize()) {
            logger.error("Database init failed! HWIDBan-Velocity will not protect this proxy.");
            return;
        }

        // GeoIP
        geoIP = new GeoIPClient(
                config.getString("geoip.api-url",
                        "http://ip-api.com/json/{ip}?fields=country,city,isp,as,query"),
                config.getInt("geoip.timeout", 5),
                config.getLong("geoip.cache-ttl-minutes", 60) * 60_000L);

        // Discord, Redis, BanManager, WhitelistManager
        discordWebhook   = new DiscordWebhook(this);
        redisSync        = new RedisSync(this);
        redisSync.start();
        banManager       = new BanManager(this);
        whitelistManager = new WhitelistManager(this);

        // Listeners
        server.getEventManager().register(this, new VelocityConnectionListener(this));

        // Commands — parity completa con plugin Paper
        reg("hvban",       new VBanCommand(this),       "hwidban-v");
        reg("hvtempban",   new VTempBanCommand(this),   "hwidtempban-v");
        reg("hvunban",     new VUnbanCommand(this),     "hwidunban-v");
        reg("hvcheck",     new VCheckCommand(this),     "hwidcheck-v");
        reg("hvlist",      new VListCommand(this),      "hwidlist-v");
        reg("hvalt",       new VAltCommand(this),       "hwidalt-v");
        reg("hvhistory",   new VHistoryCommand(this),   "hwidhistory-v");
        reg("hvinfo",      new VInfoCommand(this),      "hwidinfo-v");
        reg("hvstats",     new VStatsCommand(this),     "hwidstats-v");
        reg("hvpurge",     new VPurgeCommand(this),     "hwidpurge-v");
        reg("hvwhitelist", new VWhitelistCommand(this), "hwidwl-v", "hvwl");
        reg("hvreload",    new VReloadCommand(this),    "hwidreload-v");
        reg("hvhelp",      new VHelpCommand(this),      "hwidhelp-v");

        // Integrazione LiteBans (no-op se LiteBans non installato sul proxy)
        liteBansHook = new LiteBansHook(this);
        liteBansHook.tryRegister();

        logger.info("HWIDBan-Velocity enabled — DB={} | Discord={} | Redis={} | Watch={}",
                config.getString("database.type", "SQLITE"),
                discordWebhook.enabled(),
                redisSync.enabled(),
                banManager.isWatchMode());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (redisSync != null) redisSync.shutdown();
        if (db != null)        db.shutdown();
        logger.info("HWIDBan-Velocity disabled.");
    }

    private void reg(String name, SimpleCommand cmd, String... aliases) {
        CommandMeta meta = server.getCommandManager()
                .metaBuilder(name).aliases(aliases).plugin(this).build();
        server.getCommandManager().register(meta, cmd);
    }

    // ─── Getters ──────────────────────────────────────────────────────────

    public ProxyServer       getServer()           { return server; }
    public Logger            getLogger()           { return logger; }
    public Path              getDataDirectory()    { return dataDirectory; }
    public VelocityConfig    getVelocityConfig()   { return config; }
    public DatabaseManager   getDb()               { return db; }
    public BanManager        getBanManager()       { return banManager; }
    public WhitelistManager  getWhitelistManager() { return whitelistManager; }
    public GeoIPClient       getGeoIP()            { return geoIP; }
    public DiscordWebhook    getDiscordWebhook()   { return discordWebhook; }
    public RedisSync         getRedisSync()        { return redisSync; }
    public LiteBansHook      getLiteBansHook()     { return liteBansHook; }
}
