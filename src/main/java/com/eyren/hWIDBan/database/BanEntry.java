package com.eyren.hWIDBan.database;

public class BanEntry {

    private final String fingerprint;
    private final String playerName;
    private final String playerUuid;
    private final String reason;
    private final String bannedBy;
    private final long bannedAt;
    private final long expiresAt;

    public BanEntry(String fingerprint, String playerName, String playerUuid,
                    String reason, String bannedBy, long bannedAt, long expiresAt) {
        this.fingerprint = fingerprint;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.reason = reason;
        this.bannedBy = bannedBy;
        this.bannedAt = bannedAt;
        this.expiresAt = expiresAt;
    }

    public BanEntry(String fingerprint, String playerName, String playerUuid,
                    String reason, String bannedBy, long bannedAt) {
        this(fingerprint, playerName, playerUuid, reason, bannedBy, bannedAt, 0L);
    }

    public String fingerprint() { return fingerprint; }
    public String playerName() { return playerName; }
    public String playerUuid() { return playerUuid; }
    public String reason() { return reason; }
    public String bannedBy() { return bannedBy; }
    public long bannedAt() { return bannedAt; }
    public long expiresAt() { return expiresAt; }

    public boolean isPermanent() { return expiresAt <= 0L; }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }
}
