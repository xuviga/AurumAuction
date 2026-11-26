package com.xuvigan.aurumauction.data;

import java.util.List;
import java.util.UUID;

import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.TopEntry;

/** Per-player transaction history and leaderboards. */
public interface HistoryStore {

    void add(HistoryEntry entry);

    List<HistoryEntry> page(UUID player, int page, int perPage);

    int count(UUID player);

    List<TopEntry> top(HistoryEntry.Role role, int limit);

    void flush();

    void close();
}
