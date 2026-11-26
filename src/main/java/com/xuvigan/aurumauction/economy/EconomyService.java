package com.xuvigan.aurumauction.economy;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import com.xuvigan.aurumauction.util.Numbers;
import com.xuvigan.aurumauction.util.Reflect;

/**
 * Vault bridge through reflection only: the plugin has no compile-time Vault
 * dependency and degrades to "economy unavailable" when Vault is missing.
 */
public final class EconomyService {

    private final Plugin plugin;
    private String requestedMode;
    private String currencyFormat;

    private boolean available;
    private boolean warned;
    private Object economy;
    private Method balanceMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private Method formatMethod;

    public EconomyService(Plugin plugin, String requestedMode, String currencyFormat) {
        this.plugin = plugin;
        configure(requestedMode, currencyFormat);
        setup();
    }

    public void configure(String mode, String format) {
        this.requestedMode = mode == null ? "VAULT" : mode.trim();
        this.currencyFormat = format == null || format.trim().isEmpty() ? Numbers.DEFAULT_PATTERN : format.trim();
    }

    public final void setup() {
        available = false;
        economy = null;
        balanceMethod = null;
        withdrawMethod = null;
        depositMethod = null;
        formatMethod = null;
        warned = false;
        if (!"VAULT".equalsIgnoreCase(requestedMode)) {
            log("economy.mode is not VAULT - auction trading is disabled");
            return;
        }
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration(eco);
            if (registration == null) {
                log("Vault is not installed - auction trading is disabled");
                return;
            }
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            if (provider == null) {
                log("Vault provider is null - auction trading is disabled");
                return;
            }
            Method balance = Reflect.find(eco, "getBalance", OfflinePlayer.class);
            Method withdraw = Reflect.find(eco, "withdrawPlayer", OfflinePlayer.class, double.class);
            Method deposit = Reflect.find(eco, "depositPlayer", OfflinePlayer.class, double.class);
            if (balance == null || withdraw == null || deposit == null) {
                log("Vault economy methods are missing - auction trading is disabled");
                return;
            }
            this.balanceMethod = balance;
            this.withdrawMethod = withdraw;
            this.depositMethod = deposit;
            this.formatMethod = Reflect.find(eco, "format", double.class);
            this.economy = provider;
            this.available = true;
            log("economy mode: VAULT");
        } catch (Throwable throwable) {
            if (throwable instanceof ClassNotFoundException) {
                log("Vault is not installed - auction trading is disabled");
            } else {
                log(throwable.getClass().getSimpleName()
                        + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
            }
        }
    }

    private void log(String message) {
        if (warned || plugin == null) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }

    public boolean available() {
        return available;
    }

    public double balance(OfflinePlayer player) {
        if (!available || player == null) {
            return 0d;
        }
        try {
            Object value = balanceMethod.invoke(economy, player);
            return value instanceof Number ? ((Number) value).doubleValue() : 0d;
        } catch (Throwable throwable) {
            return 0d;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return transact(withdrawMethod, player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return transact(depositMethod, player, amount);
    }

    private boolean transact(Method method, OfflinePlayer player, double amount) {
        if (!available || method == null || player == null || amount <= 0d) {
            return false;
        }
        try {
            Object response = method.invoke(economy, player, Double.valueOf(amount));
            if (response == null) {
                return false;
            }
            Method success = Reflect.find(response.getClass(), "transactionSuccess");
            if (success == null) {
                return true;
            }
            Object result = success.invoke(response);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        } catch (Throwable throwable) {
            if (plugin != null) {
                plugin.getLogger().warning("Vault transaction failed: "
                        + throwable.getClass().getSimpleName());
            }
            return false;
        }
    }

    public String format(double amount) {
        if (available && formatMethod != null) {
            try {
                Object value = formatMethod.invoke(economy, Double.valueOf(amount));
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return (String) value;
                }
            } catch (Throwable ignored) {
                // fall back to the local formatter
            }
        }
        return Numbers.format(amount, currencyFormat);
    }
}
