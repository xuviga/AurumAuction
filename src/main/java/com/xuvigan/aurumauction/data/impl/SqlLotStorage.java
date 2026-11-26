package com.xuvigan.aurumauction.data.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.inventory.ItemStack;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.data.ItemCodec;
import com.xuvigan.aurumauction.data.LotRepository;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;
import com.xuvigan.aurumauction.model.LotType;
import com.xuvigan.aurumauction.util.Items;
import com.xuvigan.aurumauction.util.LotIdGenerator;

/**
 * SQLite/MySQL storage. A single JDBC connection guarded by synchronized
 * methods: simple, dependency-light and plenty for one server. Every write is
 * immediate, so {@link #flush()} is a no-op.
 */
public final class SqlLotStorage implements LotRepository {

    private static final String COLUMNS =
            "id, seller, seller_name, item, search_text, price, buyout_price, created_at, expires_at, "
                    + "closed_at, status, category, lot_type, bidder, bidder_name, bid_count, amount, unit_price";

    private final AurumAuction plugin;
    private final String url;
    private final String user;
    private final String password;
    private final String table;
    private final ItemCodec codec;
    private final LotIdGenerator idGenerator;
    private final AtomicLong nextId = new AtomicLong(1L);

    private Connection connection;

    public SqlLotStorage(AurumAuction plugin, String url, String user, String password, String tablePrefix) {
        this(plugin, url, user, password, tablePrefix, null);
    }

    public SqlLotStorage(AurumAuction plugin, String url, String user, String password, String tablePrefix,
                         ItemCodec codec) {
        this(plugin, url, user, password, tablePrefix, codec, null);
    }

    public SqlLotStorage(AurumAuction plugin, String url, String user, String password, String tablePrefix,
                         ItemCodec codec, LotIdGenerator idGenerator) {
        this.plugin = plugin;
        this.url = url;
        this.user = user;
        this.password = password;
        this.table = sanitizePrefix(tablePrefix) + "lots";
        this.idGenerator = idGenerator;
        this.codec = codec == null ? new ItemCodec() {
            @Override
            public String encode(ItemStack item) {
                return Items.encode(item);
            }

            @Override
            public ItemStack decode(String data) {
                return Items.decode(data);
            }
        } : codec;
        connect();
        createSchema();
        reloadNextId();
    }

    private static String sanitizePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "ah_";
        }
        String cleaned = prefix.trim().replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() ? "ah_" : cleaned;
    }

    private void connect() {
        try {
            if (url.startsWith("jdbc:sqlite:")) {
                Class.forName("org.sqlite.JDBC");
            } else if (url.startsWith("jdbc:mysql:")) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }
            this.connection = user == null || user.isEmpty()
                    ? DriverManager.getConnection(url)
                    : DriverManager.getConnection(url, user, password);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Cannot connect to " + url + ": "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage(), throwable);
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    private void createSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id BIGINT PRIMARY KEY,"
                + "seller VARCHAR(36) NOT NULL,"
                + "seller_name VARCHAR(32) NOT NULL,"
                + "item MEDIUMTEXT NOT NULL,"
                + "search_text VARCHAR(512) NOT NULL,"
                + "price DOUBLE NOT NULL,"
                + "buyout_price DOUBLE NOT NULL DEFAULT 0,"
                + "created_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "closed_at BIGINT NOT NULL DEFAULT 0,"
                + "status VARCHAR(16) NOT NULL,"
                + "category VARCHAR(16) NOT NULL,"
                + "lot_type VARCHAR(16) NOT NULL DEFAULT 'FIXED',"
                + "bidder VARCHAR(36),"
                + "bidder_name VARCHAR(32),"
                + "bid_count INT NOT NULL DEFAULT 0,"
                + "amount INT NOT NULL DEFAULT 1,"
                + "unit_price DOUBLE NOT NULL DEFAULT 0"
                + ")";
        synchronized (this) {
            Statement statement = null;
            try {
                statement = connection().createStatement();
                statement.executeUpdate(ddl);
            } catch (SQLException ex) {
                fail("create table", ex);
                throw new IllegalStateException(ex);
            } finally {
                close(statement);
            }
            createIndex("idx_" + table + "_status_expires", "status, expires_at");
            createIndex("idx_" + table + "_seller_status", "seller, status");
            createIndex("idx_" + table + "_status_closed", "status, closed_at");
            ensureColumn("amount", "INT NOT NULL DEFAULT 1");
            ensureColumn("unit_price", "DOUBLE NOT NULL DEFAULT 0");
            Statement recalc = null;
            try {
                recalc = connection().createStatement();
                recalc.executeUpdate("UPDATE " + table + " SET unit_price = price / "
                        + "CASE WHEN amount < 1 THEN 1 ELSE amount END");
            } catch (SQLException ignored) {
                // recalc is best-effort
            } finally {
                close(recalc);
            }
        }
    }

    private void ensureColumn(String name, String definition) {
        Statement statement = null;
        try {
            statement = connection().createStatement();
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + name + " " + definition);
        } catch (SQLException ignored) {
            // column already exists
        } finally {
            close(statement);
        }
    }

    private void createIndex(String name, String columns) {
        Statement statement = null;
        try {
            statement = connection().createStatement();
            statement.executeUpdate("CREATE INDEX " + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException ignored) {
            // already exists (SQLite has no IF NOT EXISTS for MySQL compatibility here)
        } finally {
            close(statement);
        }
    }

    private void reloadNextId() {
        synchronized (this) {
            PreparedStatement statement = null;
            ResultSet result = null;
            try {
                statement = connection().prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table);
                result = statement.executeQuery();
                if (result.next()) {
                    nextId.set(Math.max(1L, result.getLong(1)));
                }
            } catch (SQLException ex) {
                fail("read next id", ex);
            } finally {
                close(result);
                close(statement);
            }
        }
    }

    @Override
    public synchronized long nextId() {
        return idGenerator != null ? idGenerator.next() : nextId.getAndIncrement();
    }

    @Override
    public synchronized AuctionLot find(long id) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT " + COLUMNS + " FROM " + table + " WHERE id = ?");
            statement.setLong(1, id);
            result = statement.executeQuery();
            return result.next() ? map(result) : null;
        } catch (SQLException ex) {
            fail("find #" + id, ex);
            return null;
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized void save(AuctionLot lot) {
        if (lot == null) {
            return;
        }
        PreparedStatement update = null;
        try {
            update = connection().prepareStatement("UPDATE " + table + " SET seller=?, seller_name=?, item=?, "
                    + "search_text=?, price=?, buyout_price=?, created_at=?, expires_at=?, closed_at=?, status=?, "
                    + "category=?, lot_type=?, bidder=?, bidder_name=?, bid_count=?, amount=?, unit_price=? "
                    + "WHERE id=?");
            bind(update, lot, false);
            int updated = update.executeUpdate();
            if (updated == 0) {
                PreparedStatement insert = null;
                try {
                    insert = connection().prepareStatement("INSERT INTO " + table + " (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                    bind(insert, lot, true);
                    insert.executeUpdate();
                } finally {
                    close(insert);
                }
            }
            long current = nextId.get();
            if (lot.id() >= current) {
                nextId.set(lot.id() + 1L);
            }
        } catch (SQLException ex) {
            fail("save #" + lot.id(), ex);
        } finally {
            close(update);
        }
    }

    private void bind(PreparedStatement statement, AuctionLot lot, boolean withId) throws SQLException {
        int index = 1;
        if (withId) {
            statement.setLong(index++, lot.id());
        }
        statement.setString(index++, lot.seller() == null ? "" : lot.seller().toString());
        statement.setString(index++, lot.sellerName());
        statement.setString(index++, codec.encode(lot.item()));
        statement.setString(index++, lot.searchText());
        statement.setDouble(index++, lot.price());
        statement.setDouble(index++, lot.buyoutPrice());
        statement.setLong(index++, lot.createdAt());
        statement.setLong(index++, lot.expiresAt());
        statement.setLong(index++, lot.closedAt());
        statement.setString(index++, lot.status().name());
        statement.setString(index++, lot.category().name());
        statement.setString(index++, lot.type().name());
        statement.setString(index++, lot.bidder() == null ? null : lot.bidder().toString());
        statement.setString(index++, lot.bidderName());
        statement.setInt(index++, lot.bidCount());
        statement.setInt(index++, lot.amount());
        statement.setDouble(index++, lot.unitPrice());
        if (withId) {
            return;
        }
        statement.setLong(index, lot.id());
    }

    @Override
    public synchronized void delete(long id) {
        PreparedStatement statement = null;
        try {
            statement = connection().prepareStatement("DELETE FROM " + table + " WHERE id = ?");
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            fail("delete #" + id, ex);
        } finally {
            close(statement);
        }
    }

    @Override
    public synchronized List<AuctionLot> search(LotQuery query, int page, int perPage) {
        QueryParts parts = buildWhere(query);
        int limit = Math.max(1, perPage);
        int offset = Math.max(0, (Math.max(1, page) - 1) * limit);
        String order = orderBy(query);
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT " + COLUMNS + " FROM " + table
                    + parts.where + " ORDER BY " + order + " LIMIT ? OFFSET ?");
            int index = bindWhere(statement, parts);
            statement.setInt(index++, limit);
            statement.setInt(index, offset);
            result = statement.executeQuery();
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("search", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized int count(LotQuery query) {
        QueryParts parts = buildWhere(query);
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT COUNT(*) FROM " + table + parts.where);
            bindWhere(statement, parts);
            result = statement.executeQuery();
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException ex) {
            fail("count", ex);
            return 0;
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized List<AuctionLot> bySeller(UUID seller, LotStatus status, int limit) {
        if (seller == null) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM " + table + " WHERE seller = ?");
        if (status != null) {
            sql.append(" AND status = ?");
        }
        sql.append(" ORDER BY created_at DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
        }
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement(sql.toString());
            int index = 1;
            statement.setString(index++, seller.toString());
            if (status != null) {
                statement.setString(index++, status.name());
            }
            if (limit > 0) {
                statement.setInt(index, limit);
            }
            result = statement.executeQuery();
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("bySeller", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized int countBySeller(UUID seller, LotStatus status) {
        if (seller == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE seller = ?"
                + (status == null ? "" : " AND status = ?");
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement(sql);
            statement.setString(1, seller.toString());
            if (status != null) {
                statement.setString(2, status.name());
            }
            result = statement.executeQuery();
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException ex) {
            fail("countBySeller", ex);
            return 0;
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized List<AuctionLot> due(long now) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT " + COLUMNS + " FROM " + table
                    + " WHERE status = ? AND expires_at <= ?");
            statement.setString(1, LotStatus.ACTIVE.name());
            statement.setLong(2, now);
            result = statement.executeQuery();
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("due", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized int purgeClosed(long before) {
        PreparedStatement statement = null;
        try {
            statement = connection().prepareStatement("DELETE FROM " + table
                    + " WHERE status IN (?, ?) AND closed_at > 0 AND closed_at <= ?");
            statement.setString(1, LotStatus.SOLD.name());
            statement.setString(2, LotStatus.CANCELLED.name());
            statement.setLong(3, before);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            fail("purge", ex);
            return 0;
        } finally {
            close(statement);
        }
    }

    @Override
    public synchronized List<AuctionLot> staleActive(long before, int limit) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT " + COLUMNS + " FROM " + table
                    + " WHERE status = ? AND lot_type = ? AND created_at <= ? ORDER BY created_at ASC LIMIT ?");
            statement.setString(1, LotStatus.ACTIVE.name());
            statement.setString(2, LotType.FIXED.name());
            statement.setLong(3, before);
            statement.setInt(4, limit <= 0 ? 200 : limit);
            result = statement.executeQuery();
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("staleActive", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized List<AuctionLot> createdSince(long time, int limit) {
        PreparedStatement statement = null;
        ResultSet result = null;
        try {
            statement = connection().prepareStatement("SELECT " + COLUMNS + " FROM " + table
                    + " WHERE created_at > ? ORDER BY created_at ASC LIMIT ?");
            statement.setLong(1, time);
            statement.setInt(2, limit <= 0 ? 200 : limit);
            result = statement.executeQuery();
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("createdSince", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public synchronized boolean transition(long id, LotStatus expected, LotStatus next) {
        PreparedStatement statement = null;
        try {
            long closed = next == LotStatus.SOLD || next == LotStatus.CANCELLED
                    ? System.currentTimeMillis() : 0L;
            statement = connection().prepareStatement("UPDATE " + table
                    + " SET status = ?, closed_at = ? WHERE id = ? AND status = ?");
            statement.setString(1, next.name());
            statement.setLong(2, closed);
            statement.setLong(3, id);
            statement.setString(4, expected.name());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            fail("transition #" + id, ex);
            return false;
        } finally {
            close(statement);
        }
    }

    @Override
    public synchronized boolean compareAndSet(AuctionLot expected, AuctionLot updated) {
        if (expected == null || updated == null) {
            return false;
        }
        PreparedStatement statement = null;
        try {
            statement = connection().prepareStatement("UPDATE " + table
                    + " SET item = ?, search_text = ?, amount = ?, unit_price = ?, price = ?, bidder = ?, "
                    + "bidder_name = ?, bid_count = ?, expires_at = ?, status = ?, closed_at = ? "
                    + "WHERE id = ? AND status = ? AND price = ? AND bid_count = ?");
            statement.setString(1, codec.encode(updated.item()));
            statement.setString(2, updated.searchText());
            statement.setInt(3, updated.amount());
            statement.setDouble(4, updated.unitPrice());
            statement.setDouble(5, updated.price());
            statement.setString(6, updated.bidder() == null ? null : updated.bidder().toString());
            statement.setString(7, updated.bidderName());
            statement.setInt(8, updated.bidCount());
            statement.setLong(9, updated.expiresAt());
            statement.setString(10, updated.status().name());
            statement.setLong(11, updated.closedAt());
            statement.setLong(12, expected.id());
            statement.setString(13, expected.status().name());
            statement.setDouble(14, expected.price());
            statement.setInt(15, expected.bidCount());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            fail("compareAndSet #" + expected.id(), ex);
            return false;
        } finally {
            close(statement);
        }
    }

    @Override
    public synchronized Collection<AuctionLot> all() {
        Statement statement = null;
        ResultSet result = null;
        try {
            statement = connection().createStatement();
            result = statement.executeQuery("SELECT " + COLUMNS + " FROM " + table);
            List<AuctionLot> out = new ArrayList<AuctionLot>();
            while (result.next()) {
                AuctionLot lot = map(result);
                if (lot != null) {
                    out.add(lot);
                }
            }
            return out;
        } catch (SQLException ex) {
            fail("all", ex);
            return Collections.emptyList();
        } finally {
            close(result);
            close(statement);
        }
    }

    @Override
    public void flush() {
        // writes are immediate
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // shutting down
        } finally {
            connection = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final class QueryParts {

        private final String where;
        private final List<Object> params;

        private QueryParts(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }

    private QueryParts buildWhere(LotQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<Object>();
        if (query != null) {
            if (query.status() != null) {
                where.append(" AND status = ?");
                params.add(query.status().name());
            }
            if (query.seller() != null) {
                where.append(" AND seller = ?");
                params.add(query.seller().toString());
            }
            if (query.category() != null) {
                where.append(" AND category = ?");
                params.add(query.category().name());
            }
            if (query.text() != null && !query.text().isEmpty()) {
                where.append(" AND search_text LIKE ?");
                params.add("%" + query.text() + "%");
            }
        }
        return new QueryParts(where.toString(), params);
    }

    private int bindWhere(PreparedStatement statement, QueryParts parts) throws SQLException {
        int index = 1;
        for (Object value : parts.params) {
            statement.setObject(index++, value);
        }
        return index;
    }

    private static String orderBy(LotQuery query) {
        LotQuery.Sort sort = query == null ? LotQuery.Sort.NEWEST : query.sort();
        switch (sort) {
            case CHEAPEST:
                return "price ASC, id DESC";
            case EXPENSIVE:
                return "price DESC, id DESC";
            case UNIT_CHEAPEST:
                return "unit_price ASC, id DESC";
            case UNIT_EXPENSIVE:
                return "unit_price DESC, id DESC";
            case NAME:
                return "search_text ASC, id ASC";
            case NEWEST:
            default:
                return "created_at DESC, id DESC";
        }
    }

    private AuctionLot map(ResultSet result) throws SQLException {
        ItemStack item = codec.decode(result.getString("item"));
        if (item == null) {
            return null;
        }
        UUID seller = parseUuid(result.getString("seller"));
        if (seller == null) {
            return null;
        }
        return new AuctionLot(
                result.getLong("id"),
                seller,
                result.getString("seller_name"),
                item,
                result.getDouble("price"),
                result.getLong("created_at"),
                result.getLong("expires_at"),
                LotStatus.parse(result.getString("status")),
                parseCategory(result.getString("category")),
                result.getLong("closed_at"),
                LotType.parse(result.getString("lot_type")),
                result.getDouble("buyout_price"),
                parseUuid(result.getString("bidder")),
                result.getString("bidder_name"),
                result.getInt("bid_count"));
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LotCategory parseCategory(String value) {
        if (value == null) {
            return LotCategory.OTHER;
        }
        try {
            return LotCategory.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LotCategory.OTHER;
        }
    }

    private void fail(String action, SQLException ex) {
        if (plugin != null) {
            plugin.getLogger().warning("SQL " + action + " failed: " + ex.getMessage());
        }
    }

    private static void close(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ignored) {
                // ignore
            }
        }
    }

    private static void close(ResultSet result) {
        if (result != null) {
            try {
                result.close();
            } catch (SQLException ignored) {
                // ignore
            }
        }
    }
}
