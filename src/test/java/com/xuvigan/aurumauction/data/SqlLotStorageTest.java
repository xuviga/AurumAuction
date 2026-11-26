package com.xuvigan.aurumauction.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import com.xuvigan.aurumauction.data.impl.SqlLotStorage;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;
import com.xuvigan.aurumauction.model.LotType;

class SqlLotStorageTest {

    private static final ItemCodec CODEC = new ItemCodec() {
        @Override
        public String encode(ItemStack item) {
            return item == null ? null : item.getType().name() + ":" + item.getAmount();
        }

        @Override
        public ItemStack decode(String data) {
            if (data == null) {
                return null;
            }
            String[] parts = data.split(":");
            Material material = Material.matchMaterial(parts[0]);
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            return material == null ? null : new ItemStack(material, Math.max(1, amount));
        }
    };

    private static AuctionLot lot(long id, UUID seller, double price, long created, LotCategory category, LotType type) {
        return new AuctionLot(id, seller, "Seller" + id, new ItemStack(Material.DIRT, 1), price,
                created, created + 100_000L, LotStatus.ACTIVE, category, 0L,
                type, 0d, null, null, 0);
    }

    @Test
    void compareAndSetUpdatesPartialPurchaseColumns() throws Exception {
        File db = Files.createTempFile("aurumauction-cas", ".db").toFile();
        SqlLotStorage storage = new SqlLotStorage(null, "jdbc:sqlite:" + db.getAbsolutePath(),
                null, null, "c_", CODEC);
        try {
            long id = storage.nextId();
            UUID seller = UUID.randomUUID();
            long now = System.currentTimeMillis();
            AuctionLot full = new AuctionLot(id, seller, "Seller", new ItemStack(Material.DIRT, 10), 100d,
                    now, now + 100_000L, LotStatus.ACTIVE, LotCategory.BLOCKS);
            storage.save(full);

            AuctionLot rest = new AuctionLot(id, seller, "Seller", new ItemStack(Material.DIRT, 7), 70d,
                    now, now + 100_000L, LotStatus.ACTIVE, LotCategory.BLOCKS);
            assertTrue(storage.compareAndSet(full, rest));

            AuctionLot loaded = storage.find(id);
            assertEquals(7, loaded.amount());
            assertEquals(70d, loaded.price(), 0.001d);
            assertEquals(10d, loaded.unitPrice(), 0.001d, "unit price must be 70/7");
        } finally {
            storage.close();
            Files.deleteIfExists(db.toPath());
        }
    }

    @Test
    void multiServerIdsAndAtomicTransitions() throws Exception {
        File db = Files.createTempFile("aurumauction-multi", ".db").toFile();
        String url = "jdbc:sqlite:" + db.getAbsolutePath();
        com.xuvigan.aurumauction.util.LotIdGenerator idsA =
                new com.xuvigan.aurumauction.util.LotIdGenerator(1);
        com.xuvigan.aurumauction.util.LotIdGenerator idsB =
                new com.xuvigan.aurumauction.util.LotIdGenerator(2);
        SqlLotStorage a = new SqlLotStorage(null, url, null, null, "m_", CODEC, idsA);
        SqlLotStorage b = new SqlLotStorage(null, url, null, null, "m_", CODEC, idsB);
        try {
            long idA = idsA.next();
            long idB = idsB.next();
            assertNotEquals(idA, idB);
            a.save(new AuctionLot(idA, UUID.randomUUID(), "A", new ItemStack(Material.DIRT, 1), 10d,
                    System.currentTimeMillis(), System.currentTimeMillis() + 1000L,
                    LotStatus.ACTIVE, LotCategory.OTHER));
            b.save(new AuctionLot(idB, UUID.randomUUID(), "B", new ItemStack(Material.DIRT, 1), 20d,
                    System.currentTimeMillis(), System.currentTimeMillis() + 1000L,
                    LotStatus.ACTIVE, LotCategory.OTHER));

            assertNotNull(b.find(idA), "server B must see server A's lot");
            assertEquals(2, a.createdSince(0L, 10).size(), "cross-server poll sees both lots");

            assertTrue(a.transition(idA, LotStatus.ACTIVE, LotStatus.SOLD));
            assertTrue(!b.transition(idA, LotStatus.ACTIVE, LotStatus.SOLD),
                    "only one server may settle a lot");
            assertEquals(LotStatus.SOLD, b.find(idA).status());
        } finally {
            a.close();
            b.close();
            Files.deleteIfExists(db.toPath());
        }
    }

    @Test
    void crudSearchAndPurge() throws Exception {
        File db = Files.createTempFile("aurumauction", ".db").toFile();
        SqlLotStorage storage = new SqlLotStorage(null, "jdbc:sqlite:" + db.getAbsolutePath(),
                null, null, "t_", CODEC);
        try {
            UUID alex = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            long now = System.currentTimeMillis();

            storage.save(lot(1L, alex, 100d, now, LotCategory.BLOCKS, LotType.FIXED));
            storage.save(lot(2L, bob, 50d, now + 1, LotCategory.OTHER, LotType.BID));
            storage.save(lot(3L, alex, 900d, now + 2, LotCategory.BLOCKS, LotType.FIXED));

            assertEquals(3, storage.count(null));
            assertEquals(4L, storage.nextId());
            assertNotNull(storage.find(2L));
            assertEquals(100d, storage.find(1L).price(), 0.001d);

            assertEquals(2, storage.count(LotQuery.active().seller(alex)));
            assertEquals(2, storage.count(LotQuery.active().category(LotCategory.BLOCKS)));
            assertEquals(1, storage.count(LotQuery.active().text("seller2")));
            assertEquals(2L, storage.search(LotQuery.active().sort(LotQuery.Sort.CHEAPEST), 1, 10).get(0).id());
            assertEquals(3L, storage.search(LotQuery.active().sort(LotQuery.Sort.EXPENSIVE), 1, 10).get(0).id());
            assertEquals(2, storage.search(LotQuery.active(), 1, 2).size());

            // bidding fields round-trip
            AuctionLot bid = storage.find(2L);
            AuctionLot raised = bid.withBid(75d, alex, "Alex", 1);
            storage.save(raised);
            AuctionLot loaded = storage.find(2L);
            assertEquals(LotType.BID, loaded.type());
            assertEquals(75d, loaded.price(), 0.001d);
            assertEquals(1, loaded.bidCount());
            assertEquals(alex, loaded.bidder());

            // due + purge
            AuctionLot expired = new AuctionLot(4L, alex, "Alex", new ItemStack(Material.DIRT, 1), 10d,
                    now - 500_000L, now - 1_000L, LotStatus.ACTIVE, LotCategory.OTHER);
            storage.save(expired);
            assertEquals(1, storage.due(now).stream().filter(l -> l.id() == 4L).count());

            storage.save(expired.withStatus(LotStatus.SOLD));
            assertTrue(storage.purgeClosed(now + 10_000L) >= 1);
            assertNull(storage.find(4L));

            assertEquals(3, storage.all().size());
        } finally {
            storage.close();
            Files.deleteIfExists(db.toPath());
            Files.deleteIfExists(new File(db.getAbsolutePath() + "-wal").toPath());
            Files.deleteIfExists(new File(db.getAbsolutePath() + "-shm").toPath());
        }
    }
}
