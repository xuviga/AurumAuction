package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotStatus;

class TransactionLogTest {

    @Test
    void appendsReadableLines() throws Exception {
        Path dir = Files.createTempDirectory("aurumauction-log");
        File file = dir.resolve("transactions.log").toFile();
        TransactionLog log = new TransactionLog(null, file, true);
        AuctionLot lot = new AuctionLot(7L, UUID.randomUUID(), "Seller", new ItemStack(Material.DIAMOND, 64),
                6400d, 1000L, 2000L, LotStatus.ACTIVE, LotCategory.OTHER);
        log.log("BUY", lot, "Buyer", "Seller", 6400d, 320d);

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("| BUY |"));
        assertTrue(content.contains("lot=7"));
        assertTrue(content.contains("Diamond x64"));
        assertTrue(content.contains("actor=Buyer"));
        assertTrue(content.contains("price=6400.0"));
        assertTrue(content.contains("tax=320.0"));

        TransactionLog disabled = new TransactionLog(null, dir.resolve("off.log").toFile(), false);
        disabled.log("BUY", lot, "A", "B", 1d, 0d);
        assertTrue(!dir.resolve("off.log").toFile().exists());

        Files.deleteIfExists(file.toPath());
        Files.deleteIfExists(dir);
    }
}
