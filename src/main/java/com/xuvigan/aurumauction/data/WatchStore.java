package com.xuvigan.aurumauction.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

import com.xuvigan.aurumauction.AurumAuction;

/** Per-player watchlist of text queries (item, seller or category). */
public final class WatchStore {

    public enum Result {
        ADDED,
        EXISTS,
        LIMIT
    }

    private final AurumAuction plugin;
    private final File file;
    private final int maxPerPlayer;
    private final Map<UUID, List<String>> watches = new LinkedHashMap<UUID, List<String>>();

    public WatchStore(AurumAuction plugin, File file, int maxPerPlayer) {
        this.plugin = plugin;
        this.file = file;
        this.maxPerPlayer = Math.max(1, maxPerPlayer);
        load();
    }

    public synchronized Result add(UUID player, String query) {
        String value = normalize(query);
        if (player == null || value.isEmpty()) {
            return Result.LIMIT;
        }
        List<String> list = watches.get(player);
        if (list == null) {
            list = new ArrayList<String>();
            watches.put(player, list);
        }
        if (list.contains(value)) {
            return Result.EXISTS;
        }
        if (list.size() >= maxPerPlayer) {
            return Result.LIMIT;
        }
        list.add(value);
        flush();
        return Result.ADDED;
    }

    public synchronized boolean remove(UUID player, String query) {
        List<String> list = player == null ? null : watches.get(player);
        if (list == null) {
            return false;
        }
        boolean removed = list.remove(normalize(query));
        if (removed) {
            if (list.isEmpty()) {
                watches.remove(player);
            }
            flush();
        }
        return removed;
    }

    public synchronized void clear(UUID player) {
        if (player != null && watches.remove(player) != null) {
            flush();
        }
    }

    public synchronized List<String> list(UUID player) {
        List<String> list = player == null ? null : watches.get(player);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(list));
    }

    public synchronized Map<UUID, List<String>> all() {
        Map<UUID, List<String>> copy = new LinkedHashMap<UUID, List<String>>();
        for (Map.Entry<UUID, List<String>> entry : watches.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
        }
        return copy;
    }

    public int maxPerPlayer() {
        return maxPerPlayer;
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID player = UUID.fromString(key);
                List<String> list = new ArrayList<String>();
                for (String value : cfg.getStringList(key)) {
                    String normalized = normalize(value);
                    if (!normalized.isEmpty() && !list.contains(normalized)) {
                        list.add(normalized);
                    }
                }
                if (!list.isEmpty()) {
                    watches.put(player, list);
                }
            } catch (IllegalArgumentException ignored) {
                // malformed key
            }
        }
    }

    public synchronized void flush() {
        if (file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<String>> entry : watches.entrySet()) {
            cfg.set(entry.getKey().toString(), entry.getValue());
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
}
