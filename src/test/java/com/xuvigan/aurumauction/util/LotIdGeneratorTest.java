package com.xuvigan.aurumauction.util;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LotIdGeneratorTest {

    @Test
    void idsAreMonotonicAndUnique() {
        LotIdGenerator generator = new LotIdGenerator(1);
        long previous = 0L;
        Set<Long> seen = new HashSet<Long>();
        for (int i = 0; i < 5000; i++) {
            long id = generator.next();
            assertTrue(id > previous, "ids must grow");
            assertTrue(seen.add(Long.valueOf(id)), "ids must be unique");
            previous = id;
        }
        assertTrue(previous > 1_000_000L, "snowflake ids must not collide with legacy 1..N");
    }

    @Test
    void differentServersProduceDifferentIds() {
        LotIdGenerator first = new LotIdGenerator(1);
        LotIdGenerator second = new LotIdGenerator(2);
        assertNotEquals(first.next(), second.next());
    }
}
