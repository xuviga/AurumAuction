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
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.util.Items;

/** Items won by offline players (or when the inventory was full). */
public final class ClaimQueue {

    public static final class Claim {

        private final long lotId;
        private final String item;
        private final long createdAt;

        Claim(long lotId, String item, long createdAt) {
            this.lotId = lotId;
            this.item = item;
            this.createdAt = createdAt;
        }

        public long lotId() {
            return lotId;
        }

        public long createdAt() {
            return createdAt;
        }

        public ItemStack decode() {
            return Items.decode(item);
        }
    }

    private final AurumAuction plugin;
    private final File file;
    private final Map<UUID, List<Claim>> pending = new LinkedHashMap<UUID, List<Claim>>();

    public ClaimQueue(AurumAuction plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    public synchronized void add(UUID player, ItemStack item, long lotId) {
        if (player == null || item == null) {
            return;
        }
        String encoded = Items.encode(item);
        if (encoded == null) {
            return;
        }
        List<Claim> list = pending.get(player);
        if (list == null) {
            list = new ArrayList<Claim>();
            pending.put(player, list);
        }
        list.add(new Claim(lotId, encoded, System.currentTimeMillis()));
        flush();
    }

    /** Returns and removes all claims of the player (caller gives the items). */
    public synchronized List<Claim> take(UUID player) {
        if (player == null) {
            return Collections.emptyList();
        }
        List<Claim> list = pending.remove(player);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        flush();
        return list;
    }

    public synchronized int count(UUID player) {
        List<Claim> list = player == null ? null : pending.get(player);
        return list == null ? 0 : list.size();
    }

    public synchronized int size() {
        int total = 0;
        for (List<Claim> list : pending.values()) {
            total += list.size();
        }
        return total;
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
            List<Claim> list = new ArrayList<Claim>();
            for (Map<?, ?> map : raw) {
                long lotId = toLong(map.get("lot-id"));
                long createdAt = toLong(map.get("created-at"));
                String item = map.get("item") == null ? null : String.valueOf(map.get("item"));
                if (item != null && !item.isEmpty()) {
                    list.add(new Claim(lotId, item, createdAt));
                }
            }
            if (!list.isEmpty()) {
                pending.put(player, list);
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

    public synchronized void flush() {
        if (file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<Claim>> entry : pending.entrySet()) {
            List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
            for (Claim claim : entry.getValue()) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                map.put("lot-id", Long.valueOf(claim.lotId()));
                map.put("created-at", Long.valueOf(claim.createdAt()));
                map.put("item", claim.item);
                raw.add(map);
            }
            cfg.set(entry.getKey().toString(), raw);
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
