package com.xuvigan.aurumauction.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.xuvigan.aurumauction.AurumAuction;

/**
 * Routes every click on an AurumAuction inventory. Identification is done via
 * the {@link GuiHolder} stored in the inventory, never by its title.
 */
public final class GuiListener implements Listener {

    private final AurumAuction plugin;

    public GuiListener(AurumAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        GuiHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        if (holder.menu() == MenuType.SELL) {
            int topSize = event.getView().getTopInventory().getSize();
            int slot = event.getRawSlot();
            if (slot >= topSize) {
                return; // player inventory stays interactive
            }
            int storage = plugin.guiConfig().sellStorageSlots();
            if (slot < storage) {
                return; // storage slots accept items
            }
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player) {
                plugin.gui().click((Player) event.getWhoClicked(), holder, slot, event.getClick());
            }
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        try {
            plugin.gui().click(player, holder, slot, event.getClick());
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Menu click failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        GuiHolder holder = holderOf(event.getInventory());
        if (holder == null || holder.menu() != MenuType.SELL) {
            return;
        }
        if (event.getPlayer() instanceof Player) {
            plugin.gui().returnSellItems((Player) event.getPlayer(), holder.getInventory());
        }
    }

    static GuiHolder holderOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        try {
            org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
            return holder instanceof GuiHolder ? (GuiHolder) holder : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
