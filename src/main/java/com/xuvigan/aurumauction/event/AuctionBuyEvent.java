package com.xuvigan.aurumauction.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.xuvigan.aurumauction.model.AuctionLot;

/** Fired after the balance check and before the money is withdrawn. */
public final class AuctionBuyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final AuctionLot lot;
    private boolean cancelled;

    public AuctionBuyEvent(Player buyer, AuctionLot lot) {
        this.buyer = buyer;
        this.lot = lot;
    }

    public Player buyer() {
        return buyer;
    }

    public AuctionLot lot() {
        return lot;
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
