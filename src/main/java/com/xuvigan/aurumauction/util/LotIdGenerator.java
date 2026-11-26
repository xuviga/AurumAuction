package com.xuvigan.aurumauction.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Snowflake-like id source for multi-server setups: timestamp + server id +
 * counter. Prevents PRIMARY KEY collisions when several servers write into the
 * same MySQL table without a shared sequence.
 */
public final class LotIdGenerator {

    private static final long EPOCH = 1700000000000L;
    private final long serverId;
    private final AtomicLong last = new AtomicLong();

    public LotIdGenerator(int serverId) {
        this.serverId = Math.max(0L, Math.min(1023L, serverId));
    }

    public long next() {
        synchronized (last) {
            long now = System.currentTimeMillis();
            long id = ((now - EPOCH) << 22) | (serverId << 12);
            long previous = last.get();
            if (id <= previous) {
                id = previous + 1L;
            }
            last.set(id);
            return id;
        }
    }
}
