package com.xuvigan.aurumauction.util;

import java.lang.reflect.Method;

/** Reflection helpers used by the Vault bridge. */
public final class Reflect {

    private Reflect() {
    }

    /**
     * Exact parameter-type lookup first, then a scan for assignable signatures.
     * Vault declares both {@code (OfflinePlayer, double)} and the legacy
     * {@code (String, double)} overloads, so a plain name+arity lookup can pick
     * the wrong one and fail with IllegalArgumentException at runtime.
     */
    public static Method find(Class<?> type, String name, Class<?>... params) {
        if (type == null || name == null) {
            return null;
        }
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            // fall through to the assignable scan
        }
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().equals(name) || method.getParameterTypes().length != params.length) {
                continue;
            }
            boolean matches = true;
            for (int k = 0; k < params.length; k++) {
                if (!method.getParameterTypes()[k].isAssignableFrom(params[k])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }
}
