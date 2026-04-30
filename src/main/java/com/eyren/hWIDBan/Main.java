package com.eyren.hWIDBan;

import com.eyren.hWIDBan.ban.BanManager;
import com.eyren.hWIDBan.ban.WhitelistManager;
import com.eyren.hWIDBan.collector.BehaviorCollector;
import com.eyren.hWIDBan.collector.ClientCollector;
import com.eyren.hWIDBan.collector.GeoIPCollector;
import com.eyren.hWIDBan.collector.IPCollector;
import com.eyren.hWIDBan.collector.NetworkCollector;
import com.eyren.hWIDBan.collector.ScreenCollector;
import com.eyren.hWIDBan.commands.HwidAltCommand;
import com.eyren.hWIDBan.commands.HwidBanCommand;
import com.eyren.hWIDBan.commands.HwidCheckCommand;
import com.eyren.hWIDBan.commands.HwidHelpCommand;
import com.eyren.hWIDBan.commands.HwidHistoryCommand;
import com.eyren.hWIDBan.commands.HwidInfoCommand;
import com.eyren.hWIDBan.commands.HwidListCommand;
import com.eyren.hWIDBan.commands.HwidPurgeCommand;
import com.eyren.hWIDBan.commands.HwidReloadCommand;
import com.eyren.hWIDBan.commands.HwidStatsCommand;
import com.eyren.hWIDBan.commands.HwidTempBanCommand;
import com.eyren.hWIDBan.commands.HwidUnbanCommand;
import com.eyren.hWIDBan.commands.HwidWhitelistCommand;
import com.eyren.hWIDBan.config.ConfigManager;
import com.eyren.hWIDBan.database.DatabaseManager;
import com.eyren.hWIDBan.fingerprint.FingerprintGenerator;
import com.eyren.hWIDBan.integrations.LiteBansHook;
import com.eyren.hWIDBan.listener.BehaviorListener;
import com.eyren.hWIDBan.listener.ConnectionListener;
import com.eyren.hWIDBan.listener.PluginChannelListener;
import com.eyren.hWIDBan.listener.PreLoginListener;
import com.eyren.hWIDBan.sync.RedisSync;
import com.eyren.hWIDBan.util.DiscordWebhook;
import com.eyren.hWIDBan.util.MessageUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RedisSync redisSync;
    private BanManager banManager;
    private WhitelistManager whitelistManager;
    private FingerprintGenerator fingerprintGenerator;
    private MessageUtil messageUtil;
    private DiscordWebhook discordWebhook;
    private LiteBansHook liteBansHook;

    private IPCollector ipCollector;
    private ClientCollector clientCollector;
    private GeoIPCollector geoIPCollector;
    private BehaviorCollector behaviorCollector;
    private ScreenCollector screenCollector;
    private NetworkCollector networkCollector;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageUtil = new MessageUtil(this);
        this.discordWebhook = new DiscordWebhook(this);

        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.connect()) {
            getLogger().severe("Impossibile connettersi al database. Disabilito il plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.redisSync = new RedisSync(this);
        this.redisSync.start();

        this.ipCollector = new IPCollector(this);
        this.clientCollector = new ClientCollector(this);
        this.geoIPCollector = new GeoIPCollector(this);
        this.behaviorCollector = new BehaviorCollector(this);
        this.screenCollector = new ScreenCollector(this);
        this.networkCollector = new NetworkCollector(this);

        this.fingerprintGenerator = new FingerprintGenerator(this);
        this.banManager = new BanManager(this);
        this.whitelistManager = new WhitelistManager(this);

        registerListeners();
        registerCommands();

        // Integrazione LiteBans (no-op se LiteBans non installato)
        this.liteBansHook = new LiteBansHook(this);
        this.liteBansHook.tryRegister();

        getLogger().info("HWIDBan v" + getDescription().getVersion() + " abilitato.");
    }

    @Override
    public void onDisable() {
        if (redisSync != null) redisSync.shutdown();
        if (databaseManager != null) databaseManager.disconnect();
        if (behaviorCollector != null) behaviorCollector.clear();
        getLogger().info("HWIDBan disabilitato.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PreLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BehaviorListener(this), this);
        new PluginChannelListener(this).register();
    }

    private void registerCommands() {
        reg("hwidban",       new HwidBanCommand(this));
        reg("hwidtempban",   new HwidTempBanCommand(this));
        reg("hwidunban",     new HwidUnbanCommand(this));
        reg("hwidcheck",     new HwidCheckCommand(this));
        reg("hwidlist",      new HwidListCommand(this));
        reg("hwidalt",       new HwidAltCommand(this));
        reg("hwidhistory",   new HwidHistoryCommand(this));
        reg("hwidinfo",      new HwidInfoCommand(this));
        reg("hwidstats",     new HwidStatsCommand(this));
        reg("hwidpurge",     new HwidPurgeCommand(this));
        reg("hwidwhitelist", new HwidWhitelistCommand(this));
        reg("hwidreload",    new HwidReloadCommand(this));
        reg("hwidhelp",      new HwidHelpCommand(this));
    }

    private void reg(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Comando '" + name + "' non trovato in plugin.yml — skipping.");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter) cmd.setTabCompleter((TabCompleter) executor);
    }

    /* ── getters ── */
    public ConfigManager getConfigManager()         { return configManager; }
    public DatabaseManager getDatabaseManager()     { return databaseManager; }
    public RedisSync getRedisSync()                 { return redisSync; }
    public BanManager getBanManager()               { return banManager; }
    public WhitelistManager getWhitelistManager()   { return whitelistManager; }
    public FingerprintGenerator getFingerprintGenerator() { return fingerprintGenerator; }
    public MessageUtil getMessageUtil()             { return messageUtil; }
    public DiscordWebhook getDiscordWebhook()       { return discordWebhook; }
    public LiteBansHook getLiteBansHook()           { return liteBansHook; }

    public IPCollector getIpCollector()             { return ipCollector; }
    public ClientCollector getClientCollector()     { return clientCollector; }
    public GeoIPCollector getGeoIPCollector()       { return geoIPCollector; }
    public BehaviorCollector getBehaviorCollector() { return behaviorCollector; }
    public ScreenCollector getScreenCollector()     { return screenCollector; }
    public NetworkCollector getNetworkCollector()   { return networkCollector; }
}
