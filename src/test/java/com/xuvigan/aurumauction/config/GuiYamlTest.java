package com.xuvigan.aurumauction.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiYamlTest {

    @Test
    void bundledGuiContainsNewButtons() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File("src/main/resources/gui.yml"));
        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        assertNotNull(buttons, "buttons section");
        assertNotNull(buttons.getConfigurationSection("history"), "buttons.history");
        assertNotNull(buttons.getConfigurationSection("back"), "buttons.back");
        assertNotNull(buttons.getConfigurationSection("confirm"), "buttons.confirm");
        assertNotNull(cfg.getConfigurationSection("history"), "history menu section");
    }
}
