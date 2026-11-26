package com.xuvigan.aurumauction.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import com.xuvigan.aurumauction.AurumAuction;

/** Typed access to config.yml. */
public final class ConfigManager {

    private final AurumAuction plugin;

    private String locale = "ru";
    private String storageType = "YAML";
    private int autosaveSeconds = 60;
    private String sqliteFile = "lots.db";
    private boolean migrateYaml = true;
    private String mysqlHost = "127.0.0.1";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "minecraft";
    private String mysqlUser = "root";
    private String mysqlPassword = "";
    private String tablePrefix = "ah_";
    private String economyMode = "VAULT";
    private String currencyFormat = "#,##0.##";
    private long expireHours = 48L;
    private double minPrice = 1.0d;
    private double maxPrice = 1000000000d;
    private int defaultLimit = 5;
    private int maxAmountPerLot = 64;
    private boolean allowShulkers = true;
    private double taxPercent = 5.0d;
    private Set<String> blockedMaterials = Collections.emptySet();
    private int searchMinLength = 2;
    private int expireCheckSeconds = 60;
    private boolean buyConfirm = true;
    private int retentionDays = 30;
    private int searchInputTimeoutSeconds = 30;
    private int clickCooldownMs = 300;
    private int commandCooldownMs = 500;
    private int payoutRetrySeconds = 300;
    private int retentionCheckSeconds = 3600;
    private boolean bidsEnabled = true;
    private double bidMinIncrement = 1.0d;
    private double bidMinIncrementPercent = 5.0d;
    private int bidExtendSeconds = 60;
    private int bidDefaultHours = 24;
    private int bidMaxHours = 168;
    private boolean autoBuyEnabled = true;
    private int autoBuyAfterHours = 168;
    private double autoBuyPercent = 70.0d;
    private int autoBuyCheckSeconds = 3600;
    private boolean autoClaim = true;
    private boolean notificationsEnabled = true;
    private int historySize = 200;
    private int historyTopSize = 10;
    private int watchMax = 5;
    private boolean multiServerEnabled = false;
    private int serverId = 1;
    private int syncSeconds = 5;
    private int bedrockLoreLines = 8;
    private int maxTotalLots = 0;
    private final java.util.Map<String, Double> taxGroups = new java.util.LinkedHashMap<String, Double>();
    private final java.util.Map<String, Integer> limitGroups = new java.util.LinkedHashMap<String, Integer>();
    private boolean transactionLog = true;

    public ConfigManager(AurumAuction plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        locale = cfg.getString("locale", "ru");
        storageType = cfg.getString("storage.type", "YAML");
        autosaveSeconds = Math.max(10, cfg.getInt("storage.autosave-seconds", 60));
        sqliteFile = cfg.getString("storage.sqlite.file", "lots.db");
        migrateYaml = cfg.getBoolean("storage.migrate-yaml", true);
        mysqlHost = cfg.getString("storage.mysql.host", "127.0.0.1");
        mysqlPort = Math.max(1, cfg.getInt("storage.mysql.port", 3306));
        mysqlDatabase = cfg.getString("storage.mysql.database", "minecraft");
        mysqlUser = cfg.getString("storage.mysql.user", "root");
        mysqlPassword = cfg.getString("storage.mysql.password", "");
        tablePrefix = cfg.getString("storage.mysql.table-prefix", "ah_");
        economyMode = cfg.getString("economy.mode", "VAULT");
        currencyFormat = cfg.getString("economy.currency-format", "#,##0.##");
        expireHours = Math.max(1L, cfg.getLong("lots.expire-hours", 48L));
        minPrice = Math.max(0d, cfg.getDouble("lots.min-price", 1.0d));
        maxPrice = Math.max(minPrice, cfg.getDouble("lots.max-price", 1000000000d));
        defaultLimit = Math.max(1, cfg.getInt("lots.default-limit", 5));
        maxAmountPerLot = Math.max(1, Math.min(2304, cfg.getInt("lots.max-amount-per-lot", 64)));
        allowShulkers = cfg.getBoolean("lots.allow-shulkers", true);
        taxPercent = Math.max(0d, Math.min(100d, cfg.getDouble("lots.tax-percent", 5.0d)));
        List<String> blocked = cfg.getStringList("lots.blocked-materials");
        Set<String> names = new HashSet<String>();
        for (String raw : blocked) {
            if (raw != null && !raw.trim().isEmpty()) {
                names.add(raw.trim().toUpperCase(Locale.ROOT));
            }
        }
        blockedMaterials = Collections.unmodifiableSet(names);
        searchMinLength = Math.max(1, cfg.getInt("search.min-length", 2));
        expireCheckSeconds = Math.max(10, cfg.getInt("expire-check-seconds", 60));
        buyConfirm = cfg.getBoolean("lots.buy-confirm", true);
        retentionDays = Math.max(0, cfg.getInt("lots.retention-days", 30));
        searchInputTimeoutSeconds = Math.max(10, cfg.getInt("search.input-timeout-seconds", 30));
        clickCooldownMs = Math.max(0, cfg.getInt("limits.click-cooldown-ms", 300));
        commandCooldownMs = Math.max(0, cfg.getInt("limits.command-cooldown-ms", 500));
        payoutRetrySeconds = Math.max(60, cfg.getInt("payouts.retry-seconds", 300));
        retentionCheckSeconds = Math.max(600, cfg.getInt("retention-check-seconds", 3600));
        bidsEnabled = cfg.getBoolean("bids.enabled", true);
        bidMinIncrement = Math.max(0d, cfg.getDouble("bids.min-increment", 1.0d));
        bidMinIncrementPercent = Math.max(0d, cfg.getDouble("bids.min-increment-percent", 5.0d));
        bidExtendSeconds = Math.max(0, cfg.getInt("bids.extend-seconds", 60));
        bidDefaultHours = Math.max(1, cfg.getInt("bids.default-hours", 24));
        bidMaxHours = Math.max(bidDefaultHours, cfg.getInt("bids.max-hours", 168));
        autoBuyEnabled = cfg.getBoolean("auto-buy.enabled", true);
        autoBuyAfterHours = Math.max(1, cfg.getInt("auto-buy.after-hours", 168));
        autoBuyPercent = Math.max(0d, Math.min(100d, cfg.getDouble("auto-buy.percent", 70.0d)));
        autoBuyCheckSeconds = Math.max(600, cfg.getInt("auto-buy.check-seconds", 3600));
        autoClaim = cfg.getBoolean("claims.auto-claim", true);
        notificationsEnabled = cfg.getBoolean("notifications.enabled", true);
        historySize = Math.max(10, cfg.getInt("history.size", 200));
        historyTopSize = Math.max(1, cfg.getInt("history.top-size", 10));
        watchMax = Math.max(1, cfg.getInt("watch.max", 5));
        multiServerEnabled = cfg.getBoolean("multi-server.enabled", false);
        serverId = Math.max(0, Math.min(1023, cfg.getInt("multi-server.server-id", 1)));
        syncSeconds = Math.max(2, cfg.getInt("multi-server.sync-seconds", 5));
        bedrockLoreLines = Math.max(0, cfg.getInt("bedrock.max-lore-lines", 8));
        maxTotalLots = Math.max(0, cfg.getInt("lots.max-total", 0));
        transactionLog = cfg.getBoolean("logging.transactions", true);
        taxGroups.clear();
        org.bukkit.configuration.ConfigurationSection taxSection =
                cfg.getConfigurationSection("lots.tax-groups");
        if (taxSection != null) {
            for (String key : taxSection.getKeys(false)) {
                taxGroups.put(key, Double.valueOf(taxSection.getDouble(key)));
            }
        }
        limitGroups.clear();
        org.bukkit.configuration.ConfigurationSection limitSection =
                cfg.getConfigurationSection("limits.groups");
        if (limitSection != null) {
            for (String key : limitSection.getKeys(false)) {
                limitGroups.put(key, Integer.valueOf(limitSection.getInt(key)));
            }
        }
    }

    public String locale() {
        return locale;
    }

    public String storageType() {
        return storageType;
    }

    public int autosaveSeconds() {
        return autosaveSeconds;
    }

    public String sqliteFile() {
        return sqliteFile;
    }

    public boolean migrateYaml() {
        return migrateYaml;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUser() {
        return mysqlUser;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String economyMode() {
        return economyMode;
    }

    public String currencyFormat() {
        return currencyFormat;
    }

    public long expireHours() {
        return expireHours;
    }

    public double minPrice() {
        return minPrice;
    }

    public double maxPrice() {
        return maxPrice;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public int maxAmountPerLot() {
        return maxAmountPerLot;
    }

    public boolean allowShulkers() {
        return allowShulkers;
    }

    public double taxPercent() {
        return taxPercent;
    }

    public int searchMinLength() {
        return searchMinLength;
    }

    public int expireCheckSeconds() {
        return expireCheckSeconds;
    }

    public boolean buyConfirm() {
        return buyConfirm;
    }

    public int retentionDays() {
        return retentionDays;
    }

    public int searchInputTimeoutSeconds() {
        return searchInputTimeoutSeconds;
    }

    public int clickCooldownMs() {
        return clickCooldownMs;
    }

    public int commandCooldownMs() {
        return commandCooldownMs;
    }

    public int payoutRetrySeconds() {
        return payoutRetrySeconds;
    }

    public int retentionCheckSeconds() {
        return retentionCheckSeconds;
    }

    public boolean bidsEnabled() {
        return bidsEnabled;
    }

    public double bidMinIncrement() {
        return bidMinIncrement;
    }

    public double bidMinIncrementPercent() {
        return bidMinIncrementPercent;
    }

    public int bidExtendSeconds() {
        return bidExtendSeconds;
    }

    public int bidDefaultHours() {
        return bidDefaultHours;
    }

    public int bidMaxHours() {
        return bidMaxHours;
    }

    public boolean autoBuyEnabled() {
        return autoBuyEnabled;
    }

    public int autoBuyAfterHours() {
        return autoBuyAfterHours;
    }

    public double autoBuyPercent() {
        return autoBuyPercent;
    }

    public int autoBuyCheckSeconds() {
        return autoBuyCheckSeconds;
    }

    public boolean autoClaim() {
        return autoClaim;
    }

    public boolean notificationsEnabled() {
        return notificationsEnabled;
    }

    public int historySize() {
        return historySize;
    }

    public int historyTopSize() {
        return historyTopSize;
    }

    public int watchMax() {
        return watchMax;
    }

    public boolean multiServerEnabled() {
        return multiServerEnabled;
    }

    public int serverId() {
        return serverId;
    }

    public int syncSeconds() {
        return syncSeconds;
    }

    public int bedrockLoreLines() {
        return bedrockLoreLines;
    }

    public int maxTotalLots() {
        return maxTotalLots;
    }

    public boolean transactionLog() {
        return transactionLog;
    }

    /** Lowest configured group tax the player qualifies for, or the default. */
    public double taxFor(org.bukkit.entity.Player player) {
        if (player != null && player.hasPermission("auction.tax.bypass")) {
            return 0d;
        }
        double chosen = taxPercent;
        boolean found = false;
        for (java.util.Map.Entry<String, Double> entry : taxGroups.entrySet()) {
            if (player != null && player.hasPermission("auction.group." + entry.getKey())
                    && (!found || entry.getValue().doubleValue() < chosen)) {
                chosen = entry.getValue().doubleValue();
                found = true;
            }
        }
        return Math.max(0d, Math.min(100d, chosen));
    }

    /** Highest of default, auction.limit.&lt;n&gt; and group limits. */
    public int limitFor(org.bukkit.entity.Player player) {
        int best = defaultLimit;
        if (player != null) {
            for (int i = 1; i <= 100; i++) {
                if (i > best && player.hasPermission("auction.limit." + i)) {
                    best = i;
                }
            }
            for (java.util.Map.Entry<String, Integer> entry : limitGroups.entrySet()) {
                if (player.hasPermission("auction.group." + entry.getKey())
                        && entry.getValue().intValue() > best) {
                    best = entry.getValue().intValue();
                }
            }
        }
        return best;
    }

    public boolean isBlocked(Material material) {
        return material != null && blockedMaterials.contains(material.name());
    }
}
