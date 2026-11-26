package com.xuvigan.aurumauction.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AuctionLotTest {

    private static AuctionLot lot(long expiresAt, LotStatus status) {
        return new AuctionLot(7L, UUID.randomUUID(), "Steve", null, 5000d,
                1000L, expiresAt, status, LotCategory.OTHER);
    }

    @Test
    void bidFieldsArePreserved() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();
        AuctionLot lot = new AuctionLot(1L, seller, "Seller", null, 100d, 1000L, 2000L,
                LotStatus.ACTIVE, LotCategory.OTHER, 0L, LotType.BID, 500d, null, null, 0);
        assertTrue(lot.isBid());
        assertTrue(lot.hasBuyout());
        assertFalse(lot.hasBids());
        assertEquals(500d, lot.buyoutPrice(), 0.001d);

        AuctionLot raised = lot.withBid(120d, bidder, "Bidder", 1);
        assertEquals(120d, raised.price(), 0.001d);
        assertEquals(1, raised.bidCount());
        assertEquals(bidder, raised.bidder());
        assertEquals("Bidder", raised.bidderName());
        assertTrue(raised.hasBids());
        assertEquals(500d, raised.buyoutPrice(), 0.001d);

        AuctionLot extended = raised.withExpiry(9000L);
        assertEquals(9000L, extended.expiresAt());
        assertEquals(1, extended.bidCount());
        assertEquals(120d, extended.price(), 0.001d);
    }

    @Test
    void closedAtTracksTerminalStatuses() {
        AuctionLot active = lot(2000L, LotStatus.ACTIVE);
        assertEquals(0L, active.closedAt());
        long soldAt = active.withStatus(LotStatus.SOLD).closedAt();
        assertTrue(soldAt > 0L);
        assertTrue(active.withStatus(LotStatus.CANCELLED).closedAt() > 0L);
        assertEquals(0L, active.withStatus(LotStatus.EXPIRED).closedAt());
        AuctionLot sold = active.withStatus(LotStatus.SOLD);
        assertEquals(soldAt, sold.withStatus(LotStatus.SOLD).closedAt());
    }

    @Test
    void expiryIsInclusive() {
        AuctionLot lot = lot(2000L, LotStatus.ACTIVE);
        assertFalse(lot.isExpired(1999L));
        assertTrue(lot.isExpired(2000L));
        assertEquals(0L, lot.remainingMillis(2500L));
        assertEquals(500L, lot.remainingMillis(1500L));
    }

    @Test
    void activeRequiresActiveStatusAndTime() {
        assertTrue(lot(2000L, LotStatus.ACTIVE).isActive(1500L));
        assertFalse(lot(2000L, LotStatus.ACTIVE).isActive(2000L));
        assertFalse(lot(2000L, LotStatus.SOLD).isActive(1500L));
    }

    @Test
    void withStatusKeepsIdentity() {
        AuctionLot original = lot(2000L, LotStatus.ACTIVE);
        AuctionLot sold = original.withStatus(LotStatus.SOLD);
        assertEquals(original.id(), sold.id());
        assertSame(original.item(), sold.item());
        assertEquals(LotStatus.SOLD, sold.status());
        assertEquals(LotStatus.ACTIVE, original.status());
    }

    @Test
    void unitPriceHandlesMissingItem() {
        assertEquals(5000d, lot(2000L, LotStatus.ACTIVE).unitPrice(), 0.001d);
        assertEquals(1, lot(2000L, LotStatus.ACTIVE).amount());
    }

    @Test
    void searchTextContainsSellerAndId() {
        String text = lot(2000L, LotStatus.ACTIVE).searchText();
        assertTrue(text.contains("steve"));
        assertTrue(text.contains("7"));
    }
}
