package com.xuvigan.aurumauction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void parsesRussianShorthand() {
        assertEquals(100_000_000d, Numbers.parse("100kk", -1d), 0.001d);
        assertEquals(1_500_000_000d, Numbers.parse("1.5kkk", -1d), 0.001d);
        assertEquals(1_500d, Numbers.parse("1.5k", -1d), 0.001d);
        assertEquals(10_000_000d, Numbers.parse("10кк", -1d), 0.001d);
    }

    @Test
    void parsesPlainValuesWithSeparators() {
        assertEquals(2000d, Numbers.parse("2 000", -1d), 0.001d);
        assertEquals(2000d, Numbers.parse("2\u00A0000", -1d), 0.001d);
        assertEquals(1500d, Numbers.parse("1,5k", -1d), 0.001d);
        assertEquals(42d, Numbers.parse("42", -1d), 0.001d);
    }

    @Test
    void rejectsMalformedInput() {
        assertEquals(-1d, Numbers.parse(null, -1d), 0.001d);
        assertEquals(-1d, Numbers.parse("", -1d), 0.001d);
        assertEquals(-1d, Numbers.parse("abc", -1d), 0.001d);
        assertEquals(-1d, Numbers.parse("kk", -1d), 0.001d);
        assertEquals(-1d, Numbers.parse("-5k", -1d), 0.001d);
    }

    @Test
    void compactsLargeValues() {
        assertEquals("999", Numbers.compact(999d));
        assertEquals("1k", Numbers.compact(1000d));
        assertEquals("1.5k", Numbers.compact(1500d));
        assertEquals("1kk", Numbers.compact(1_000_000d));
        assertEquals("1.5kk", Numbers.compact(1_500_000d));
        assertEquals("2kkk", Numbers.compact(2_000_000_000d));
    }

    @Test
    void formatsDuration() {
        assertEquals("0м", Numbers.duration(0L));
        assertEquals("1м", Numbers.duration(90_000L));
        assertEquals("1ч", Numbers.duration(3_600_000L));
        assertTrue(Numbers.duration(90_000_000L).contains("д"));
    }
}
