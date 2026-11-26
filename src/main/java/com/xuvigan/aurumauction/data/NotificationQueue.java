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

import com.xuvigan.aurumauction.AurumAuction;

/** Offline notification queue: messages are delivered on the next join. */
public final class NotificationQueue {

    private final AurumAuction plugin;
    private final File file;
    private final Map<UUID, List<String>> pending = new LinkedHashMap<UUID, List<String>>();

    public NotificationQueue(AurumAuction plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        load();
    }

    public synchronized void add(UUID player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        List<String> list = pending.get(player);
        if (list == null) {
            list = new ArrayList<String>();
            pending.put(player, list);
        }
        list.add(message);
        if (list.size() > 50) {
            list.remove(0);
        }
        flush();
    }

    /** Returns and removes every pending message for the player. */
    public synchronized List<String> take(UUID player) {
        if (player == null) {
            return Collections.emptyList();
        }
        List<String> list = pending.remove(player);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        flush();
        return list;
    }

    public synchronized int size() {
        int total = 0;
        for (List<String> list : pending.values()) {
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
            try {
                UUID player = UUID.fromString(key);
                pending.put(player, new ArrayList<String>(cfg.getStringList(key)));
            } catch (IllegalArgumentException ignored) {
                // skip malformed key
            }
        }
    }

    public synchronized void flush() {
        if (file == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<String>> entry : pending.entrySet()) {
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
