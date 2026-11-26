package com.xuvigan.aurumauction.data.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.data.LotRepository;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;
import com.xuvigan.aurumauction.util.Items;
import com.xuvigan.aurumauction.util.LotIdGenerator;

/**
 * YAML backed lot storage: the whole lot set lives in memory and is flushed to
 * lots.yml on a schedule and on shutdown. Writes go through a temp file so a
 * crash cannot leave a half-written database behind.
 */
public final class YamlLotStorage implements LotRepository {

    private final AurumAuction plugin;
    private final File file;
    private final LotIdGenerator idGenerator;
    private final Map<Long, AuctionLot> lots = new ConcurrentHashMap<Long, AuctionLot>();
    private final AtomicLong nextId = new AtomicLong(1L);
    private volatile boolean dirty;

    public YamlLotStorage(AurumAuction plugin, File file) {
        this(plugin, file, null);
    }

    public YamlLotStorage(AurumAuction plugin, File file, LotIdGenerator idGenerator) {
        this.plugin = plugin;
        this.file = file;
        this.idGenerator = idGenerator;
        load();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("lots");
        if (section == null) {
            return;
        }
        long maxId = 0L;
        for (String key : section.getKeys(false)) {
            ConfigurationSection lot = section.getConfigurationSection(key);
            if (lot == null) {
                continue;
            }
            try {
                long id = lot.getLong("id", Long.parseLong(key));
                ItemStack item = Items.decode(lot.getString("item"));
                if (item == null) {
                    warn("lot #" + id + " skipped: item data is unreadable");
                    continue;
                }
                UUID seller;
                try {
                    seller = UUID.fromString(lot.getString("seller", ""));
                } catch (IllegalArgumentException ex) {
                    warn("lot #" + id + " skipped: invalid seller uuid");
                    continue;
                }
                AuctionLot parsed = new AuctionLot(
                        id,
                        seller,
                        lot.getString("seller-name", "?"),
                        item,
                        lot.getDouble("price", 0d),
                        lot.getLong("created-at", System.currentTimeMillis()),
                        lot.getLong("expires-at", 0L),
                        LotStatus.parse(lot.getString("status")),
                        parseCategory(lot.getString("category")),
                        lot.getLong("closed-at", 0L),
                        com.xuvigan.aurumauction.model.LotType.parse(lot.getString("type")),
                        lot.getDouble("buyout-price", 0d),
                        parseUuid(lot.getString("bidder")),
                        lot.getString("bidder-name"),
                        lot.getInt("bid-count", 0));
                lots.put(Long.valueOf(id), parsed);
                maxId = Math.max(maxId, id);
            } catch (Throwable throwable) {
                warn("lot '" + key + "' skipped: " + throwable.getClass().getSimpleName());
            }
        }
        nextId.set(maxId + 1L);
    }

    private static LotCategory parseCategory(String value) {
        if (value == null) {
            return LotCategory.OTHER;
        }
        try {
            return LotCategory.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LotCategory.OTHER;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public long nextId() {
        return idGenerator != null ? idGenerator.next() : nextId.getAndIncrement();
    }

    @Override
    public AuctionLot find(long id) {
        return lots.get(Long.valueOf(id));
    }

    @Override
    public void save(AuctionLot lot) {
        if (lot == null) {
            return;
        }
        lots.put(Long.valueOf(lot.id()), lot);
        dirty = true;
    }

    @Override
    public void delete(long id) {
        if (lots.remove(Long.valueOf(id)) != null) {
            dirty = true;
        }
    }

    @Override
    public List<AuctionLot> search(LotQuery query, int page, int perPage) {
        List<AuctionLot> matched = new ArrayList<AuctionLot>();
        for (AuctionLot lot : lots.values()) {
            if (query == null || query.matches(lot)) {
                matched.add(lot);
            }
        }
        Collections.sort(matched, query == null ? LotQuery.active().comparator() : query.comparator());
        int safePerPage = Math.max(1, perPage);
        int from = Math.max(0, (Math.max(1, page) - 1) * safePerPage);
        if (from >= matched.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(matched.size(), from + safePerPage);
        return new ArrayList<AuctionLot>(matched.subList(from, to));
    }

    @Override
    public int count(LotQuery query) {
        int total = 0;
        for (AuctionLot lot : lots.values()) {
            if (query == null || query.matches(lot)) {
                total++;
            }
        }
        return total;
    }

    @Override
    public List<AuctionLot> bySeller(UUID seller, LotStatus status, int limit) {
        List<AuctionLot> matched = new ArrayList<AuctionLot>();
        if (seller != null) {
            for (AuctionLot lot : lots.values()) {
                if (seller.equals(lot.seller()) && (status == null || lot.status() == status)) {
                    matched.add(lot);
                }
            }
        }
        Collections.sort(matched, new Comparator<AuctionLot>() {
            @Override
            public int compare(AuctionLot a, AuctionLot b) {
                return Long.compare(b.createdAt(), a.createdAt());
            }
        });
        if (limit > 0 && matched.size() > limit) {
            return new ArrayList<AuctionLot>(matched.subList(0, limit));
        }
        return matched;
    }

    @Override
    public int countBySeller(UUID seller, LotStatus status) {
        int total = 0;
        if (seller == null) {
            return 0;
        }
        for (AuctionLot lot : lots.values()) {
            if (seller.equals(lot.seller()) && (status == null || lot.status() == status)) {
                total++;
            }
        }
        return total;
    }

    @Override
    public List<AuctionLot> due(long now) {
        List<AuctionLot> out = new ArrayList<AuctionLot>();
        for (AuctionLot lot : lots.values()) {
            if (lot.status() == LotStatus.ACTIVE && lot.isExpired(now)) {
                out.add(lot);
            }
        }
        return out;
    }

    @Override
    public int purgeClosed(long before) {
        int removed = 0;
        for (AuctionLot lot : new ArrayList<AuctionLot>(lots.values())) {
            boolean closed = lot.status() == LotStatus.SOLD || lot.status() == LotStatus.CANCELLED;
            if (closed && lot.closedAt() > 0L && lot.closedAt() <= before) {
                lots.remove(Long.valueOf(lot.id()));
                removed++;
            }
        }
        if (removed > 0) {
            dirty = true;
        }
        return removed;
    }

    @Override
    public List<AuctionLot> staleActive(long before, int limit) {
        List<AuctionLot> matched = new ArrayList<AuctionLot>();
        for (AuctionLot lot : lots.values()) {
            if (lot.status() == LotStatus.ACTIVE && !lot.isBid() && lot.createdAt() <= before) {
                matched.add(lot);
            }
        }
        Collections.sort(matched, new Comparator<AuctionLot>() {
            @Override
            public int compare(AuctionLot a, AuctionLot b) {
                return Long.compare(a.createdAt(), b.createdAt());
            }
        });
        if (limit > 0 && matched.size() > limit) {
            return new ArrayList<AuctionLot>(matched.subList(0, limit));
        }
        return matched;
    }

    @Override
    public List<AuctionLot> createdSince(long time, int limit) {
        List<AuctionLot> matched = new ArrayList<AuctionLot>();
        for (AuctionLot lot : lots.values()) {
            if (lot.createdAt() > time) {
                matched.add(lot);
            }
        }
        Collections.sort(matched, new Comparator<AuctionLot>() {
            @Override
            public int compare(AuctionLot a, AuctionLot b) {
                return Long.compare(a.createdAt(), b.createdAt());
            }
        });
        if (limit > 0 && matched.size() > limit) {
            return new ArrayList<AuctionLot>(matched.subList(0, limit));
        }
        return matched;
    }

    @Override
    public synchronized boolean transition(long id, LotStatus expected, LotStatus next) {
        AuctionLot current = lots.get(Long.valueOf(id));
        if (current == null || current.status() != expected) {
            return false;
        }
        lots.put(Long.valueOf(id), current.withStatus(next));
        dirty = true;
        return true;
    }

    @Override
    public synchronized boolean compareAndSet(AuctionLot expected, AuctionLot updated) {
        if (expected == null || updated == null) {
            return false;
        }
        AuctionLot current = lots.get(Long.valueOf(expected.id()));
        if (current == null
                || current.status() != expected.status()
                || Double.compare(current.price(), expected.price()) != 0
                || current.bidCount() != expected.bidCount()
                || !java.util.Objects.equals(current.bidder(), expected.bidder())) {
            return false;
        }
        lots.put(Long.valueOf(updated.id()), updated);
        dirty = true;
        return true;
    }

    @Override
    public Collection<AuctionLot> all() {
        return Collections.unmodifiableCollection(new ArrayList<AuctionLot>(lots.values()));
    }

    @Override
    public void flush() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (AuctionLot lot : new ArrayList<AuctionLot>(lots.values())) {
            String base = "lots." + lot.id() + ".";
            cfg.set(base + "id", Long.valueOf(lot.id()));
            cfg.set(base + "seller", lot.seller() == null ? "" : lot.seller().toString());
            cfg.set(base + "seller-name", lot.sellerName());
            cfg.set(base + "price", Double.valueOf(lot.price()));
            cfg.set(base + "created-at", Long.valueOf(lot.createdAt()));
            cfg.set(base + "expires-at", Long.valueOf(lot.expiresAt()));
            cfg.set(base + "status", lot.status().name());
            cfg.set(base + "category", lot.category().name());
            cfg.set(base + "closed-at", Long.valueOf(lot.closedAt()));
            cfg.set(base + "type", lot.type().name());
            cfg.set(base + "buyout-price", Double.valueOf(lot.buyoutPrice()));
            cfg.set(base + "bidder", lot.bidder() == null ? null : lot.bidder().toString());
            cfg.set(base + "bidder-name", lot.bidderName());
            cfg.set(base + "bid-count", Integer.valueOf(lot.bidCount()));
            cfg.set(base + "item", Items.encode(lot.item()));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                warn("could not create " + parent.getAbsolutePath());
                return;
            }
            Path temp = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
            cfg.save(temp.toFile());
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ex) {
            warn("could not save lots.yml: " + ex.getMessage());
        }
    }

    @Override
    public void close() {
        flush();
    }

    private void warn(String message) {
        if (plugin != null) {
            plugin.getLogger().warning("" + message);
        }
    }
}
