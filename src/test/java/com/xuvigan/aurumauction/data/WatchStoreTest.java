package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WatchStoreTest {

    @Test
    void addLimitRemoveAndReload() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-watch");
        File file = dir.resolve("watchlist.yml").toFile();
        UUID player = UUID.randomUUID();

        WatchStore store = new WatchStore(null, file, 2);
        assertEquals(WatchStore.Result.ADDED, store.add(player, " Diamond "));
        assertEquals(WatchStore.Result.EXISTS, store.add(player, "diamond"));
        assertEquals(WatchStore.Result.ADDED, store.add(player, "alice"));
        assertEquals(WatchStore.Result.LIMIT, store.add(player, "third"));
        assertEquals(2, store.list(player).size());
        assertEquals("diamond", store.list(player).get(0));

        assertTrue(store.remove(player, "DIAMOND"));
        assertFalse(store.remove(player, "missing"));
        assertEquals(1, store.list(player).size());

        WatchStore reloaded = new WatchStore(null, file, 2);
        assertEquals(1, reloaded.list(player).size());
        assertEquals("alice", reloaded.list(player).get(0));

        reloaded.clear(player);
        assertEquals(0, reloaded.list(player).size());

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }
}
