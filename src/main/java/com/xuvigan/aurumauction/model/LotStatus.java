package com.xuvigan.aurumauction.model;

/** Lifecycle of an auction lot. */
public enum LotStatus {

    ACTIVE,
    SOLD,
    EXPIRED,
    CANCELLED;

    public static LotStatus parse(String value) {
        if (value == null) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
