package com.xuvigan.aurumauction.model;

import java.util.UUID;

/** Aggregated leaderboard row (top seller or top buyer). */
public final class TopEntry {

    private final UUID player;
    private final String name;
    private final double total;
    private final int count;

    public TopEntry(UUID player, String name, double total, int count) {
        this.player = player;
        this.name = name == null ? "?" : name;
        this.total = total;
        this.count = count;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    public double total() {
        return total;
    }

    public int count() {
        return count;
    }
}
