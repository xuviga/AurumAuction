package com.xuvigan.aurumauction.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Guards against broken YAML in the bundled resources (fresh installs parse them). */
class BundledConfigTest {

    @Test
    void allBundledYamlIsValid() {
        String[] files = {"config.yml", "gui.yml", "messages_ru.yml", "messages_en.yml", "plugin.yml"};
        for (final String name : files) {
            assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() throws Throwable {
                    YamlConfiguration configuration = new YamlConfiguration();
                    configuration.load(new File("src/main/resources/" + name));
                }
            }, name + " must be valid YAML");
        }
    }
}
