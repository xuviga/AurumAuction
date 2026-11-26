package com.xuvigan.aurumauction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.xuvigan.aurumauction.command.AuctionCommand;
import com.xuvigan.aurumauction.command.AuctionTabCompleter;
import com.xuvigan.aurumauction.config.ConfigManager;
import com.xuvigan.aurumauction.config.GuiConfig;
import com.xuvigan.aurumauction.config.Messages;
import com.xuvigan.aurumauction.data.ClaimQueue;
import com.xuvigan.aurumauction.data.HistoryStore;
import com.xuvigan.aurumauction.data.LotRepository;
import com.xuvigan.aurumauction.data.NotificationQueue;
import com.xuvigan.aurumauction.data.PayoutQueue;
import com.xuvigan.aurumauction.data.TransactionLog;
import com.xuvigan.aurumauction.data.WatchStore;
import com.xuvigan.aurumauction.data.impl.SqlLotStorage;
import com.xuvigan.aurumauction.data.impl.YamlHistoryStore;
import com.xuvigan.aurumauction.data.impl.YamlLotStorage;
import com.xuvigan.aurumauction.economy.EconomyService;
import com.xuvigan.aurumauction.gui.ChatInput;
import com.xuvigan.aurumauction.gui.GuiListener;
import com.xuvigan.aurumauction.gui.GuiManager;
import com.xuvigan.aurumauction.listener.PlayerJoinListener;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.placeholder.AuctionExpansion;
import com.xuvigan.aurumauction.service.AuctionService;
import com.xuvigan.aurumauction.service.Notifier;
import com.xuvigan.aurumauction.util.BukkitSchedulerAdapter;
import com.xuvigan.aurumauction.util.Cooldowns;
import com.xuvigan.aurumauction.util.FoliaSchedulerAdapter;
import com.xuvigan.aurumauction.util.LotIdGenerator;
import com.xuvigan.aurumauction.util.Scheduler;

/** AurumAuction entry point. */
public final class AurumAuction extends JavaPlugin {

    private static AurumAuction instance;

    private ConfigManager config;
    private Messages messages;
    private GuiConfig guiConfig;
    private LotRepository lots;
    private PayoutQueue payouts;
    private ClaimQueue claims;
    private NotificationQueue notifications;
    private HistoryStore history;
    private WatchStore watches;
    private TransactionLog transactionLog;
    private EconomyService economy;
    private Notifier notifier;
    private AuctionService auction;
    private GuiManager gui;
    private ChatInput chatInput;
    private Cooldowns cooldowns;
    private Scheduler scheduler;

    private Object expansion;
    private Scheduler.TaskHandle expireTask;
    private Scheduler.TaskHandle autosaveTask;
    private Scheduler.TaskHandle purgeTask;
    private Scheduler.TaskHandle payoutTask;
    private Scheduler.TaskHandle autoBuyTask;
    private Scheduler.TaskHandle syncTask;
    private long lastSync;
    private final java.util.Set<Long> announcedLots = new java.util.HashSet<Long>();

    public static AurumAuction get() {
        return instance;
    }

    // ------------------------------------------------------------------- access

    public ConfigManager config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public LotRepository lots() {
        return lots;
    }

    public PayoutQueue payouts() {
        return payouts;
    }

    public ClaimQueue claims() {
        return claims;
    }

    public NotificationQueue notifications() {
        return notifications;
    }

    public HistoryStore history() {
        return history;
    }

    public WatchStore watches() {
        return watches;
    }

    public Notifier notifier() {
        return notifier;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public EconomyService economy() {
        return economy;
    }

    public AuctionService auction() {
        return auction;
    }

    public GuiManager gui() {
        return gui;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onEnable() {
        instance = this;
        long started = System.currentTimeMillis();
        getLogger().info("Starting AurumAuction " + getDescription().getVersion() + " by XuViGaN");

        saveDefaultConfig();
        this.config = new ConfigManager(this);
        loadMessages();
        this.guiConfig = new GuiConfig(this);
        this.scheduler = createScheduler();
        getLogger().info("Scheduler: " + (scheduler.isFolia() ? "FOLIA (regionized)" : "BUKKIT"));

        if (!"YAML".equalsIgnoreCase(config.storageType())) {
            getLogger().info("Storage type: " + config.storageType());
        }
        this.lots = createStorage();
        this.payouts = new PayoutQueue(this, new File(getDataFolder(), "payouts.yml"));
        this.claims = new ClaimQueue(this, new File(getDataFolder(), "claims.yml"));
        this.notifications = new NotificationQueue(this, new File(getDataFolder(), "notifications.yml"));
        this.history = new YamlHistoryStore(this, new File(getDataFolder(), "history.yml"), config.historySize());
        this.watches = new WatchStore(this, new File(getDataFolder(), "watchlist.yml"), config.watchMax());
        this.transactionLog = new TransactionLog(this, new File(getDataFolder(), "transactions.log"),
                config.transactionLog());
        this.economy = new EconomyService(this, config.economyMode(), config.currencyFormat());
        this.notifier = new Notifier(this);
        this.auction = new AuctionService(this, lots, economy, payouts, claims, notifier, history, watches,
                transactionLog, config);
        this.gui = new GuiManager(this);
        this.cooldowns = new Cooldowns();
        this.chatInput = new ChatInput(this);
        this.chatInput.start();
        payouts.retry(economy);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        registerCommands();
        startTasks();
        registerExpansion();

        getLogger().info("AurumAuction enabled in " + (System.currentTimeMillis() - started)
                + " ms (lots: " + lots.count(null) + ", economy: "
                + (economy.available() ? "VAULT" : "UNAVAILABLE") + ")");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (chatInput != null) {
            chatInput.stop();
        }
        if (lots != null) {
            lots.close();
        }
        if (payouts != null) {
            payouts.flush();
        }
        if (claims != null) {
            claims.flush();
        }
        if (notifications != null) {
            notifications.flush();
        }
        if (history != null) {
            history.close();
        }
        if (watches != null) {
            watches.flush();
        }
        if (expansion != null) {
            try {
                expansion.getClass().getMethod("unregister").invoke(expansion);
            } catch (Throwable ignored) {
                // nothing to unregister
            }
            expansion = null;
        }
        instance = null;
        getLogger().info("AurumAuction disabled");
    }

    // ------------------------------------------------------------------- reload

    /** Reloads config, messages, gui and the economy bridge (lots stay loaded). */
    public void reloadAll() {
        config.reload();
        loadMessages();
        guiConfig.reload();
        economy.configure(config.economyMode(), config.currencyFormat());
        economy.setup();
        payouts.retry(economy);
        if (chatInput != null) {
            chatInput.start();
        }
        startTasks();
    }

    private void loadMessages() {
        File folder = getDataFolder();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            getLogger().warning("Could not create " + folder.getAbsolutePath());
        }
        String locale = config.locale() == null ? "ru" : config.locale().trim().toLowerCase(java.util.Locale.ROOT);
        String resource = "messages_" + locale + ".yml";
        if (getResource(resource) == null) {
            resource = "messages_ru.yml";
        }
        File file = new File(folder, "messages.yml");
        if (!file.isFile()) {
            try {
                InputStream stream = getResource(resource);
                if (stream != null) {
                    Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    stream.close();
                }
            } catch (IOException ex) {
                getLogger().warning("Could not write messages.yml: " + ex.getMessage());
            }
        }
        YamlConfiguration cfg = file.isFile()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        try {
            InputStream stream = getResource(resource);
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                cfg.setDefaults(defaults);
                stream.close();
            }
        } catch (Throwable throwable) {
            getLogger().warning("Could not read bundled " + resource + ": " + throwable.getMessage());
        }
        if (messages == null) {
            this.messages = new Messages(cfg);
        } else {
            this.messages.reload(cfg);
        }
    }

    // ------------------------------------------------------------------ storage

    private Scheduler createScheduler() {
        String name = Bukkit.getName() == null ? "" : Bukkit.getName().toLowerCase(java.util.Locale.ROOT);
        String version = Bukkit.getVersion() == null ? "" : Bukkit.getVersion().toLowerCase(java.util.Locale.ROOT);
        boolean folia = name.contains("folia") || version.contains("folia")
                || Bukkit.getServer().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("folia");
        if (folia) {
            try {
                return new FoliaSchedulerAdapter(this);
            } catch (Throwable throwable) {
                getLogger().severe("Folia detected but scheduler adapter failed: " + throwable.getMessage());
            }
        }
        return new BukkitSchedulerAdapter(this);
    }

    private LotRepository createStorage() {
        String type = config.storageType() == null ? "YAML" : config.storageType().trim().toUpperCase(java.util.Locale.ROOT);
        LotIdGenerator ids = config.multiServerEnabled() ? new LotIdGenerator(config.serverId()) : null;
        if (config.multiServerEnabled() && !"MYSQL".equals(type)) {
            getLogger().warning("multi-server requires storage.type MYSQL; shared tables will not sync on "
                    + type + " - continuing with local storage");
        }
        if ("SQLITE".equals(type)) {
            try {
                File db = new File(getDataFolder(), config.sqliteFile());
                File parent = db.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    getLogger().warning("Could not create " + parent.getAbsolutePath());
                }
                SqlLotStorage sql = new SqlLotStorage(this, "jdbc:sqlite:" + db.getAbsolutePath(),
                        null, null, config.tablePrefix(), null, ids);
                migrateYaml(sql);
                getLogger().info("Storage: SQLITE (" + db.getName() + ")");
                return sql;
            } catch (Throwable throwable) {
                getLogger().severe("SQLite unavailable (" + throwable.getMessage() + ") - falling back to YAML");
            }
        }
        if ("MYSQL".equals(type)) {
            try {
                String url = "jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/"
                        + config.mysqlDatabase()
                        + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC";
                SqlLotStorage sql = new SqlLotStorage(this, url, config.mysqlUser(), config.mysqlPassword(),
                        config.tablePrefix(), null, ids);
                migrateYaml(sql);
                getLogger().info("Storage: MYSQL (" + config.mysqlHost() + "/" + config.mysqlDatabase()
                        + ", server-id " + config.serverId() + ")");
                return sql;
            } catch (Throwable throwable) {
                getLogger().severe("MySQL unavailable (" + throwable.getMessage() + ") - falling back to YAML");
            }
        }
        return new YamlLotStorage(this, new File(getDataFolder(), "lots.yml"), ids);
    }

    /** Imports lots.yml into an empty SQL table once, then renames the file. */
    private void migrateYaml(SqlLotStorage sql) {
        if (!config.migrateYaml()) {
            return;
        }
        File legacy = new File(getDataFolder(), "lots.yml");
        if (!legacy.isFile() || sql.count(null) > 0) {
            return;
        }
        YamlLotStorage yaml = new YamlLotStorage(this, legacy);
        int imported = 0;
        for (AuctionLot lot : yaml.all()) {
            sql.save(lot);
            imported++;
        }
        sql.flush();
        try {
            Files.move(legacy.toPath(), new File(getDataFolder(), "lots.yml.migrated").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            getLogger().warning("Could not rename lots.yml after migration: " + ex.getMessage());
        }
        getLogger().info("Migrated " + imported + " lot(s) from YAML to SQL");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("ah");
        if (command == null) {
            getLogger().warning("Command 'ah' is missing from plugin.yml");
            return;
        }
        command.setExecutor(new AuctionCommand(this));
        command.setTabCompleter(new AuctionTabCompleter());
        command.setDescription(getDescription().getDescription());
    }

    private void registerExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            Object value = new AuctionExpansion(this);
            value.getClass().getMethod("register").invoke(value);
            this.expansion = value;
            getLogger().info("PlaceholderAPI expansion registered");
        } catch (Throwable throwable) {
            getLogger().warning("PlaceholderAPI expansion skipped: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    // -------------------------------------------------------------------- tasks

    private void startTasks() {
        stopTasks();
        long expireTicks = 20L * Math.max(10, config.expireCheckSeconds());
        this.expireTask = scheduler.runTimer( new Runnable() {
            @Override
            public void run() {
                try {
                    int expired = auction.expireDue();
                    if (expired > 0) {
                        getLogger().info("Moved " + expired + " lot(s) to expired");
                    }
                } catch (Throwable throwable) {
                    getLogger().warning("Expiry task failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }, expireTicks, expireTicks);

        long saveTicks = 20L * Math.max(10, config.autosaveSeconds());
        this.autosaveTask = scheduler.runTimer( new Runnable() {
            @Override
            public void run() {
                try {
                    lots.flush();
                } catch (Throwable throwable) {
                    getLogger().warning("Autosave failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }, saveTicks, saveTicks);

        long purgeTicks = 20L * Math.max(600, config.retentionCheckSeconds());
        this.purgeTask = scheduler.runTimer( new Runnable() {
            @Override
            public void run() {
                try {
                    int days = config.retentionDays();
                    if (days <= 0) {
                        return;
                    }
                    int removed = lots.purgeClosed(System.currentTimeMillis() - days * 86400_000L);
                    if (removed > 0) {
                        lots.flush();
                        getLogger().info("Purged " + removed + " closed lot(s) older than " + days + " day(s)");
                    }
                } catch (Throwable throwable) {
                    getLogger().warning("Retention task failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }, purgeTicks, purgeTicks);

        long payoutTicks = 20L * Math.max(60, config.payoutRetrySeconds());
        this.payoutTask = scheduler.runTimer( new Runnable() {
            @Override
            public void run() {
                try {
                    payouts.retry(economy);
                } catch (Throwable throwable) {
                    getLogger().warning("Payout retry failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }, payoutTicks, payoutTicks);

        long autoBuyTicks = 20L * Math.max(600, config.autoBuyCheckSeconds());
        this.autoBuyTask = scheduler.runTimer( new Runnable() {
            @Override
            public void run() {
                try {
                    int bought = auction.autoBuyout();
                    if (bought > 0) {
                        getLogger().info("Auto-bought " + bought + " stale lot(s)");
                    }
                } catch (Throwable throwable) {
                    getLogger().warning("Auto-buyout failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }, autoBuyTicks, autoBuyTicks);

        if (config.multiServerEnabled()) {
            this.lastSync = System.currentTimeMillis();
            long syncTicks = 20L * Math.max(2, config.syncSeconds());
            this.syncTask = scheduler.runTimer(new Runnable() {
                @Override
                public void run() {
                    try {
                        long since = lastSync - 2000L;
                        java.util.List<AuctionLot> fresh = lots.createdSince(since, 200);
                        long max = lastSync;
                        for (AuctionLot lot : fresh) {
                            if (announcedLots.add(Long.valueOf(lot.id()))) {
                                auction.announceNewLot(lot);
                            }
                            if (lot.createdAt() > max) {
                                max = lot.createdAt();
                            }
                        }
                        lastSync = max;
                        if (announcedLots.size() > 5000) {
                            announcedLots.clear();
                        }
                    } catch (Throwable throwable) {
                        getLogger().warning("Cross-server sync failed: "
                                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                    }
                }
            }, syncTicks, syncTicks);
        }
    }

    private void stopTasks() {
        if (expireTask != null) {
            try {
                expireTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            expireTask = null;
        }
        if (autosaveTask != null) {
            try {
                autosaveTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            autosaveTask = null;
        }
        if (purgeTask != null) {
            try {
                purgeTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            purgeTask = null;
        }
        if (payoutTask != null) {
            try {
                payoutTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            payoutTask = null;
        }
        if (autoBuyTask != null) {
            try {
                autoBuyTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            autoBuyTask = null;
        }
        if (syncTask != null) {
            try {
                syncTask.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            syncTask = null;
        }
    }
}
