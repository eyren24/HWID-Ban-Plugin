package com.eyren.hWIDBan.database;

import com.eyren.hWIDBan.Main;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DatabaseManager {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private final Main plugin;

    // SQLite: connessione diretta (no pool — SQLite non lo supporta bene)
    private File sqliteFile;

    // MySQL: pool HikariCP
    private HikariDataSource mysqlPool;

    private boolean sqlite;
    private String bansTable;
    private String fingerprintsTable;
    private String whitelistTable;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    /* ─────────────────────────────────────────────
       SETUP
    ───────────────────────────────────────────── */

    public boolean connect() {
        bansTable        = sanitize(plugin.getConfig().getString("database.tables.bans",         "hwidban_bans"),         "hwidban_bans");
        fingerprintsTable = sanitize(plugin.getConfig().getString("database.tables.fingerprints", "hwidban_fingerprints"), "hwidban_fingerprints");
        whitelistTable   = sanitize(plugin.getConfig().getString("database.tables.whitelist",     "hwidban_whitelist"),    "hwidban_whitelist");

        String type = plugin.getConfig().getString("database.type", "SQLITE").trim().toUpperCase();

        try {
            if ("MYSQL".equals(type)) {
                return connectMySQL();
            } else {
                return connectSQLite();
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Errore fatale DB: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    private boolean connectSQLite() {
        sqlite = true;
        sqliteFile = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("database.sqlite.file", "hwidban.db"));

        // crea la directory del plugin se non esiste
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        // carica il driver esplicitamente (necessario in ambienti shaded)
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("Driver SQLite non trovato! Assicurati che sqlite-jdbc sia nel jar shaded.");
            plugin.getLogger().severe("Dettaglio: " + e.getMessage());
            return false;
        }

        // test connessione
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            // WAL mode: migliori performance e gestione concorrenza
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossibile aprire il file SQLite: " + sqliteFile.getAbsolutePath());
            plugin.getLogger().severe("Errore: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        try {
            createTables();
            migrate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione delle tabelle: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        plugin.getLogger().info("SQLite connesso: " + sqliteFile.getName());
        return true;
    }

    private boolean connectMySQL() {
        sqlite = false;
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int    port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String db   = plugin.getConfig().getString("database.mysql.database", "hwidban");
        String user = plugin.getConfig().getString("database.mysql.username", "root");
        String pass = plugin.getConfig().getString("database.mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        int poolSz  = plugin.getConfig().getInt("database.mysql.pool-size", 10);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=" + ssl
                + "&autoReconnect=true"
                + "&characterEncoding=utf8"
                + "&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setMaximumPoolSize(poolSz);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(10_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("HWIDBan-MySQL");

        try {
            mysqlPool = new HikariDataSource(cfg);
        } catch (Exception e) {
            plugin.getLogger().severe("Impossibile connettersi a MySQL su " + host + ":" + port);
            plugin.getLogger().severe("Errore: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        try {
            createTables();
            migrate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Errore nella creazione delle tabelle MySQL: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        plugin.getLogger().info("MySQL connesso: " + host + ":" + port + "/" + db);
        return true;
    }

    /** Unico punto di accesso alle connessioni — SQLite o MySQL */
    private Connection getConnection() throws SQLException {
        if (sqlite) {
            return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        }
        return mysqlPool.getConnection();
    }

    private String sanitize(String name, String fallback) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            plugin.getLogger().warning("Nome tabella non valido '" + name + "', uso '" + fallback + "'.");
            return fallback;
        }
        return name;
    }

    /* ─────────────────────────────────────────────
       DDL — tabelle e migration
    ───────────────────────────────────────────── */

    private void createTables() throws SQLException {
        exec("CREATE TABLE IF NOT EXISTS " + bansTable + " ("
                + "fingerprint  VARCHAR(64) NOT NULL PRIMARY KEY,"
                + "player_name  VARCHAR(32),"
                + "player_uuid  VARCHAR(36),"
                + "reason       TEXT,"
                + "banned_by    VARCHAR(32),"
                + "banned_at    BIGINT NOT NULL DEFAULT 0,"
                + "expires_at   BIGINT NOT NULL DEFAULT 0"
                + ")");

        exec("CREATE TABLE IF NOT EXISTS " + fingerprintsTable + " ("
                + "player_uuid  VARCHAR(36) NOT NULL,"
                + "fingerprint  VARCHAR(64) NOT NULL,"
                + "raw          TEXT,"
                + "first_seen   BIGINT NOT NULL DEFAULT 0,"
                + "last_seen    BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (player_uuid, fingerprint)"
                + ")");

        exec("CREATE TABLE IF NOT EXISTS " + whitelistTable + " ("
                + "type      VARCHAR(16) NOT NULL,"
                + "value     VARCHAR(128) NOT NULL,"
                + "added_by  VARCHAR(32),"
                + "added_at  BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (type, value)"
                + ")");
    }

    /** Aggiunge colonne mancanti per upgrade da versioni precedenti */
    private void migrate() throws SQLException {
        ensureColumn(bansTable,        "expires_at",  "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(fingerprintsTable, "first_seen", "BIGINT NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (Connection c = getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, table, column)) {
                if (rs.next()) return; // già presente
            }
        }
        // colonna mancante — aggiungila (ignora errore se esiste già per race)
        try {
            exec("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("[Migration] Aggiunta colonna '" + column + "' a " + table);
        } catch (SQLException ignored) {}
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /* ─────────────────────────────────────────────
       BANS
    ───────────────────────────────────────────── */

    public boolean isBanned(String fingerprint) {
        BanEntry e = getBan(fingerprint);
        return e != null && !e.isExpired();
    }

    public BanEntry getBan(String fingerprint) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT * FROM " + bansTable + " WHERE fingerprint=?")) {
            st.setString(1, fingerprint);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return readEntry(rs);
            }
        } catch (SQLException e) {
            warn("getBan", e);
        }
        return null;
    }

    public BanEntry findBanByRawContains(String needle) {
        if (needle == null || needle.isEmpty()) return null;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT b.* FROM " + bansTable + " b "
                             + "JOIN " + fingerprintsTable + " f ON b.fingerprint=f.fingerprint "
                             + "WHERE f.raw LIKE ? LIMIT 1")) {
            st.setString(1, "%" + needle + "%");
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return readEntry(rs);
            }
        } catch (SQLException e) {
            warn("findBanByRawContains", e);
        }
        return null;
    }

    public boolean addBan(BanEntry entry) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "INSERT INTO " + bansTable
                             + " (fingerprint,player_name,player_uuid,reason,banned_by,banned_at,expires_at)"
                             + " VALUES (?,?,?,?,?,?,?)")) {
            st.setString(1, entry.fingerprint());
            st.setString(2, entry.playerName());
            st.setString(3, entry.playerUuid());
            st.setString(4, entry.reason());
            st.setString(5, entry.bannedBy());
            st.setLong(6, entry.bannedAt());
            st.setLong(7, entry.expiresAt());
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            warn("addBan", e);
            return false;
        }
    }

    /**
     * Cerca un ban attivo direttamente per UUID del player.
     * Usato al PreLogin come check primario quando l'UUID è già noto
     * ma la tabella fingerprints potrebbe essere ancora vuota (ban appena emesso).
     */
    public BanEntry getActiveBanByUUID(String uuid) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT * FROM " + bansTable
                             + " WHERE player_uuid=?"
                             + " AND (expires_at=0 OR expires_at>?)"
                             + " LIMIT 1")) {
            st.setString(1, uuid);
            st.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return readEntry(rs);
            }
        } catch (SQLException e) {
            warn("getActiveBanByUUID", e);
        }
        return null;
    }

    public boolean removeBan(String fingerprint) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "DELETE FROM " + bansTable + " WHERE fingerprint=?")) {
            st.setString(1, fingerprint);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            warn("removeBan", e);
            return false;
        }
    }

    public List<BanEntry> listBans(int limit) {
        List<BanEntry> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT * FROM " + bansTable + " ORDER BY banned_at DESC LIMIT ?")) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(readEntry(rs));
            }
        } catch (SQLException e) {
            warn("listBans", e);
        }
        return list;
    }

    private BanEntry readEntry(ResultSet rs) throws SQLException {
        long expires = 0;
        try { expires = rs.getLong("expires_at"); } catch (SQLException ignored) {}
        return new BanEntry(
                rs.getString("fingerprint"),
                rs.getString("player_name"),
                rs.getString("player_uuid"),
                rs.getString("reason"),
                rs.getString("banned_by"),
                rs.getLong("banned_at"),
                expires);
    }

    /* ─────────────────────────────────────────────
       FINGERPRINTS
    ───────────────────────────────────────────── */

    public List<String> getFingerprintsForPlayer(String uuid) {
        List<String> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT fingerprint FROM " + fingerprintsTable
                             + " WHERE player_uuid=? ORDER BY last_seen DESC")) {
            st.setString(1, uuid);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            }
        } catch (SQLException e) {
            warn("getFingerprintsForPlayer", e);
        }
        return list;
    }

    public List<FingerprintRecord> getFingerprintHistory(String uuid, int limit) {
        List<FingerprintRecord> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT fingerprint,raw,first_seen,last_seen FROM " + fingerprintsTable
                             + " WHERE player_uuid=? ORDER BY last_seen DESC LIMIT ?")) {
            st.setString(1, uuid);
            st.setInt(2, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(new FingerprintRecord(
                        rs.getString("fingerprint"),
                        rs.getString("raw"),
                        rs.getLong("first_seen"),
                        rs.getLong("last_seen")));
            }
        } catch (SQLException e) {
            warn("getFingerprintHistory", e);
        }
        return list;
    }

    public void saveFingerprint(String uuid, String fingerprint, String raw) {
        long now = System.currentTimeMillis();
        String sql = sqlite
                ? "INSERT INTO " + fingerprintsTable
                        + " (player_uuid,fingerprint,raw,first_seen,last_seen) VALUES (?,?,?,?,?)"
                        + " ON CONFLICT(player_uuid,fingerprint) DO UPDATE SET raw=excluded.raw, last_seen=excluded.last_seen"
                : "INSERT INTO " + fingerprintsTable
                        + " (player_uuid,fingerprint,raw,first_seen,last_seen) VALUES (?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE raw=VALUES(raw), last_seen=VALUES(last_seen)";

        try (Connection c = getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, uuid);
            st.setString(2, fingerprint);
            st.setString(3, raw);
            st.setLong(4, now);
            st.setLong(5, now);
            st.executeUpdate();
        } catch (SQLException e) {
            warn("saveFingerprint", e);
        }
    }

    public List<String[]> findAlts(String fingerprint, int limit) {
        List<String[]> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT player_uuid,last_seen FROM " + fingerprintsTable
                             + " WHERE fingerprint=? ORDER BY last_seen DESC LIMIT ?")) {
            st.setString(1, fingerprint);
            st.setInt(2, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(new String[] { rs.getString(1), String.valueOf(rs.getLong(2)) });
            }
        } catch (SQLException e) {
            warn("findAlts", e);
        }
        return list;
    }

    public int countSharedAccounts(String fingerprint) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT COUNT(DISTINCT player_uuid) FROM " + fingerprintsTable + " WHERE fingerprint=?")) {
            st.setString(1, fingerprint);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            warn("countSharedAccounts", e);
        }
        return 0;
    }

    public int purgeOldFingerprints(long olderThanMillis) {
        long cutoff = System.currentTimeMillis() - olderThanMillis;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "DELETE FROM " + fingerprintsTable + " WHERE last_seen<?")) {
            st.setLong(1, cutoff);
            return st.executeUpdate();
        } catch (SQLException e) {
            warn("purgeOldFingerprints", e);
            return 0;
        }
    }

    /* ─────────────────────────────────────────────
       WHITELIST
    ───────────────────────────────────────────── */

    public boolean addWhitelist(String type, String value, String addedBy) {
        String sql = sqlite
                ? "INSERT INTO " + whitelistTable + " (type,value,added_by,added_at) VALUES (?,?,?,?)"
                        + " ON CONFLICT(type,value) DO UPDATE SET added_by=excluded.added_by, added_at=excluded.added_at"
                : "INSERT INTO " + whitelistTable + " (type,value,added_by,added_at) VALUES (?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE added_by=VALUES(added_by), added_at=VALUES(added_at)";
        try (Connection c = getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, type);
            st.setString(2, value);
            st.setString(3, addedBy);
            st.setLong(4, System.currentTimeMillis());
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            warn("addWhitelist", e);
            return false;
        }
    }

    public boolean removeWhitelist(String type, String value) {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "DELETE FROM " + whitelistTable + " WHERE type=? AND value=?")) {
            st.setString(1, type);
            st.setString(2, value);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            warn("removeWhitelist", e);
            return false;
        }
    }

    public boolean isWhitelisted(String type, String value) {
        if (value == null || value.isEmpty()) return false;
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT 1 FROM " + whitelistTable + " WHERE type=? AND value=?")) {
            st.setString(1, type);
            st.setString(2, value);
            try (ResultSet rs = st.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            warn("isWhitelisted", e);
            return false;
        }
    }

    public List<String[]> listWhitelist() {
        List<String[]> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT type,value,added_by,added_at FROM " + whitelistTable + " ORDER BY added_at DESC");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) list.add(new String[] {
                    rs.getString(1), rs.getString(2),
                    rs.getString(3), String.valueOf(rs.getLong(4)) });
        } catch (SQLException e) {
            warn("listWhitelist", e);
        }
        return list;
    }

    /* ─────────────────────────────────────────────
       STATS e PURGE
    ───────────────────────────────────────────── */

    public Stats getStats() {
        Stats s = new Stats();
        s.totalBans        = count("SELECT COUNT(*) FROM " + bansTable, -1);
        long now           = System.currentTimeMillis();
        s.activeBans       = count("SELECT COUNT(*) FROM " + bansTable + " WHERE expires_at=0 OR expires_at>?", now);
        s.bans24h          = count("SELECT COUNT(*) FROM " + bansTable + " WHERE banned_at>?", now - 86_400_000L);
        s.bans7d           = count("SELECT COUNT(*) FROM " + bansTable + " WHERE banned_at>?", now - 7L * 86_400_000L);
        s.totalFingerprints = count("SELECT COUNT(*) FROM " + fingerprintsTable, -1);
        s.uniquePlayers    = count("SELECT COUNT(DISTINCT player_uuid) FROM " + fingerprintsTable, -1);
        s.whitelistSize    = count("SELECT COUNT(*) FROM " + whitelistTable, -1);
        return s;
    }

    private long count(String sql, long param) {
        try (Connection c = getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            if (param >= 0) st.setLong(1, param);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    public int purgeExpiredBans() {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "DELETE FROM " + bansTable + " WHERE expires_at>0 AND expires_at<?")) {
            st.setLong(1, System.currentTimeMillis());
            return st.executeUpdate();
        } catch (SQLException e) {
            warn("purgeExpiredBans", e);
            return 0;
        }
    }

    /* ─────────────────────────────────────────────
       LIFECYCLE
    ───────────────────────────────────────────── */

    public void disconnect() {
        if (mysqlPool != null && !mysqlPool.isClosed()) mysqlPool.close();
    }

    public boolean reconnect() {
        disconnect();
        return connect();
    }

    /* ─────────────────────────────────────────────
       UTILITY
    ───────────────────────────────────────────── */

    private void warn(String ctx, SQLException e) {
        plugin.getLogger().warning("[DB:" + ctx + "] " + e.getMessage());
        if (plugin.getConfigManager().debug()) e.printStackTrace();
    }

    /* ─────────────────────────────────────────────
       INNER CLASSES
    ───────────────────────────────────────────── */

    public static class FingerprintRecord {
        public final String fingerprint;
        public final String raw;
        public final long firstSeen;
        public final long lastSeen;

        public FingerprintRecord(String fingerprint, String raw, long firstSeen, long lastSeen) {
            this.fingerprint = fingerprint;
            this.raw = raw;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    public static class Stats {
        public long totalBans;
        public long activeBans;
        public long bans24h;
        public long bans7d;
        public long totalFingerprints;
        public long uniquePlayers;
        public long whitelistSize;
    }
}
