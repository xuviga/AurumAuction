package com.xuvigan.aurumauction.util;

import java.util.UUID;

import org.bukkit.entity.Player;

/** Optional Floodgate/Geyser detection, soft and reflection-only. */
public final class Bedrock {

    private Bedrock() {
    }

    public static boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object result = api.getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(instance, player.getUniqueId());
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
