package com.xuvigan.aurumauction.model;

import java.util.UUID;

/** One completed transaction line in a player's history. */
public final class HistoryEntry {

    public enum Role {
        BUYER,
        SELLER;

        public static Role parse(String value) {
            if (value == null) {
                return BUYER;
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return BUYER;
            }
        }
    }

    private final long time;
    private final UUID player;
    private final String playerName;
    private final Role role;
    private final String item;
    private final String material;
    private final int amount;
    private final double price;
    private final double tax;
    private final String counterparty;
    private final long lotId;

    public HistoryEntry(long time, UUID player, String playerName, Role role, String item, String material,
                        int amount, double price, double tax, String counterparty, long lotId) {
        this.time = time;
        this.player = player;
        this.playerName = playerName == null ? "?" : playerName;
        this.role = role == null ? Role.BUYER : role;
        this.item = item == null ? "?" : item;
        this.material = material;
        this.amount = Math.max(1, amount);
        this.price = price;
        this.tax = tax;
        this.counterparty = counterparty == null ? "?" : counterparty;
        this.lotId = lotId;
    }

    public long time() {
        return time;
    }

    public UUID player() {
        return player;
    }

    public String playerName() {
        return playerName;
    }

    public Role role() {
        return role;
    }

    public String item() {
        return item;
    }

    public String material() {
        return material;
    }

    public int amount() {
        return amount;
    }

    public double price() {
        return price;
    }

    public double tax() {
        return tax;
    }

    public String counterparty() {
        return counterparty;
    }

    public long lotId() {
        return lotId;
    }
}
