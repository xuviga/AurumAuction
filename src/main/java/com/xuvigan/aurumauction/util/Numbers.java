package com.xuvigan.aurumauction.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number helpers for money: formatting with a configurable pattern, short
 * Russian suffixes (k / kk / kkk) and parsing of player input such as
 * {@code 100kk}, {@code 1.5kkk} or {@code 2 000}.
 */
public final class Numbers {

    public static final String DEFAULT_PATTERN = "#,##0.##";

    private Numbers() {
    }

    public static String format(double value, String pattern) {
        String safe = pattern == null || pattern.trim().isEmpty() ? DEFAULT_PATTERN : pattern.trim();
        DecimalFormat formatter;
        try {
            formatter = new DecimalFormat(safe, DecimalFormatSymbols.getInstance(Locale.US));
        } catch (IllegalArgumentException ex) {
            formatter = new DecimalFormat(DEFAULT_PATTERN, DecimalFormatSymbols.getInstance(Locale.US));
        }
        return formatter.format(value);
    }

    /** Compact form: 1 500 000 -> 1.5kk, 15 000 -> 15k. */
    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1000000000d) {
            return trim(value / 1000000000d) + "kkk";
        }
        if (abs >= 1000000d) {
            return trim(value / 1000000d) + "kk";
        }
        if (abs >= 1000d) {
            return trim(value / 1000d) + "k";
        }
        return trim(value);
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 9.0E15) {
            return String.valueOf((long) value);
        }
        DecimalFormat formatter = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(value);
    }

    /**
     * Parses a player supplied price. Supports {@code k}/{@code kk}/{@code kkk}
     * (also Cyrillic {@code к}/{@code кк}/{@code ккк}), spaces, NBSP and commas.
     * Returns {@code fallback} for empty or malformed input.
     */
    public static double parse(String input, double fallback) {
        if (input == null) {
            return fallback;
        }
        String cleaned = input.trim()
                .replace(" ", "")
                .replace("\u00A0", "")
                .replace(",", ".")
                .toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            return fallback;
        }
        double multiplier = 1.0d;
        String suffix = null;
        if (cleaned.endsWith("kkk") || cleaned.endsWith("ккк")) {
            multiplier = 1000000000d;
            suffix = cleaned.substring(cleaned.length() - 3);
        } else if (cleaned.endsWith("kk") || cleaned.endsWith("кк")) {
            multiplier = 1000000d;
            suffix = cleaned.substring(cleaned.length() - 2);
        } else if (cleaned.endsWith("k") || cleaned.endsWith("к")) {
            multiplier = 1000d;
            suffix = cleaned.substring(cleaned.length() - 1);
        }
        String numeric = suffix == null ? cleaned : cleaned.substring(0, cleaned.length() - suffix.length());
        if (numeric.isEmpty()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(numeric);
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                return fallback;
            }
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int parseInt(String input, int fallback) {
        double value = parse(input, fallback);
        if (value == fallback && fallback != 0) {
            return fallback;
        }
        return (int) Math.round(value);
    }

    /** 45 000 ms -> "12ч 30м"; 90 000 ms -> "1д 1ч". */
    public static String duration(long millis) {
        if (millis <= 0) {
            return "0м";
        }
        long seconds = millis / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("д ");
        }
        if (hours > 0) {
            out.append(hours).append("ч ");
        }
        if (minutes > 0 || out.length() == 0) {
            out.append(minutes).append("м");
        }
        return out.toString().trim();
    }
}
