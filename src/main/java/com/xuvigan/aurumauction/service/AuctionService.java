package com.xuvigan.aurumauction.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.config.ConfigManager;
import com.xuvigan.aurumauction.data.ClaimQueue;
import com.xuvigan.aurumauction.data.HistoryStore;
import com.xuvigan.aurumauction.data.LotRepository;
import com.xuvigan.aurumauction.data.PayoutQueue;
import com.xuvigan.aurumauction.data.TransactionLog;
import com.xuvigan.aurumauction.data.WatchStore;
import com.xuvigan.aurumauction.economy.EconomyService;
import com.xuvigan.aurumauction.event.AuctionBuyEvent;
import com.xuvigan.aurumauction.event.AuctionSellEvent;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;
import com.xuvigan.aurumauction.model.LotType;
import com.xuvigan.aurumauction.util.Items;

/** All auction operations: list, buy, bid, cancel, claim, expire, auto-buyout. */
public final class AuctionService {

    public enum Code {
        OK,
        NO_ECONOMY,
        EMPTY_HAND,
        BLOCKED,
        SHULKERS_DISABLED,
        PRICE_LOW,
        PRICE_HIGH,
        LIMIT_REACHED,
        INVALID_AMOUNT,
        CANCELLED,
        NOT_FOUND,
        NOT_ACTIVE,
        EXPIRED,
        OWN_LOT,
        NO_MONEY,
        NOT_OWNER,
        BIDS_DISABLED,
        NOT_BID,
        BID_TOO_LOW,
        ALREADY_TOP,
        NOT_BUYABLE,
        BUYOUT_TOO_LOW,
        SERVER_LIMIT
    }

    public static final class Result {

        private final Code code;
        private final AuctionLot lot;
        private final double paid;
        private final double tax;
        private final boolean dropped;
        private final double minBid;

        private Result(Code code, AuctionLot lot, double paid, double tax, boolean dropped, double minBid) {
            this.code = code;
            this.lot = lot;
            this.paid = paid;
            this.tax = tax;
            this.dropped = dropped;
            this.minBid = minBid;
        }

        static Result fail(Code code) {
            return new Result(code, null, 0d, 0d, false, 0d);
        }

        static Result fail(Code code, double minBid) {
            return new Result(code, null, 0d, 0d, false, minBid);
        }

        static Result ok(AuctionLot lot, double paid, double tax, boolean dropped) {
            return new Result(Code.OK, lot, paid, tax, dropped, 0d);
        }

        public Code code() {
            return code;
        }

        public AuctionLot lot() {
            return lot;
        }

        public double paid() {
            return paid;
        }

        public double tax() {
            return tax;
        }

        public boolean dropped() {
            return dropped;
        }

        public double minBid() {
            return minBid;
        }

        public boolean success() {
            return code == Code.OK;
        }
    }

    private final AurumAuction plugin;
    private final LotRepository lots;
    private final EconomyService economy;
    private final PayoutQueue payouts;
    private final ClaimQueue claims;
    private final Notifier notifier;
    private final HistoryStore history;
    private final WatchStore watches;
    private final TransactionLog transactionLog;
    private final ConfigManager config;

    public AuctionService(AurumAuction plugin, LotRepository lots, EconomyService economy, PayoutQueue payouts,
                          ClaimQueue claims, Notifier notifier, HistoryStore history, WatchStore watches,
                          TransactionLog transactionLog, ConfigManager config) {
        this.plugin = plugin;
        this.lots = lots;
        this.economy = economy;
        this.payouts = payouts;
        this.claims = claims;
        this.notifier = notifier;
        this.history = history;
        this.watches = watches;
        this.transactionLog = transactionLog;
        this.config = config;
    }

    // -------------------------------------------------------------------- sell

    public Result sell(Player player, double price, int amount) {
        return createLot(player, price, amount, LotType.FIXED, 0, 0d);
    }

    /** Lists a bidding lot: {@code startPrice}, duration in hours and optional buyout. */
    public Result sellAuction(Player player, double startPrice, int hours, double buyout) {
        if (!config.bidsEnabled()) {
            return Result.fail(Code.BIDS_DISABLED);
        }
        int duration = hours <= 0 ? config.bidDefaultHours() : Math.min(hours, config.bidMaxHours());
        if (buyout > 0d && buyout <= startPrice) {
            return Result.fail(Code.BUYOUT_TOO_LOW);
        }
        return createLot(player, startPrice, -1, LotType.BID, duration, buyout);
    }

    private Result createLot(Player player, double price, int amount, LotType type, int hours, double buyout) {
        return createLot(player, price, amount, type, hours, buyout,
                player.getInventory().getItemInMainHand(), true);
    }

    /** Lists a stack taken from a shop/sell menu (the caller removes it from the container). */
    public Result sellItem(Player player, ItemStack item, double price) {
        if (item == null || item.getType() == Material.AIR) {
            return Result.fail(Code.EMPTY_HAND);
        }
        return createLot(player, price, item.getAmount(), LotType.FIXED, 0, 0d, item, false);
    }

    private Result createLot(Player player, double price, int amount, LotType type, int hours, double buyout,
                             ItemStack source, boolean removeFromHand) {
        if (!economy.available()) {
            return Result.fail(Code.NO_ECONOMY);
        }
        if (source == null || source.getType() == Material.AIR) {
            return Result.fail(Code.EMPTY_HAND);
        }
        int available = Math.min(source.getAmount(), config.maxAmountPerLot());
        int sellAmount = amount <= 0 ? available : amount;
        if (sellAmount <= 0 || sellAmount > available) {
            return Result.fail(Code.INVALID_AMOUNT);
        }
        if (config.isBlocked(source.getType()) && !player.hasPermission("auction.bypass.blocked")) {
            return Result.fail(Code.BLOCKED);
        }
        if (!config.allowShulkers() && Items.isShulker(source)) {
            return Result.fail(Code.SHULKERS_DISABLED);
        }
        if (price < config.minPrice()) {
            return Result.fail(Code.PRICE_LOW);
        }
        if (price > config.maxPrice()) {
            return Result.fail(Code.PRICE_HIGH);
        }
        int limit = limit(player);
        if (lots.countBySeller(player.getUniqueId(), LotStatus.ACTIVE) >= limit) {
            return Result.fail(Code.LIMIT_REACHED);
        }
        if (config.maxTotalLots() > 0 && lots.count(LotQuery.active()) >= config.maxTotalLots()) {
            return Result.fail(Code.SERVER_LIMIT);
        }
        ItemStack snapshot = source.clone();
        snapshot.setAmount(sellAmount);
        AuctionSellEvent event = new AuctionSellEvent(player, snapshot, price);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.fail(Code.CANCELLED);
        }
        if (removeFromHand) {
            if (source.getAmount() <= sellAmount) {
                player.getInventory().setItemInMainHand(null);
            } else {
                source.setAmount(source.getAmount() - sellAmount);
                player.getInventory().setItemInMainHand(source);
            }
        }
        long now = System.currentTimeMillis();
        long lifetime = type == LotType.BID
                ? Math.max(1, hours) * 3600_000L
                : config.expireHours() * 3600_000L;
        AuctionLot lot = new AuctionLot(
                lots.nextId(),
                player.getUniqueId(),
                player.getName(),
                snapshot,
                price,
                now,
                now + lifetime,
                LotStatus.ACTIVE,
                LotCategory.of(snapshot.getType()),
                0L,
                type,
                buyout,
                null,
                null,
                0);
        lots.save(lot);
        if (!config.multiServerEnabled()) {
            // in multi-server mode the poller announces lots from every node
            notifyWatchers(lot, player.getUniqueId());
        }
        return Result.ok(lot, price, 0d, false);
    }

    // --------------------------------------------------------------------- buy

    public Result buy(Player buyer, long lotId) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        if (lot.status() != LotStatus.ACTIVE) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        long now = System.currentTimeMillis();
        if (lot.isExpired(now)) {
            settleExpired(lot);
            return Result.fail(Code.EXPIRED);
        }
        if (lot.seller() != null && lot.seller().equals(buyer.getUniqueId())) {
            return Result.fail(Code.OWN_LOT);
        }
        if (lot.isBid() && !lot.hasBuyout()) {
            return Result.fail(Code.NOT_BUYABLE);
        }
        double price = effectivePrice(lot);
        if (!economy.available()) {
            return Result.fail(Code.NO_ECONOMY);
        }
        if (economy.balance(buyer) < price) {
            return Result.fail(Code.NO_MONEY);
        }
        AuctionBuyEvent event = new AuctionBuyEvent(buyer, lot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.fail(Code.CANCELLED);
        }
        if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.SOLD)) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        if (!economy.withdraw(buyer, price)) {
            lots.save(lot);
            return Result.fail(Code.NO_MONEY);
        }
        refundBidder(lot);
        boolean dropped = deliver(buyer, lot.item());
        double tax = taxOf(lot, price);
        double payout = Math.max(0d, price - tax);
        paySeller(lot, payout, price);
        notifier.notify(lot.seller(), "notify-sold",
                "item", Items.displayName(lot.item()),
                "amount", String.valueOf(lot.amount()),
                "price", economy.format(payout),
                "buyer", buyer.getName());
        record(buyer.getUniqueId(), buyer.getName(), HistoryEntry.Role.BUYER, lot, price, 0d, lot.sellerName());
        record(lot.seller(), lot.sellerName(), HistoryEntry.Role.SELLER, lot, payout, tax, buyer.getName());
        transactionLog.log("BUY", lot, buyer.getName(), lot.sellerName(), price, tax);
        return Result.ok(lot.withStatus(LotStatus.SOLD), payout, tax, dropped);
    }

    /** Buys a part of a fixed-price stack. */
    public Result buyPartial(Player buyer, long lotId, int amount) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        if (lot.status() != LotStatus.ACTIVE) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        long now = System.currentTimeMillis();
        if (lot.isExpired(now)) {
            settleExpired(lot);
            return Result.fail(Code.EXPIRED);
        }
        if (lot.seller() != null && lot.seller().equals(buyer.getUniqueId())) {
            return Result.fail(Code.OWN_LOT);
        }
        if (lot.isBid()) {
            return Result.fail(Code.NOT_BUYABLE);
        }
        if (amount <= 0) {
            return Result.fail(Code.INVALID_AMOUNT);
        }
        if (amount >= lot.amount()) {
            return buy(buyer, lotId);
        }
        if (!economy.available()) {
            return Result.fail(Code.NO_ECONOMY);
        }
        double partPrice = Math.max(0.01d, round2(lot.unitPrice() * amount));
        if (economy.balance(buyer) < partPrice) {
            return Result.fail(Code.NO_MONEY);
        }
        AuctionBuyEvent event = new AuctionBuyEvent(buyer, lot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.fail(Code.CANCELLED);
        }
        ItemStack part = lot.item().clone();
        part.setAmount(amount);
        ItemStack rest = lot.item().clone();
        rest.setAmount(lot.amount() - amount);
        AuctionLot remainder = new AuctionLot(lot.id(), lot.seller(), lot.sellerName(), rest,
                Math.max(0.01d, round2(lot.price() - partPrice)), lot.createdAt(), lot.expiresAt(),
                LotStatus.ACTIVE, lot.category(), 0L);
        AuctionLot sold = new AuctionLot(lot.id(), lot.seller(), lot.sellerName(), part, partPrice,
                lot.createdAt(), lot.expiresAt(), LotStatus.SOLD, lot.category(), now);
        if (!lots.compareAndSet(lot, remainder)) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        if (!economy.withdraw(buyer, partPrice)) {
            if (!lots.compareAndSet(remainder, lot)) {
                plugin.getLogger().severe("Partial buy revert failed for lot #" + lot.id());
            }
            return Result.fail(Code.NO_MONEY);
        }
        boolean dropped = deliver(buyer, part);
        double tax = taxOf(lot, partPrice);
        double payout = Math.max(0d, partPrice - tax);
        paySeller(lot, payout, partPrice);
        notifier.notify(lot.seller(), "notify-sold",
                "item", Items.displayName(part),
                "amount", String.valueOf(amount),
                "price", economy.format(payout),
                "buyer", buyer.getName());
        record(buyer.getUniqueId(), buyer.getName(), HistoryEntry.Role.BUYER, sold, partPrice, 0d, lot.sellerName());
        record(lot.seller(), lot.sellerName(), HistoryEntry.Role.SELLER, sold, payout, tax, buyer.getName());
        transactionLog.log("BUY_PART", sold, buyer.getName(), lot.sellerName(), partPrice, tax);
        return Result.ok(sold, payout, tax, dropped);
    }

    // --------------------------------------------------------------------- bid

    public Result bid(Player bidder, long lotId, double amount) {
        if (!config.bidsEnabled()) {
            return Result.fail(Code.BIDS_DISABLED);
        }
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        if (!lot.isBid()) {
            return Result.fail(Code.NOT_BID);
        }
        if (lot.status() != LotStatus.ACTIVE) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        long now = System.currentTimeMillis();
        if (lot.isExpired(now)) {
            settleExpired(lot);
            return Result.fail(Code.EXPIRED);
        }
        if (lot.seller() != null && lot.seller().equals(bidder.getUniqueId())) {
            return Result.fail(Code.OWN_LOT);
        }
        if (lot.bidder() != null && lot.bidder().equals(bidder.getUniqueId())) {
            return Result.fail(Code.ALREADY_TOP);
        }
        if (!economy.available()) {
            return Result.fail(Code.NO_ECONOMY);
        }
        if (lot.hasBuyout() && amount >= lot.buyoutPrice()) {
            return buy(bidder, lotId);
        }
        double min = minNextBid(lot);
        if (amount < min) {
            return Result.fail(Code.BID_TOO_LOW, min);
        }
        if (economy.balance(bidder) < amount) {
            return Result.fail(Code.NO_MONEY);
        }
        if (!economy.withdraw(bidder, amount)) {
            return Result.fail(Code.NO_MONEY);
        }
        long extendMs = config.bidExtendSeconds() * 1000L;
        long expiresAt = lot.expiresAt();
        if (extendMs > 0L && lot.remainingMillis(now) < extendMs) {
            expiresAt = now + extendMs;
        }
        AuctionLot updated = lot.withBid(amount, bidder.getUniqueId(), bidder.getName(), lot.bidCount() + 1)
                .withExpiry(expiresAt);
        if (!lots.compareAndSet(lot, updated)) {
            refund(bidder.getUniqueId(), bidder.getName(), amount, lot.id());
            return Result.fail(Code.NOT_ACTIVE);
        }
        refundBidder(lot);
        if (lot.bidder() != null && !lot.bidder().equals(bidder.getUniqueId())) {
            notifier.notify(lot.bidder(), "notify-outbid",
                    "item", Items.displayName(lot.item()),
                    "price", economy.format(amount));
        }
        notifier.notify(lot.seller(), "notify-bid",
                "item", Items.displayName(lot.item()),
                "price", economy.format(amount),
                "bidder", bidder.getName());
        transactionLog.log("BID", updated, bidder.getName(), lot.sellerName(), amount, 0d);
        return Result.ok(updated, amount, 0d, false);
    }

    public double effectivePrice(AuctionLot lot) {
        return lot.isBid() && lot.hasBuyout() ? lot.buyoutPrice() : lot.price();
    }

    public double minNextBid(AuctionLot lot) {
        if (!lot.hasBids()) {
            return lot.price();
        }
        double step = Math.max(config.bidMinIncrement(), lot.price() * config.bidMinIncrementPercent() / 100d);
        return round2(lot.price() + step);
    }

    // ------------------------------------------------------------------ cancel

    public Result cancel(Player player, long lotId) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        if (lot.seller() == null || !lot.seller().equals(player.getUniqueId())) {
            return Result.fail(Code.NOT_OWNER);
        }
        if (lot.status() != LotStatus.ACTIVE) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.CANCELLED)) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        refundBidder(lot);
        boolean dropped = deliver(player, lot.item());
        return Result.ok(lot.withStatus(LotStatus.CANCELLED), 0d, 0d, dropped);
    }

    // ------------------------------------------------------------------- claim

    /** Returns an expired lot to its owner. */
    public Result claim(Player player, long lotId) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return Result.fail(Code.NOT_FOUND);
        }
        if (lot.seller() == null || !lot.seller().equals(player.getUniqueId())) {
            return Result.fail(Code.NOT_OWNER);
        }
        if (lot.status() != LotStatus.EXPIRED) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        if (!lots.transition(lot.id(), LotStatus.EXPIRED, LotStatus.CANCELLED)) {
            return Result.fail(Code.NOT_ACTIVE);
        }
        boolean dropped = deliver(player, lot.item());
        return Result.ok(lot, 0d, 0d, dropped);
    }

    /** Delivers every queued won item; returns how many were handed over. */
    public int claimAll(Player player) {
        List<ClaimQueue.Claim> pending = claims.take(player.getUniqueId());
        int delivered = 0;
        for (ClaimQueue.Claim claim : pending) {
            ItemStack item = claim.decode();
            if (item == null) {
                continue;
            }
            deliver(player, item);
            delivered++;
        }
        return delivered;
    }

    public int claimCount(UUID player) {
        return claims.count(player);
    }

    // ------------------------------------------------------------------ expiry

    /** Settles every due lot; returns how many were processed. */
    public int expireDue() {
        List<AuctionLot> due = lots.due(System.currentTimeMillis());
        for (AuctionLot lot : due) {
            settleExpired(lot);
        }
        if (!due.isEmpty()) {
            lots.flush();
        }
        return due.size();
    }

    private void settleExpired(AuctionLot lot) {
        UUID winner = lot.bidder();
        Player online = winner == null ? null : Bukkit.getPlayer(winner);
        if (online != null && online.isOnline() && plugin.scheduler().isFolia()) {
            final AuctionLot targetLot = lot;
            plugin.scheduler().runFor(online, new Runnable() {
                @Override
                public void run() {
                    settleExpiredNow(targetLot);
                }
            });
            return;
        }
        settleExpiredNow(lot);
    }

    private void settleExpiredNow(AuctionLot lot) {
        if (lot.isBid() && lot.hasBids()) {
            if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.SOLD)) {
                return;
            }
            settleAuction(lot);
            return;
        }
        if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.EXPIRED)) {
            return;
        }
        notifier.notify(lot.seller(), "notify-expired",
                "item", Items.displayName(lot.item()),
                "amount", String.valueOf(lot.amount()));
    }

    private void settleAuction(AuctionLot lot) {
        UUID winner = lot.bidder();
        double price = lot.price();
        double tax = taxOf(lot, price);
        double payout = Math.max(0d, price - tax);
        Player online = winner == null ? null : Bukkit.getPlayer(winner);
        if (online != null && online.isOnline()) {
            deliver(online, lot.item());
        } else if (winner != null) {
            claims.add(winner, lot.item(), lot.id());
            notifier.notify(winner, "notify-claim-ready",
                    "item", Items.displayName(lot.item()));
        }
        paySeller(lot, payout, price);
        if (winner != null) {
            notifier.notify(winner, "notify-won",
                    "item", Items.displayName(lot.item()),
                    "price", economy.format(price));
        }
        notifier.notify(lot.seller(), "notify-sold",
                "item", Items.displayName(lot.item()),
                "amount", String.valueOf(lot.amount()),
                "price", economy.format(payout),
                "buyer", lot.bidderName() == null ? "?" : lot.bidderName());
        if (winner != null) {
            record(winner, lot.bidderName(), HistoryEntry.Role.BUYER, lot, price, 0d, lot.sellerName());
        }
        record(lot.seller(), lot.sellerName(), HistoryEntry.Role.SELLER, lot, payout, tax,
                lot.bidderName() == null ? "?" : lot.bidderName());
        transactionLog.log("SOLD", lot, lot.bidderName(), lot.sellerName(), price, tax);
    }

    // --------------------------------------------------------------- auto-buy

    /** Server buys stale fixed-price lots at a percentage of the price. */
    public int autoBuyout() {
        if (!config.autoBuyEnabled() || config.autoBuyPercent() <= 0d) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - config.autoBuyAfterHours() * 3600_000L;
        List<AuctionLot> stale = lots.staleActive(cutoff, 200);
        int bought = 0;
        for (AuctionLot lot : stale) {
            if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.CANCELLED)) {
                continue;
            }
            double payout = round2(lot.price() * config.autoBuyPercent() / 100d);
            if (payout > 0d) {
                paySeller(lot, payout, payout);
            }
            notifier.notify(lot.seller(), "notify-autobuy",
                    "item", Items.displayName(lot.item()),
                    "price", economy.format(payout));
            record(lot.seller(), lot.sellerName(), HistoryEntry.Role.SELLER, lot, payout, 0d, "SERVER");
            transactionLog.log("AUTOBUY", lot, "SERVER", lot.sellerName(), payout, 0d);
            bought++;
        }
        return bought;
    }

    // ------------------------------------------------------------------- misc

    private void record(UUID player, String name, HistoryEntry.Role role, AuctionLot lot, double price,
                        double tax, String counterparty) {
        if (player == null || history == null) {
            return;
        }
        String material = lot.item() == null || lot.item().getType() == null
                ? null : lot.item().getType().name();
        history.add(new HistoryEntry(System.currentTimeMillis(), player, name, role,
                Items.displayName(lot.item()), material, lot.amount(), price, tax, counterparty, lot.id()));
    }

    private void notifyWatchers(AuctionLot lot, UUID seller) {
        if (watches == null) {
            return;
        }
        String text = lot.searchText();
        for (Map.Entry<UUID, List<String>> entry : watches.all().entrySet()) {
            if (entry.getKey().equals(seller)) {
                continue;
            }
            for (String query : entry.getValue()) {
                if (query != null && !query.isEmpty() && text.contains(query)) {
                    notifier.notify(entry.getKey(), "notify-watch",
                            "item", Items.displayName(lot.item()),
                            "price", economy.format(lot.price()),
                            "id", String.valueOf(lot.id()));
                    break;
                }
            }
        }
    }

    /** Highest auction.limit.<n> permission or the configured default. */
    public int limit(Player player) {
        return config.limitFor(player);
    }

    public double taxOf(AuctionLot lot) {
        return taxOf(lot, lot.price());
    }

    private double taxOf(AuctionLot lot, double price) {
        Player online = lot.seller() == null ? null : Bukkit.getPlayer(lot.seller());
        double percent = config.taxFor(online);
        if (percent <= 0d) {
            return 0d;
        }
        return round2(price * percent / 100d);
    }

    private void paySeller(AuctionLot lot, double payout, double price) {
        if (payout <= 0d || lot.seller() == null) {
            return;
        }
        OfflinePlayer seller = Bukkit.getOfflinePlayer(lot.seller());
        if (!economy.deposit(seller, payout)) {
            plugin.getLogger().warning("Could not pay seller " + lot.sellerName()
                    + " for lot #" + lot.id() + " (payout " + payout + ") - queued for retry");
            payouts.add(lot.seller(), lot.sellerName(), payout, lot.id());
        }
    }

    private void refundBidder(AuctionLot lot) {
        if (lot.bidder() == null || lot.price() <= 0d) {
            return;
        }
        refund(lot.bidder(), lot.bidderName(), lot.price(), lot.id());
    }

    private void refund(UUID player, String name, double amount, long lotId) {
        if (player == null || amount <= 0d) {
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(player);
        if (!economy.deposit(target, amount)) {
            payouts.add(player, name, amount, lotId);
        }
    }

    private boolean deliver(Player player, ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemStack copy = item.clone();
        Map<Integer, ItemStack> leftover = new HashMap<Integer, ItemStack>();
        try {
            leftover = player.getInventory().addItem(copy);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not give item to " + player.getName()
                    + ": " + throwable.getClass().getSimpleName());
            leftover.put(Integer.valueOf(0), copy);
        }
        if (leftover.isEmpty()) {
            return false;
        }
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        return true;
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    // ------------------------------------------------------------------- admin

    /**
     * Moderation removal. Active and expired lots become EXPIRED, so the item is
     * returned to the seller's claim list; closed lots are deleted outright.
     */
    public boolean adminRemove(long lotId) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null) {
            return false;
        }
        refundBidder(lot);
        if (lot.status() == LotStatus.ACTIVE) {
            if (!lots.transition(lot.id(), LotStatus.ACTIVE, LotStatus.EXPIRED)) {
                return false;
            }
        } else if (lot.status() != LotStatus.EXPIRED) {
            lots.delete(lotId);
        }
        return true;
    }

    /** Cross-server watcher sync: announce a lot created by any server. */
    public void announceNewLot(AuctionLot lot) {
        if (lot == null) {
            return;
        }
        notifyWatchers(lot, lot.seller());
    }

    public AuctionLot adminInfo(long lotId) {
        return lots.find(lotId);
    }

    /** Force-settles an active lot (testing/ops): bids are paid out or the lot expires. */
    public boolean adminEnd(long lotId) {
        AuctionLot lot = lots.find(lotId);
        if (lot == null || lot.status() != LotStatus.ACTIVE) {
            return false;
        }
        settleExpired(lot);
        return true;
    }
}
