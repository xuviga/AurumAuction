package com.xuvigan.aurumauction.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.economy.EconomyService;

/**
 * Durable queue of seller payouts that Vault refused at sale time (offline
 * economy errors, provider hiccups). Retried on startup and on a schedule, so
 * a failed deposit never silently eats the seller's money.
 */
public final class PayoutQueue {

    public static final class Pending {

        private final UUID seller;
        private final String name;
        private final double amount;
        private final long lotId;
        private final long createdAt;

        Pending(UUID seller, String name, double amount, long lotId, long createdAt) {
            this.seller = seller;
            this.name = name;
            this.amount = amount;
            this.lotId = lotId;
            this.createdAt = createdAt;
        }

        public UUID seller() {
            return seller;
        }

        public String name() {
            return name;
        }

        public double amount() {
            return amount;
        }

        public long lotId() {
            return lotId;
        }

        public long createdAt() {
            return createdAt;
        }
    }

    private final AurumAuction plugin;
    private final File file;
    private final List<Pending> entries = new ArrayList<Pending>();

    public PayoutQueue(AurumAuction plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    public synchronized void add(UUID seller, String name, double amount, long lotId) {
        if (seller == null || amount <= 0d) {
            return;
        }
        entries.add(new Pending(seller, name, amount, lotId, System.currentTimeMillis()));
        flush();
        if (plugin != null) {
            plugin.getLogger().warning("Queued payout of " + amount + " for " + name
                    + " (lot #" + lotId + "), total pending: " + entries.size());
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized List<Pending> entries() {
        return Collections.unmodifiableList(new ArrayList<Pending>(entries));
    }

    /** Attempts every pending payout; returns how many were paid. */
    public synchronized int retry(EconomyService economy) {
        if (economy == null || !economy.available() || entries.isEmpty()) {
            return 0;
        }
        int paid = 0;
        List<Pending> done = new ArrayList<Pending>();
        for (Pending pending : entries) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(pending.seller());
            if (target == null) {
                continue;
            }
            if (economy.deposit(target, pending.amount())) {
                done.add(pending);
                paid++;
            }
        }
        if (!done.isEmpty()) {
            entries.removeAll(done);
            flush();
            if (plugin != null) {
                plugin.getLogger().info("Paid " + paid + " queued payout(s), "
                        + entries.size() + " still pending");
            }
        }
        return paid;
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = cfg.getMapList("payouts");
        for (Map<?, ?> map : raw) {
            try {
                UUID seller = UUID.fromString(String.valueOf(map.get("seller")));
                String name = String.valueOf(map.get("name"));
                double amount = toDouble(map.get("amount"));
                long lotId = toLong(map.get("lot-id"));
                long createdAt = toLong(map.get("created-at"));
                if (amount > 0d) {
                    entries.add(new Pending(seller, name, amount, lotId, createdAt));
                }
            } catch (Throwable throwable) {
                warn("skipped malformed payout entry: " + throwable.getClass().getSimpleName());
            }
        }
        if (!entries.isEmpty()) {
            warn("loaded " + entries.size() + " pending payout(s)");
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public synchronized void flush() {
        if (file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
        for (Pending pending : entries) {
            Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
            map.put("seller", pending.seller() == null ? "" : pending.seller().toString());
            map.put("name", pending.name());
            map.put("amount", Double.valueOf(pending.amount()));
            map.put("lot-id", Long.valueOf(pending.lotId()));
            map.put("created-at", Long.valueOf(pending.createdAt()));
            raw.add(map);
        }
        cfg.set("payouts", raw);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                warn("could not create " + parent.getAbsolutePath());
                return;
            }
            Path temp = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
            cfg.save(temp.toFile());
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            warn("could not save " + file.getName() + ": " + ex.getMessage());
        }
    }

    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }
}
