package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PayoutQueueTest {

    @Test
    void entriesSurviveReload() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-payouts");
        File file = dir.resolve("payouts.yml").toFile();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        PayoutQueue queue = new PayoutQueue(null, file);
        queue.add(first, "Alice", 125.5d, 7L);
        queue.add(second, "Bob", 10d, 8L);
        assertEquals(2, queue.size());
        assertTrue(file.isFile());

        PayoutQueue reloaded = new PayoutQueue(null, file);
        assertEquals(2, reloaded.size());
        assertEquals(125.5d, reloaded.entries().get(0).amount(), 0.001d);
        assertEquals("Alice", reloaded.entries().get(0).name());
        assertEquals(7L, reloaded.entries().get(0).lotId());
        assertEquals(second, reloaded.entries().get(1).seller());

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }

    @Test
    void ignoresZeroAndMissingSeller() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-payouts-empty");
        File file = dir.resolve("payouts.yml").toFile();
        PayoutQueue queue = new PayoutQueue(null, file);
        queue.add(null, "Ghost", 50d, 1L);
        queue.add(UUID.randomUUID(), "Zero", 0d, 2L);
        assertEquals(0, queue.size());
        Files.deleteIfExists(dir);
    }
}
