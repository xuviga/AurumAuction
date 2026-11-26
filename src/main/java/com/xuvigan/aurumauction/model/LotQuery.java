package com.xuvigan.aurumauction.model;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

import com.xuvigan.aurumauction.util.Items;

/** Filter + sort description used by repositories and the GUI. */
public final class LotQuery {

    public enum Sort {
        NEWEST,
        CHEAPEST,
        EXPENSIVE,
        UNIT_CHEAPEST,
        UNIT_EXPENSIVE,
        NAME;

        public Sort next() {
            Sort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private UUID seller;
    private String text;
    private LotCategory category;
    private LotStatus status = LotStatus.ACTIVE;
    private Sort sort = Sort.NEWEST;

    public static LotQuery active() {
        return new LotQuery();
    }

    public LotQuery seller(UUID seller) {
        this.seller = seller;
        return this;
    }

    public LotQuery text(String text) {
        this.text = text == null ? null : text.trim().toLowerCase(Locale.ROOT);
        return this;
    }

    public LotQuery category(LotCategory category) {
        this.category = category;
        return this;
    }

    public LotQuery status(LotStatus status) {
        this.status = status;
        return this;
    }

    public LotQuery sort(Sort sort) {
        this.sort = sort == null ? Sort.NEWEST : sort;
        return this;
    }

    public UUID seller() {
        return seller;
    }

    public String text() {
        return text;
    }

    public LotCategory category() {
        return category;
    }

    public LotStatus status() {
        return status;
    }

    public Sort sort() {
        return sort;
    }

    public boolean matches(AuctionLot lot) {
        if (lot == null) {
            return false;
        }
        if (status != null && lot.status() != status) {
            return false;
        }
        if (seller != null && !seller.equals(lot.seller())) {
            return false;
        }
        if (category != null && lot.category() != category) {
            return false;
        }
        if (text != null && !text.isEmpty() && !lot.searchText().contains(text)) {
            return false;
        }
        return true;
    }

    public Comparator<AuctionLot> comparator() {
        switch (sort) {
            case CHEAPEST:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        int byPrice = Double.compare(a.price(), b.price());
                        return byPrice != 0 ? byPrice : Long.compare(b.id(), a.id());
                    }
                };
            case EXPENSIVE:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        int byPrice = Double.compare(b.price(), a.price());
                        return byPrice != 0 ? byPrice : Long.compare(b.id(), a.id());
                    }
                };
            case UNIT_CHEAPEST:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        int byUnit = Double.compare(a.unitPrice(), b.unitPrice());
                        return byUnit != 0 ? byUnit : Long.compare(b.id(), a.id());
                    }
                };
            case UNIT_EXPENSIVE:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        int byUnit = Double.compare(b.unitPrice(), a.unitPrice());
                        return byUnit != 0 ? byUnit : Long.compare(b.id(), a.id());
                    }
                };
            case NAME:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        String left = Items.displayName(a.item());
                        String right = Items.displayName(b.item());
                        int byName = left.compareToIgnoreCase(right);
                        return byName != 0 ? byName : Long.compare(a.id(), b.id());
                    }
                };
            case NEWEST:
            default:
                return new Comparator<AuctionLot>() {
                    @Override
                    public int compare(AuctionLot a, AuctionLot b) {
                        int byTime = Long.compare(b.createdAt(), a.createdAt());
                        return byTime != 0 ? byTime : Long.compare(b.id(), a.id());
                    }
                };
        }
    }
}
