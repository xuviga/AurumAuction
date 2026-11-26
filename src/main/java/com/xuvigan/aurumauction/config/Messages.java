package com.xuvigan.aurumauction.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import com.xuvigan.aurumauction.util.Text;

/** messages.yml accessor: prefix, colours and positional placeholders. */
public final class Messages {

    private FileConfiguration cfg;
    private String prefix = "";

    public Messages(FileConfiguration cfg) {
        apply(cfg);
    }

    public void reload(FileConfiguration cfg) {
        apply(cfg);
    }

    private void apply(FileConfiguration config) {
        this.cfg = config;
        this.prefix = prepare(config == null ? "" : config.getString("prefix", ""));
    }

    public String prefix() {
        return prefix;
    }

    public String raw(String path, String def) {
        if (cfg == null) {
            return def == null ? "" : def;
        }
        String value = cfg.getString(path);
        return value == null ? (def == null ? "" : def) : value;
    }

    private String prepare(String text) {
        return Text.color(text == null ? "" : text);
    }

    public String get(String path) {
        return prepare(raw(path, ""));
    }

    public String get(String path, String def) {
        return prepare(raw(path, def));
    }

    public String format(String path, Object... pairs) {
        return Text.formatPairs(get(path), pairs);
    }

    public String message(String path, Object... pairs) {
        String body = format(path, pairs);
        return prefix + body;
    }

    public List<String> list(String path) {
        List<String> rawList = cfg == null ? null : cfg.getStringList(path);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>(rawList.size());
        for (String line : rawList) {
            out.add(prepare(line));
        }
        return out;
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        List<String> source = list(path);
        List<String> out = new ArrayList<String>(source.size());
        for (String line : source) {
            out.add(Text.format(line, placeholders));
        }
        return out;
    }

    public void send(CommandSender sender, String path, Object... pairs) {
        if (sender == null) {
            return;
        }
        String body = format(path, pairs);
        if (body.isEmpty()) {
            return;
        }
        sender.sendMessage(prefix + body);
    }

    public void sendRaw(CommandSender sender, String text) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(prepare(text));
    }
}
