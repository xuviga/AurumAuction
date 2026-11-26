package com.xuvigan.aurumauction.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class LotQueryTest {

    private static AuctionLot lot(long id, UUID seller, double price, long createdAt, LotCategory category) {
        return new AuctionLot(id, seller, "Seller" + id, null, price, createdAt,
                createdAt + 10_000L, LotStatus.ACTIVE, category);
    }

    @Test
    void filtersBySellerTextAndCategory() {
        UUID alex = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        AuctionLot first = lot(1L, alex, 100d, 1000L, LotCategory.BLOCKS);

        assertTrue(LotQuery.active().seller(alex).matches(first));
        assertFalse(LotQuery.active().seller(bob).matches(first));

        assertTrue(LotQuery.active().text("seller1").matches(first));
        assertFalse(LotQuery.active().text("nobody").matches(first));

        assertTrue(LotQuery.active().category(LotCategory.BLOCKS).matches(first));
        assertFalse(LotQuery.active().category(LotCategory.FOOD).matches(first));
    }

    @Test
    void statusFilterExcludesNonActive() {
        AuctionLot sold = lot(1L, UUID.randomUUID(), 100d, 1000L, LotCategory.OTHER)
                .withStatus(LotStatus.SOLD);
        assertFalse(LotQuery.active().matches(sold));
        assertTrue(LotQuery.active().status(LotStatus.SOLD).matches(sold));
    }

    @Test
    void sortsByPriceAndAge() {
        AuctionLot cheap = lot(1L, UUID.randomUUID(), 10d, 3000L, LotCategory.OTHER);
        AuctionLot expensive = lot(2L, UUID.randomUUID(), 900d, 1000L, LotCategory.OTHER);
        AuctionLot middle = lot(3L, UUID.randomUUID(), 50d, 2000L, LotCategory.OTHER);

        List<AuctionLot> list = new ArrayList<AuctionLot>();
        list.add(expensive);
        list.add(cheap);
        list.add(middle);

        Collections.sort(list, LotQuery.active().sort(LotQuery.Sort.CHEAPEST).comparator());
        assertEquals(1L, list.get(0).id());
        assertEquals(3L, list.get(1).id());
        assertEquals(2L, list.get(2).id());

        Collections.sort(list, LotQuery.active().sort(LotQuery.Sort.NEWEST).comparator());
        assertEquals(1L, list.get(0).id());
        assertEquals(3L, list.get(1).id());
        assertEquals(2L, list.get(2).id());

        Collections.sort(list, LotQuery.active().sort(LotQuery.Sort.EXPENSIVE).comparator());
        assertEquals(2L, list.get(0).id());
    }

    @Test
    void sortsByUnitPrice() {
        UUID seller = UUID.randomUUID();
        AuctionLot stack = new AuctionLot(1L, seller, "A", new ItemStack(Material.DIRT, 64), 100d,
                2000L, 3000L, LotStatus.ACTIVE, LotCategory.BLOCKS);
        AuctionLot single = new AuctionLot(2L, seller, "A", new ItemStack(Material.DIRT, 1), 10d,
                2000L, 3000L, LotStatus.ACTIVE, LotCategory.BLOCKS);
        java.util.List<AuctionLot> list = new java.util.ArrayList<AuctionLot>();
        list.add(single);
        list.add(stack);
        java.util.Collections.sort(list, LotQuery.active().sort(LotQuery.Sort.UNIT_CHEAPEST).comparator());
        assertEquals(1L, list.get(0).id(), "100/64 is cheaper per unit than 10/1");
        java.util.Collections.sort(list, LotQuery.active().sort(LotQuery.Sort.UNIT_EXPENSIVE).comparator());
        assertEquals(2L, list.get(0).id());
    }

    @Test
    void sortCycles() {
        assertEquals(LotQuery.Sort.CHEAPEST, LotQuery.Sort.NEWEST.next());
        assertEquals(LotQuery.Sort.UNIT_CHEAPEST, LotQuery.Sort.EXPENSIVE.next());
        assertEquals(LotQuery.Sort.UNIT_EXPENSIVE, LotQuery.Sort.UNIT_CHEAPEST.next());
        assertEquals(LotQuery.Sort.NAME, LotQuery.Sort.UNIT_EXPENSIVE.next());
        assertEquals(LotQuery.Sort.NEWEST, LotQuery.Sort.NAME.next());
    }
}
