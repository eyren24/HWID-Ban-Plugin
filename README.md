# HWIDBan ─ Core Plugin

Sistema di ban server-side per Minecraft basato su **fingerprint multi-fattore SHA-256**.
Bannare il fingerprint significa colpire qualcosa che il player **non può cambiare facilmente**,
senza richiedere alcuna mod lato client.

| | |
|---|---|
| **Versioni supportate** | Paper / Spigot 1.20 → latest |
| **Java** | 17+ |
| **Storage** | SQLite (default) · MySQL |
| **Sync** | Redis PubSub (opzionale) |
| **Colori** | IridiumColorAPI (gradient, hex, rainbow) |
| **Notifiche** | Discord Webhook (opzionale) |

---

## Installazione

```bash
mvn clean package          # genera target/HWID-BAN-*.jar
cp target/*.jar plugins/   # copia sul server
# Avvia il server → config.yml generato in plugins/HWIDBan/
```

---

## Comandi

| Comando | Alias | Permesso | Descrizione |
|---|---|---|---|
| `/hwidban <player> [reason]` | `hban` · `fpban` | `hwidban.ban` | Ban permanente per fingerprint. |
| `/hwidtempban <player> <durata> [reason]` | `htempban` | `hwidban.tempban` | Ban temporaneo (`1h`, `2d12h`, `30d`, `perm`). |
| `/hwidunban <player\|hash>` | `hunban` · `fpunban` | `hwidban.unban` | Rimuove ban (per nome o hash SHA-256 completo). |
| `/hwidcheck <player>` | `hcheck` · `fpcheck` | `hwidban.check` | Mostra il breakdown completo del fingerprint. |
| `/hwidlist [limit]` | `hlist` · `fplist` | `hwidban.list` | Ultimi N ban (default 10, max 50). |
| `/hwidalt <player>` | `halt` · `fpalt` | `hwidban.alt` | Trova account alternativi con stesso fingerprint. |
| `/hwidhistory <player> [limit]` | `hhistory` · `fphist` | `hwidban.history` | Cronologia fingerprint salvati per un account. |
| `/hwidinfo <player>` | `hinfo` · `fpinfo` | `hwidban.info` | Stato completo: ban, UUID, ultimo join, FP count. |
| `/hwidstats` | `hstats` · `fpstats` | `hwidban.stats` | Statistiche globali del database. |
| `/hwidpurge fingerprints <giorni>` | `hpurge` | `hwidban.purge` | Elimina fingerprint più vecchi di N giorni. |
| `/hwidpurge expired` | `hpurge` | `hwidban.purge` | Elimina dal DB i ban già scaduti. |
| `/hwidwhitelist add <tipo> <valore>` | `hwl` · `fpwl` | `hwidban.whitelist` | Aggiunge alla whitelist. |
| `/hwidwhitelist remove <tipo> <valore>` | `hwl` | `hwidban.whitelist` | Rimuove dalla whitelist. |
| `/hwidwhitelist list` | `hwl` | `hwidban.whitelist` | Elenca tutte le voci whitelist. |
| `/hwidreload` | `hreload` | `hwidban.reload` | Ricarica config senza riavvio. |

> Tutti i comandi hanno **tab completion** sugli argomenti player.

### Formato durata (tempban)
| Input | Significato |
|---|---|
| `30s` | 30 secondi |
| `10m` | 10 minuti |
| `2h` | 2 ore |
| `1d` | 1 giorno |
| `2d12h30m` | 2 giorni, 12 ore, 30 minuti |
| `7d` | 1 settimana |
| `30d` | 30 giorni |
| `perm` / `0` | Permanente |

### Tipi whitelist
`IP` · `SUBNET` · `ISP` · `ASN` · `COUNTRY`

```bash
/hwidwhitelist add SUBNET 93.44.128.0/20
/hwidwhitelist add ISP "Telecom Italia S.p.A."
/hwidwhitelist add COUNTRY IT
```

---

## Permessi

| Permesso | Default | Effetto |
|---|---|---|
| `hwidban.admin` | `op` | **Wildcard**: assegna automaticamente tutti i permessi sotto. |
| `hwidban.ban` | `false` | `/hwidban` |
| `hwidban.tempban` | `false` | `/hwidtempban` |
| `hwidban.unban` | `false` | `/hwidunban` |
| `hwidban.check` | `false` | `/hwidcheck` |
| `hwidban.list` | `false` | `/hwidlist` |
| `hwidban.alt` | `false` | `/hwidalt` |
| `hwidban.history` | `false` | `/hwidhistory` |
| `hwidban.info` | `false` | `/hwidinfo` |
| `hwidban.stats` | `false` | `/hwidstats` |
| `hwidban.purge` | `false` | `/hwidpurge` |
| `hwidban.whitelist` | `false` | `/hwidwhitelist` |
| `hwidban.reload` | `false` | `/hwidreload` |
| `hwidban.notify` | `false` | Riceve broadcast di ban/unban. |
| `hwidban.bypass` | `false` | ⚠ Salta tutti i controlli. Mai dare a utenti normali. |

### Esempio LuckPerms
```bash
# Owner
/lp group owner permission set hwidban.admin true

# Moderatore
/lp group moderator permission set hwidban.ban true
/lp group moderator permission set hwidban.tempban true
/lp group moderator permission set hwidban.unban true
/lp group moderator permission set hwidban.check true
/lp group moderator permission set hwidban.list true
/lp group moderator permission set hwidban.alt true
/lp group moderator permission set hwidban.info true
/lp group moderator permission set hwidban.notify true

# Helper (solo lettura)
/lp group helper permission set hwidban.check true
/lp group helper permission set hwidban.info true
/lp group helper permission set hwidban.notify true
```

---

## Come funziona il fingerprint

```
SHA-256(
  subnet    : "93.44.128.0/20"           ← IP anonimizzato in /20
  isp       : "Telecom Italia S.p.A."
  asn       : "AS3269"
  country   : "Italy"
  city      : "Rome"
  brand     : "vanilla"                  ← client brand (parzialmente falsif.)
  version   : "765"                      ← protocol version
  ping      : "23"                       ← bucket arrotondato /10
  timing    : "12|11|13|12|11"           ← packet timing buckets
  screen    : "1920x1080"               ← via plugin channel (opt.)
  os        : "windows"                  ← euristica
  lang      : "it_IT"
) = 3f2a7c1e9b6d8f57e2c9a4b1...
```

Ogni fonte è **attivabile/disattivabile** singolarmente in `fingerprint.sources`.

---

## Flusso di login

```
Client ──► AsyncPlayerPreLoginEvent (thread async, HIGHEST)
              │
              ├─ Whitelist IP/Subnet/ISP/ASN/Country? → PERMESSO
              ├─ Rate limit IP superato?              → KICK_OTHER
              ├─ UUID ha fingerprint bannati nello storico? → KICK_BANNED
              └─ Subnet presente in fingerprint bannati?    → KICK_BANNED
                         │
                         ▼ (allowed)
            PlayerJoinEvent + delay 60 tick (async)
              │
              ├─ Genera fingerprint completo (con brand, timing, screen)
              ├─ Salva in DB (storico UUID → fingerprint)
              ├─ Auto-alt detection (se abilitato)
              ├─ Watch mode: solo log, no kick
              └─ Ban attivo? → kickPlayer() (fallback per new fp)
```

---

## Configurazione rapida

### Database MySQL
```yaml
database:
  type: "MYSQL"
  mysql:
    host: "localhost"
    port: 3306
    database: "hwidban"
    username: "root"
    password: "password"
    pool-size: 10
```

### Discord Webhook
```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/..."
  username: "HWIDBan"
```

### Redis (multi-server)
```yaml
sync:
  redis:
    enabled: true
    host: "localhost"
    port: 6379
    password: "secret"
    channel: "hwidban-sync"
```

### Watch Mode (test prima del deploy)
```yaml
security:
  watch-mode: true   # logga senza kickare
```

### Auto Alt-Ban
```yaml
security:
  auto-alt:
    enabled: true
    threshold: 4      # ban automatico se stesso FP su 4+ account
    duration: "30d"
    reason: "Abuso multi-account (%count% account)"
```

---

## Architettura

```
com.eyren.hWIDBan
├── Main.java
├── config/ConfigManager.java
├── util/
│   ├── Colors.java          ← wrapper IridiumColorAPI
│   ├── MessageUtil.java     ← rendering messaggi + placeholder
│   ├── VersionUtil.java     ← rilevamento versione server
│   ├── DurationParser.java  ← "1d2h30m" ↔ millisecondi
│   └── DiscordWebhook.java  ← HTTP POST embed Discord
├── fingerprint/
│   ├── FingerprintData.java
│   └── FingerprintGenerator.java   ← SHA-256 multi-source
├── collector/               ← una classe atomica per tipo di dato
│   ├── IPCollector.java
│   ├── ClientCollector.java
│   ├── GeoIPCollector.java
│   ├── NetworkCollector.java
│   ├── ScreenCollector.java
│   └── BehaviorCollector.java
├── database/
│   ├── DatabaseManager.java ← HikariCP, MySQL/SQLite, migration schema
│   └── BanEntry.java        ← con expiresAt (0 = permanente)
├── ban/
│   ├── BanManager.java      ← ban/tempban/unban/kick/broadcast/audit/auto-alt
│   └── WhitelistManager.java
├── sync/RedisSync.java
├── listener/
│   ├── PreLoginListener.java    ← AsyncPlayerPreLoginEvent (gate principale)
│   ├── ConnectionListener.java  ← PlayerJoinEvent (fingerprint completo)
│   ├── BehaviorListener.java
│   └── PluginChannelListener.java
└── commands/
    ├── HwidBanCommand.java
    ├── HwidTempBanCommand.java
    ├── HwidUnbanCommand.java
    ├── HwidCheckCommand.java
    ├── HwidListCommand.java
    ├── HwidAltCommand.java
    ├── HwidHistoryCommand.java
    ├── HwidInfoCommand.java
    ├── HwidStatsCommand.java
    ├── HwidPurgeCommand.java
    ├── HwidWhitelistCommand.java
    └── HwidReloadCommand.java
```

---

## Schema Database

```sql
-- Ban attivi (permanent e temporanei)
hwidban_bans (
  fingerprint  VARCHAR(64) PK,
  player_name  VARCHAR(32),
  player_uuid  VARCHAR(36),
  reason       TEXT,
  banned_by    VARCHAR(32),
  banned_at    BIGINT,
  expires_at   BIGINT DEFAULT 0   -- 0 = permanente
)

-- Storico fingerprint per UUID
hwidban_fingerprints (
  player_uuid  VARCHAR(36),
  fingerprint  VARCHAR(64),
  raw          TEXT,              -- stringa raw usata per SHA-256
  first_seen   BIGINT,
  last_seen    BIGINT,
  PRIMARY KEY (player_uuid, fingerprint)
)

-- Whitelist IP/Subnet/ISP/ASN/Country
hwidban_whitelist (
  type      VARCHAR(16),          -- IP | SUBNET | ISP | ASN | COUNTRY
  value     VARCHAR(128),
  added_by  VARCHAR(32),
  added_at  BIGINT,
  PRIMARY KEY (type, value)
)
```

**Migration automatica**: le colonne `expires_at` e `first_seen` vengono aggiunte
automaticamente se mancanti (upgrade da versioni precedenti del plugin).

---

## Bypass e mitigazioni

| Bypass | Difficoltà | Mitigazione |
|---|---|---|
| Cambio IP | Bassa | Subnet /20 + ISP/ASN restano stabili |
| VPN gratuita | Bassa | ISP blacklist via whitelist inversa; ASN hosting rilevato |
| VPN residenziale premium | Alta | Nessuna (limite del sistema server-side) |
| Cambio UUID (alt) | Bassa | Storico UUID → FP al preLogin + auto-alt detection |
| Client brand spoofato | Media | Brand è 1 fonte su 12; timing e ping pattern non falsificabili |
| Subnet injection nel config | Mitigata | Regex `^[A-Za-z0-9_]{1,64}$` su nomi tabella |
| Rate-brute login | Mitigata | Rate limit IP configurabile |

---

## Requisiti

1. **`online-mode: true`** sul server, oppure proxy con IP-forward corretto  
2. **Firewall**: blocca connessioni dirette al server se usi BungeeCord/Velocity  
3. **IP-forward**: senza di esso `player.getAddress()` ritorna sempre l'IP del proxy  

---

*Author: **Eyren** — 2026*
