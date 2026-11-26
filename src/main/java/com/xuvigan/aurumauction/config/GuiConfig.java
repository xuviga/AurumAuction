package com.xuvigan.aurumauction.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.xuvigan.aurumauction.AurumAuction;

/** gui.yml accessor: title, rows, lot lore, buttons, filler. */
public final class GuiConfig {

    public static final class Button {

        private final Material material;
        private final String name;
        private final int slot;

        Button(Material material, String name, int slot) {
            this.material = material;
            this.name = name;
            this.slot = slot;
        }

        public Material material() {
            return material;
        }

        public String name() {
            return name;
        }

        public int slot() {
            return slot;
        }
    }

    private final AurumAuction plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private YamlConfiguration bundled = new YamlConfiguration();
    private String title = "&8Аукцион &7— &f%page%&7/&f%pages%";
    private int rows = 6;
    private Material filler = Material.GRAY_STAINED_GLASS_PANE;
    private List<String> lotLore = new ArrayList<String>();
    private List<String> shulkerLore = new ArrayList<String>();
    private String confirmTitle = "&8Подтверждение покупки";
    private int confirmRows = 3;
    private List<String> confirmLore = new ArrayList<String>();
    private List<String> bidLore = new ArrayList<String>();
    private String historyTitle = "&8История &7— &f%page%&7/&f%pages%";
    private List<String> historyLore = new ArrayList<String>();
    private String sellTitle = "&8Выставить предмет за %price%";
    private int sellRows = 3;

    public GuiConfig(AurumAuction plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.isFile()) {
            plugin.saveResource("gui.yml", false);
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
        try {
            InputStream stream = plugin.getResource("gui.yml");
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                this.cfg.setDefaults(defaults);
                this.bundled = defaults;
                stream.close();
            }
        } catch (Throwable ignored) {
            // bundled defaults are optional
        }
        title = cfg.getString("main.title", title);
        rows = Math.max(2, Math.min(6, cfg.getInt("main.rows", 6)));
        filler = material(cfg.getString("filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        lotLore = new ArrayList<String>(cfg.getStringList("lot-lore"));
        shulkerLore = new ArrayList<String>(cfg.getStringList("shulker-lore"));
        confirmTitle = cfg.getString("confirm.title", confirmTitle);
        confirmRows = Math.max(3, Math.min(6, cfg.getInt("confirm.rows", 3)));
        confirmLore = new ArrayList<String>(cfg.getStringList("confirm.lore"));
        bidLore = new ArrayList<String>(cfg.getStringList("bid-lore"));
        historyTitle = cfg.getString("history.title", historyTitle);
        historyLore = new ArrayList<String>(cfg.getStringList("history.lore"));
        sellTitle = cfg.getString("sell.title", sellTitle);
        sellRows = Math.max(2, Math.min(6, cfg.getInt("sell.rows", 3)));
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int contentSlots() {
        return (rows - 1) * 9;
    }

    public Material filler() {
        return filler;
    }

    public List<String> lotLore() {
        return lotLore;
    }

    public List<String> shulkerLore() {
        return shulkerLore;
    }

    public String confirmTitle() {
        return confirmTitle;
    }

    public int confirmRows() {
        return confirmRows;
    }

    public List<String> confirmLore() {
        return confirmLore;
    }

    public List<String> bidLore() {
        return bidLore;
    }

    public String historyTitle() {
        return historyTitle;
    }

    public List<String> historyLore() {
        return historyLore;
    }

    public String sellTitle() {
        return sellTitle;
    }

    public int sellRows() {
        return sellRows;
    }

    public int sellStorageSlots() {
        return Math.max(1, Math.min(18, sellRows * 9 - 9));
    }

    public Button button(String key) {
        ConfigurationSection section = cfg.getConfigurationSection("buttons." + key);
        if (section == null || section.getInt("slot", -1) < 0) {
            // Bukkit does not merge nested default sections into an existing file,
            // so new buttons (added by a plugin update) are read from the jar.
            ConfigurationSection fallback = bundled.getConfigurationSection("buttons." + key);
            if (fallback != null) {
                section = fallback;
            }
        }
        if (section == null) {
            return null;
        }
        String materialName = section.getString("material", "STONE");
        String name = section.getString("name", key);
        int slot = section.getInt("slot", -1);
        if (slot < 0) {
            return null;
        }
        return new Button(material(materialName, Material.STONE), name, slot);
    }

    private static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(name);
        return matched == null ? fallback : matched;
    }
}
