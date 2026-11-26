package com.xuvigan.aurumauction.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small per-player cooldown gate (GUI clicks, commands). */
public final class Cooldowns {

    private final Map<String, Long> last = new ConcurrentHashMap<String, Long>();

    public boolean allow(UUID player, String key, long millis) {
        if (player == null || millis <= 0L) {
            return true;
        }
        String id = player.toString() + ':' + key;
        long now = System.currentTimeMillis();
        Long previous = last.get(id);
        if (previous != null && now - previous.longValue() < millis) {
            return false;
        }
        last.put(id, Long.valueOf(now));
        return true;
    }

    public void clear(UUID player) {
        if (player == null) {
            return;
        }
        String prefix = player.toString() + ':';
        last.keySet().removeIf(new java.util.function.Predicate<String>() {
            @Override
            public boolean test(String value) {
                return value.startsWith(prefix);
            }
        });
    }
}
