package com.xuvigan.aurumauction.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class ReflectTest {

    @SuppressWarnings("unused")
    static final class VaultLike {
        public String withdrawPlayer(String name, double amount) {
            return "string overload";
        }

        public String withdrawPlayer(OfflinePlayer player, double amount) {
            return "player overload";
        }

        public String getBalance(OfflinePlayer player) {
            return "balance";
        }

        public String format(double amount) {
            return "format";
        }
    }

    @SuppressWarnings("unused")
    static final class Assignable {
        public String ping(Object target) {
            return "object";
        }
    }

    @Test
    void prefersOfflinePlayerOverloadOverString() throws Exception {
        Method method = Reflect.find(VaultLike.class, "withdrawPlayer", OfflinePlayer.class, double.class);
        assertNotNull(method);
        assertEquals(OfflinePlayer.class, method.getParameterTypes()[0]);
        assertEquals(double.class, method.getParameterTypes()[1]);
    }

    @Test
    void findsPrimitiveAndZeroArgSignatures() {
        assertNotNull(Reflect.find(VaultLike.class, "format", double.class));
        assertNotNull(Reflect.find(VaultLike.class, "getBalance", OfflinePlayer.class));
    }

    @Test
    void fallsBackToAssignableParameters() {
        Method method = Reflect.find(Assignable.class, "ping", OfflinePlayer.class);
        assertNotNull(method);
    }

    @Test
    void returnsNullForUnknownMethod() {
        assertNull(Reflect.find(VaultLike.class, "missing"));
        assertNull(Reflect.find(VaultLike.class, "withdrawPlayer"));
    }
}
