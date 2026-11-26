package com.xuvigan.aurumauction.model;

/** Fixed-price lot or bidding lot. */
public enum LotType {

    FIXED,
    BID;

    public static LotType parse(String value) {
        if (value == null) {
            return FIXED;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FIXED;
        }
    }
}
