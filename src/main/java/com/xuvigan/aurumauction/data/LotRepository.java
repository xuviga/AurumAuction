package com.xuvigan.aurumauction.data;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;

/**
 * Storage abstraction. 0.1.x ships a YAML implementation; SQLite/MySQL will
 * implement the same contract in 0.2.0 without touching the services.
 */
public interface LotRepository {

    long nextId();

    AuctionLot find(long id);

    void save(AuctionLot lot);

    void delete(long id);

    List<AuctionLot> search(LotQuery query, int page, int perPage);

    int count(LotQuery query);

    List<AuctionLot> bySeller(UUID seller, LotStatus status, int limit);

    int countBySeller(UUID seller, LotStatus status);

    /** Active lots whose expiry timestamp is at or before {@code now}. */
    List<AuctionLot> due(long now);

    /**
     * Deletes SOLD/CANCELLED lots closed before {@code before} (retention policy).
     * EXPIRED lots are never purged: their items still belong to the seller.
     */
    int purgeClosed(long before);

    /** Active fixed-price lots created before {@code before} (auto-buyout). */
    List<AuctionLot> staleActive(long before, int limit);

    /** Lots created after {@code time} (cross-server watcher sync). */
    List<AuctionLot> createdSince(long time, int limit);

    /** Atomically changes status when the current status equals {@code expected}. */
    boolean transition(long id, LotStatus expected, LotStatus next);

    /** Compare-and-set on status/price/bid count (bids across servers). */
    boolean compareAndSet(AuctionLot expected, AuctionLot updated);

    Collection<AuctionLot> all();

    void flush();

    void close();
}
