package com.xuvigan.aurumauction.service;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.xuvigan.aurumauction.AurumAuction;

/**
 * Sends a message now when the player is online, otherwise stores it in
 * notifications.yml for the next join.
 */
public final class Notifier {

    private final AurumAuction plugin;

    public Notifier(AurumAuction plugin) {
        this.plugin = plugin;
    }

    public void notify(UUID player, String key, Object... pairs) {
        if (player == null || !plugin.config().notificationsEnabled()) {
            return;
        }
        String text = plugin.messages().message(key, pairs);
        if (text == null || text.isEmpty()) {
            return;
        }
        Player online = Bukkit.getPlayer(player);
        if (online != null && online.isOnline()) {
            final Player target = online;
            final String message = text;
            plugin.scheduler().runFor(target, new Runnable() {
                @Override
                public void run() {
                    target.sendMessage(message);
                }
            });
            return;
        }
        plugin.notifications().add(player, text);
    }

    public void deliver(Player player) {
        if (player == null) {
            return;
        }
        List<String> pending = plugin.notifications().take(player.getUniqueId());
        for (String line : pending) {
            player.sendMessage(line);
        }
    }
}
