package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.xuvigan.aurumauction.data.impl.YamlLotStorage;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotStatus;

class YamlLotStorageTest {

    private static AuctionLot lot(long id, LotStatus status, long closedAt, long expiresAt) {
        return new AuctionLot(id, UUID.randomUUID(), "Seller" + id,
                new ItemStack(Material.DIRT, 1), 100d,
                1000L, expiresAt, status, LotCategory.BLOCKS, closedAt);
    }

    @Test
    void purgeOnlyRemovesOldClosedLots() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-lots");
        File file = dir.resolve("lots.yml").toFile();
        long now = System.currentTimeMillis();

        YamlLotStorage storage = new YamlLotStorage(null, file);
        storage.save(lot(1L, LotStatus.SOLD, now - 90_000L, now));
        storage.save(lot(2L, LotStatus.CANCELLED, now - 1_000L, now));
        storage.save(lot(3L, LotStatus.EXPIRED, 0L, now - 90_000L));
        storage.save(lot(4L, LotStatus.ACTIVE, 0L, now + 90_000L));
        storage.flush();

        int removed = storage.purgeClosed(now - 50_000L);
        assertEquals(1, removed);
        assertNull(storage.find(1L));
        assertNotNull(storage.find(2L));
        assertNotNull(storage.find(3L), "expired lots hold items and must never be purged");
        assertNotNull(storage.find(4L));

        storage.flush();
        // Reload is covered by the E2E persistence check: Bukkit item (de)serialisation
        // needs a live server, so it cannot be asserted in a plain JUnit run.

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }
}
