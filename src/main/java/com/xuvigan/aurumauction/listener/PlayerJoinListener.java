package com.xuvigan.aurumauction.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.xuvigan.aurumauction.AurumAuction;

/** Delivers offline notifications and won items on join. */
public final class PlayerJoinListener implements Listener {

    private final AurumAuction plugin;

    public PlayerJoinListener(AurumAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.notifier().deliver(player);
        if (!plugin.config().autoClaim()) {
            return;
        }
        int claimed = plugin.auction().claimAll(player);
        if (claimed > 0) {
            plugin.messages().send(player, "claims-auto", "count", String.valueOf(claimed));
        }
    }
}
