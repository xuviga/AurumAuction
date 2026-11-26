package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.xuvigan.aurumauction.data.impl.YamlHistoryStore;
import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.TopEntry;

class YamlHistoryStoreTest {

    private static HistoryEntry entry(UUID player, String name, HistoryEntry.Role role, double price, long time) {
        return new HistoryEntry(time, player, name, role, "Diamond", "DIAMOND", 64, price, 0d, "Other", 1L);
    }

    @Test
    void pagesCapsAndTops() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-history");
        File file = dir.resolve("history.yml").toFile();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        YamlHistoryStore store = new YamlHistoryStore(null, file, 10);
        store.add(entry(alice, "Alice", HistoryEntry.Role.SELLER, 100d, 1L));
        store.add(entry(alice, "Alice", HistoryEntry.Role.SELLER, 200d, 2L));
        store.add(entry(bob, "Bob", HistoryEntry.Role.SELLER, 50d, 3L));
        store.add(entry(buyer, "Buyer", HistoryEntry.Role.BUYER, 150d, 4L));

        assertEquals(2, store.count(alice));
        List<HistoryEntry> first = store.page(alice, 1, 1);
        assertEquals(1, first.size());
        assertEquals(200d, first.get(0).price(), 0.001d, "newest entry comes first");

        List<TopEntry> sellers = store.top(HistoryEntry.Role.SELLER, 10);
        assertEquals(2, sellers.size());
        assertEquals("Alice", sellers.get(0).name());
        assertEquals(300d, sellers.get(0).total(), 0.001d);
        assertEquals(2, sellers.get(0).count());
        assertEquals("Bob", sellers.get(1).name());

        List<TopEntry> buyers = store.top(HistoryEntry.Role.BUYER, 10);
        assertEquals(1, buyers.size());
        assertEquals("Buyer", buyers.get(0).name());

        for (int i = 0; i < 20; i++) {
            store.add(entry(alice, "Alice", HistoryEntry.Role.SELLER, i, 100L + i));
        }
        assertEquals(10, store.count(alice), "per-player cap is enforced");

        YamlHistoryStore reloaded = new YamlHistoryStore(null, file, 10);
        assertEquals(10, reloaded.count(alice));
        assertEquals(1, reloaded.count(bob));

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }

    @Test
    void emptyPlayerIsSafe() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-history-empty");
        File file = dir.resolve("history.yml").toFile();
        YamlHistoryStore store = new YamlHistoryStore(null, file, 10);
        assertTrue(store.page(UUID.randomUUID(), 1, 10).isEmpty());
        assertEquals(0, store.count(null));
        assertTrue(store.top(HistoryEntry.Role.SELLER, 5).isEmpty());
        Files.deleteIfExists(dir);
    }
}
