package com.xuvigan.aurumauction.data.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.data.HistoryStore;
import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.TopEntry;

/**
 * YAML history with a per-player cap. Small enough to stay local even when lots
 * live in SQL; the interface is ready for a SQL implementation later.
 */
public final class YamlHistoryStore implements HistoryStore {

    private final AurumAuction plugin;
    private final File file;
    private final int maxPerPlayer;
    private final Map<UUID, List<HistoryEntry>> entries = new LinkedHashMap<UUID, List<HistoryEntry>>();

    public YamlHistoryStore(AurumAuction plugin, File file, int maxPerPlayer) {
        this.plugin = plugin;
        this.file = file;
        this.maxPerPlayer = Math.max(10, maxPerPlayer);
        load();
    }

    @Override
    public synchronized void add(HistoryEntry entry) {
        if (entry == null || entry.player() == null) {
            return;
        }
        List<HistoryEntry> list = entries.get(entry.player());
        if (list == null) {
            list = new ArrayList<HistoryEntry>();
            entries.put(entry.player(), list);
        }
        list.add(0, entry);
        while (list.size() > maxPerPlayer) {
            list.remove(list.size() - 1);
        }
        flush();
    }

    @Override
    public synchronized List<HistoryEntry> page(UUID player, int page, int perPage) {
        List<HistoryEntry> list = player == null ? null : entries.get(player);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int size = Math.max(1, perPage);
        int from = Math.max(0, (Math.max(1, page) - 1) * size);
        if (from >= list.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(list.size(), from + size);
        return new ArrayList<HistoryEntry>(list.subList(from, to));
    }

    @Override
    public synchronized int count(UUID player) {
        List<HistoryEntry> list = player == null ? null : entries.get(player);
        return list == null ? 0 : list.size();
    }

    @Override
    public synchronized List<TopEntry> top(HistoryEntry.Role role, int limit) {
        Map<UUID, double[]> totals = new LinkedHashMap<UUID, double[]>();
        Map<UUID, String> names = new LinkedHashMap<UUID, String>();
        for (Map.Entry<UUID, List<HistoryEntry>> bucket : entries.entrySet()) {
            for (HistoryEntry entry : bucket.getValue()) {
                if (entry.role() != role) {
                    continue;
                }
                double[] value = totals.get(entry.player());
                if (value == null) {
                    value = new double[] {0d, 0d};
                    totals.put(entry.player(), value);
                }
                value[0] += entry.price();
                value[1] += 1d;
                names.put(entry.player(), entry.playerName());
            }
        }
        List<TopEntry> out = new ArrayList<TopEntry>();
        for (Map.Entry<UUID, double[]> bucket : totals.entrySet()) {
            double[] value = bucket.getValue();
            out.add(new TopEntry(bucket.getKey(), names.get(bucket.getKey()), value[0], (int) value[1]));
        }
        Collections.sort(out, new Comparator<TopEntry>() {
            @Override
            public int compare(TopEntry a, TopEntry b) {
                int byTotal = Double.compare(b.total(), a.total());
                return byTotal != 0 ? byTotal : Integer.compare(b.count(), a.count());
            }
        });
        if (limit > 0 && out.size() > limit) {
            return new ArrayList<TopEntry>(out.subList(0, limit));
        }
        return out;
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            List<Map<?, ?>> raw = cfg.getMapList(key);
            List<HistoryEntry> list = new ArrayList<HistoryEntry>();
            for (Map<?, ?> map : raw) {
                list.add(new HistoryEntry(
                        toLong(map.get("time")),
                        player,
                        map.get("player-name") == null ? "?" : String.valueOf(map.get("player-name")),
                        HistoryEntry.Role.parse(map.get("role") == null ? null : String.valueOf(map.get("role"))),
                        map.get("item") == null ? "?" : String.valueOf(map.get("item")),
                        map.get("material") == null ? null : String.valueOf(map.get("material")),
                        (int) toLong(map.get("amount")),
                        toDouble(map.get("price")),
                        toDouble(map.get("tax")),
                        map.get("counterparty") == null ? "?" : String.valueOf(map.get("counterparty")),
                        toLong(map.get("lot"))));
            }
            if (!list.isEmpty()) {
                entries.put(player, list);
            }
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

    @Override
    public synchronized void flush() {
        if (file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<HistoryEntry>> bucket : entries.entrySet()) {
            List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
            for (HistoryEntry entry : bucket.getValue()) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("time", Long.valueOf(entry.time()));
                map.put("player-name", entry.playerName());
                map.put("role", entry.role().name());
                map.put("item", entry.item());
                map.put("material", entry.material());
                map.put("amount", Integer.valueOf(entry.amount()));
                map.put("price", Double.valueOf(entry.price()));
                map.put("tax", Double.valueOf(entry.tax()));
                map.put("counterparty", entry.counterparty());
                map.put("lot", Long.valueOf(entry.lotId()));
                raw.add(map);
            }
            cfg.set(bucket.getKey().toString(), raw);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            Path temp = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
            cfg.save(temp.toFile());
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("Could not save " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public void close() {
        flush();
    }
}
