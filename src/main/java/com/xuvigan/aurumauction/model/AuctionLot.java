package com.xuvigan.aurumauction.model;

import java.util.Locale;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import com.xuvigan.aurumauction.util.Items;

/**
 * Immutable auction lot. The item keeps its full meta (NBT/CMD/enchantments).
 * {@code price} is the fixed price for FIXED lots and the current highest bid
 * (or the starting price before the first bid) for BID lots.
 */
public final class AuctionLot {

    private final long id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long expiresAt;
    private final LotStatus status;
    private final LotCategory category;
    private final long closedAt;
    private final LotType type;
    private final double buyoutPrice;
    private final UUID bidder;
    private final String bidderName;
    private final int bidCount;

    public AuctionLot(long id, UUID seller, String sellerName, ItemStack item, double price,
                      long createdAt, long expiresAt, LotStatus status, LotCategory category) {
        this(id, seller, sellerName, item, price, createdAt, expiresAt, status, category, 0L);
    }

    public AuctionLot(long id, UUID seller, String sellerName, ItemStack item, double price,
                      long createdAt, long expiresAt, LotStatus status, LotCategory category, long closedAt) {
        this(id, seller, sellerName, item, price, createdAt, expiresAt, status, category, closedAt,
                LotType.FIXED, 0d, null, null, 0);
    }

    public AuctionLot(long id, UUID seller, String sellerName, ItemStack item, double price,
                      long createdAt, long expiresAt, LotStatus status, LotCategory category, long closedAt,
                      LotType type, double buyoutPrice, UUID bidder, String bidderName, int bidCount) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName == null ? "?" : sellerName;
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status == null ? LotStatus.ACTIVE : status;
        this.category = category == null ? LotCategory.OTHER : category;
        this.closedAt = closedAt;
        this.type = type == null ? LotType.FIXED : type;
        this.buyoutPrice = Math.max(0d, buyoutPrice);
        this.bidder = bidder;
        this.bidderName = bidderName;
        this.bidCount = Math.max(0, bidCount);
    }

    public long id() {
        return id;
    }

    public UUID seller() {
        return seller;
    }

    public String sellerName() {
        return sellerName;
    }

    public ItemStack item() {
        return item;
    }

    public double price() {
        return price;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public LotStatus status() {
        return status;
    }

    public LotCategory category() {
        return category;
    }

    /** Timestamp when the lot reached SOLD/CANCELLED; 0 while it is still open. */
    public long closedAt() {
        return closedAt;
    }

    public LotType type() {
        return type;
    }

    public boolean isBid() {
        return type == LotType.BID;
    }

    public double buyoutPrice() {
        return buyoutPrice;
    }

    public boolean hasBuyout() {
        return buyoutPrice > 0d;
    }

    public UUID bidder() {
        return bidder;
    }

    public String bidderName() {
        return bidderName;
    }

    public int bidCount() {
        return bidCount;
    }

    public boolean hasBids() {
        return bidder != null && bidCount > 0;
    }

    public int amount() {
        return item == null ? 1 : Math.max(1, item.getAmount());
    }

    public double unitPrice() {
        return price / amount();
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public boolean isActive(long now) {
        return status == LotStatus.ACTIVE && !isExpired(now);
    }

    public long remainingMillis(long now) {
        return Math.max(0L, expiresAt - now);
    }

    public AuctionLot withStatus(LotStatus newStatus) {
        long closed = closedAt;
        if (newStatus == LotStatus.SOLD || newStatus == LotStatus.CANCELLED) {
            closed = closedAt > 0L ? closedAt : System.currentTimeMillis();
        }
        return new AuctionLot(id, seller, sellerName, item, price, createdAt, expiresAt, newStatus, category,
                closed, type, buyoutPrice, bidder, bidderName, bidCount);
    }

    public AuctionLot withBid(double amount, UUID newBidder, String newBidderName, int newBidCount) {
        return new AuctionLot(id, seller, sellerName, item, amount, createdAt, expiresAt, status, category,
                closedAt, type, buyoutPrice, newBidder, newBidderName, newBidCount);
    }

    public AuctionLot withExpiry(long newExpiresAt) {
        return new AuctionLot(id, seller, sellerName, item, price, createdAt, newExpiresAt, status, category,
                closedAt, type, buyoutPrice, bidder, bidderName, bidCount);
    }

    /** Lower-cased haystack for the text search (material, custom name, seller, bidder). */
    public String searchText() {
        StringBuilder out = new StringBuilder(64);
        out.append(Items.displayName(item)).append(' ');
        out.append(Items.humanise(item == null ? null : item.getType())).append(' ');
        out.append(sellerName).append(' ');
        if (bidderName != null) {
            out.append(bidderName).append(' ');
        }
        out.append(category.name()).append(' ');
        out.append(id);
        return out.toString().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "AuctionLot{id=" + id + ", seller=" + sellerName + ", price=" + price
                + ", status=" + status + ", type=" + type + ", bids=" + bidCount
                + ", item=" + (item == null ? "null" : item.getType()) + "}";
    }
}
