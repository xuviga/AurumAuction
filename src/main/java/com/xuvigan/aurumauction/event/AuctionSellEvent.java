package com.xuvigan.aurumauction.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired before an item is removed from the player's hand and listed. */
public final class AuctionSellEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack item;
    private final double price;
    private boolean cancelled;

    public AuctionSellEvent(Player player, ItemStack item, double price) {
        this.player = player;
        this.item = item;
        this.price = price;
    }

    public Player player() {
        return player;
    }

    public ItemStack item() {
        return item;
    }

    public double price() {
        return price;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
