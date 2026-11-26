package com.xuvigan.aurumauction.command;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.data.WatchStore;
import com.xuvigan.aurumauction.gui.MenuType;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.TopEntry;
import com.xuvigan.aurumauction.service.AuctionService;
import com.xuvigan.aurumauction.util.Items;
import com.xuvigan.aurumauction.util.Numbers;

/** /ah command tree. */
public final class AuctionCommand implements CommandExecutor {

    private final AurumAuction plugin;

    public AuctionCommand(AurumAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Player player = playerOrNull(sender);
            if (player == null) {
                return true;
            }
            if (!require(player, "auction.use")) {
                return true;
            }
            plugin.gui().open(player, MenuType.MAIN, 1);
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("auction.admin")) {
                plugin.messages().send(sender, "admin-only");
                return true;
            }
            plugin.reloadAll();
            plugin.messages().send(sender, "reload-done");
            return true;
        }
        if ("help".equals(sub)) {
            sendHelp(sender);
            return true;
        }
        if ("admin".equals(sub)) {
            if (!sender.hasPermission("auction.admin")) {
                plugin.messages().send(sender, "admin-only");
                return true;
            }
            admin(sender, args);
            return true;
        }
        Player player = playerOrNull(sender);
        if (player == null) {
            return true;
        }
        if ("sell".equals(sub)) {
            if (!require(player, "auction.sell")) {
                return true;
            }
            if (!plugin.cooldowns().allow(player.getUniqueId(), "sell", plugin.config().commandCooldownMs())) {
                plugin.messages().send(player, "too-fast");
                return true;
            }
            sell(player, args);
            return true;
        }
        if ("search".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            if (!plugin.cooldowns().allow(player.getUniqueId(), "search", plugin.config().commandCooldownMs())) {
                plugin.messages().send(player, "too-fast");
                return true;
            }
            search(player, args);
            return true;
        }
        if ("auction".equals(sub)) {
            if (!require(player, "auction.sell")) {
                return true;
            }
            if (!plugin.cooldowns().allow(player.getUniqueId(), "sell", plugin.config().commandCooldownMs())) {
                plugin.messages().send(player, "too-fast");
                return true;
            }
            sellAuction(player, args);
            return true;
        }
        if ("bid".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            if (!plugin.cooldowns().allow(player.getUniqueId(), "bid", plugin.config().commandCooldownMs())) {
                plugin.messages().send(player, "too-fast");
                return true;
            }
            bid(player, args);
            return true;
        }
        if ("claims".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            int claimed = plugin.auction().claimAll(player);
            plugin.messages().send(player, claimed > 0 ? "claims-done" : "claims-none",
                    "count", String.valueOf(claimed));
            return true;
        }
        if ("buy".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            if (!plugin.cooldowns().allow(player.getUniqueId(), "buy", plugin.config().commandCooldownMs())) {
                plugin.messages().send(player, "too-fast");
                return true;
            }
            buy(player, args);
            return true;
        }
        if ("sellmenu".equals(sub)) {
            if (!require(player, "auction.sell")) {
                return true;
            }
            if (args.length < 2) {
                plugin.messages().send(player, "sell-menu-usage");
                return true;
            }
            double price = Numbers.parse(args[1], -1d);
            if (price < 0d) {
                plugin.messages().send(player, "sell-menu-price");
                return true;
            }
            plugin.gui().openSell(player, price);
            return true;
        }
        if ("history".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            plugin.gui().open(player, com.xuvigan.aurumauction.gui.MenuType.HISTORY, 1);
            return true;
        }
        if ("top".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            top(player, args);
            return true;
        }
        if ("watch".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            watch(player, args);
            return true;
        }
        if ("my".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            plugin.gui().open(player, MenuType.MINE, 1);
            return true;
        }
        if ("expired".equals(sub)) {
            if (!require(player, "auction.use")) {
                return true;
            }
            plugin.gui().open(player, MenuType.EXPIRED, 1);
            return true;
        }
        plugin.messages().send(player, "unknown-subcommand");
        return true;
    }

    private void sell(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "sell-usage");
            return;
        }
        double price = Numbers.parse(args[1], -1d);
        if (price < 0d) {
            plugin.messages().send(player, "sell-usage");
            return;
        }
        int amount = args.length > 2 ? Numbers.parseInt(args[2], -1) : -1;
        AuctionService.Result result = plugin.auction().sell(player, price, amount);
        switch (result.code()) {
            case OK: {
                AuctionLot lot = result.lot();
                plugin.messages().send(player, "sell-success",
                        "id", String.valueOf(lot.id()),
                        "item", Items.displayName(lot.item()),
                        "amount", String.valueOf(lot.amount()),
                        "price", plugin.economy().format(lot.price()));
                break;
            }
            case NO_ECONOMY:
                plugin.messages().send(player, "economy-missing");
                break;
            case EMPTY_HAND:
                plugin.messages().send(player, "sell-empty-hand");
                break;
            case BLOCKED:
                plugin.messages().send(player, "sell-blocked");
                break;
            case SHULKERS_DISABLED:
                plugin.messages().send(player, "sell-shulkers-disabled");
                break;
            case PRICE_LOW:
                plugin.messages().send(player, "sell-price-low",
                        "min", plugin.economy().format(plugin.config().minPrice()));
                break;
            case PRICE_HIGH:
                plugin.messages().send(player, "sell-price-high",
                        "max", plugin.economy().format(plugin.config().maxPrice()));
                break;
            case LIMIT_REACHED:
                plugin.messages().send(player, "sell-limit",
                        "limit", String.valueOf(plugin.auction().limit(player)));
                break;
            case SERVER_LIMIT:
                plugin.messages().send(player, "server-limit");
                break;
            case INVALID_AMOUNT:
                plugin.messages().send(player, "sell-amount-invalid",
                        "max", String.valueOf(plugin.config().maxAmountPerLot()));
                break;
            case CANCELLED:
                plugin.messages().send(player, "sell-cancelled");
                break;
            default:
                plugin.messages().send(player, "sell-usage");
                break;
        }
    }

    private void search(Player player, String[] args) {        if (args.length < 2) {
            plugin.messages().send(player, "search-too-short",
                    "min", String.valueOf(plugin.config().searchMinLength()));
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[i]);
        }
        String query = text.toString().trim();
        if (query.length() < plugin.config().searchMinLength()) {
            plugin.messages().send(player, "search-too-short",
                    "min", String.valueOf(plugin.config().searchMinLength()));
            return;
        }
        int found = plugin.lots().count(LotQuery.active().text(query));
        if (found == 0) {
            plugin.messages().send(player, "search-none", "query", query);
            return;
        }
        plugin.gui().open(player, MenuType.SEARCH, 1, query);
    }

    private void sellAuction(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "sell-auction-usage");
            return;
        }
        double start = Numbers.parse(args[1], -1d);
        if (start < 0d) {
            plugin.messages().send(player, "sell-auction-usage");
            return;
        }
        int hours = args.length > 2
                ? Numbers.parseInt(args[2], plugin.config().bidDefaultHours())
                : plugin.config().bidDefaultHours();
        if (hours <= 0) {
            hours = plugin.config().bidDefaultHours();
        }
        double buyout = args.length > 3 ? Numbers.parse(args[3], 0d) : 0d;
        AuctionService.Result result = plugin.auction().sellAuction(player, start, hours, buyout);
        switch (result.code()) {
            case OK: {
                AuctionLot lot = result.lot();
                plugin.messages().send(player, "auction-created",
                        "id", String.valueOf(lot.id()),
                        "item", Items.displayName(lot.item()),
                        "amount", String.valueOf(lot.amount()),
                        "price", plugin.economy().format(lot.price()),
                        "hours", String.valueOf(hours),
                        "buyout", lot.hasBuyout() ? plugin.economy().format(lot.buyoutPrice()) : "-");
                break;
            }
            case BIDS_DISABLED:
                plugin.messages().send(player, "bids-disabled");
                break;
            case BUYOUT_TOO_LOW:
                plugin.messages().send(player, "buyout-too-low");
                break;
            case NO_ECONOMY:
                plugin.messages().send(player, "economy-missing");
                break;
            case EMPTY_HAND:
                plugin.messages().send(player, "sell-empty-hand");
                break;
            case BLOCKED:
                plugin.messages().send(player, "sell-blocked");
                break;
            case SHULKERS_DISABLED:
                plugin.messages().send(player, "sell-shulkers-disabled");
                break;
            case PRICE_LOW:
                plugin.messages().send(player, "sell-price-low",
                        "min", plugin.economy().format(plugin.config().minPrice()));
                break;
            case PRICE_HIGH:
                plugin.messages().send(player, "sell-price-high",
                        "max", plugin.economy().format(plugin.config().maxPrice()));
                break;
            case LIMIT_REACHED:
                plugin.messages().send(player, "sell-limit",
                        "limit", String.valueOf(plugin.auction().limit(player)));
                break;
            case SERVER_LIMIT:
                plugin.messages().send(player, "server-limit");
                break;
            case INVALID_AMOUNT:
                plugin.messages().send(player, "sell-amount-invalid",
                        "max", String.valueOf(plugin.config().maxAmountPerLot()));
                break;
            case CANCELLED:
                plugin.messages().send(player, "sell-cancelled");
                break;
            default:
                plugin.messages().send(player, "sell-auction-usage");
                break;
        }
    }

    private void buy(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "buy-usage");
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(player, "buy-usage");
            return;
        }
        int amount = args.length > 2 ? Numbers.parseInt(args[2], -1) : 0;
        AuctionService.Result result = amount > 0
                ? plugin.auction().buyPartial(player, id, amount)
                : plugin.auction().buy(player, id);
        switch (result.code()) {
            case OK: {
                AuctionLot lot = result.lot();
                plugin.messages().send(player, "buy-success",
                        "item", Items.displayName(lot.item()),
                        "amount", String.valueOf(lot.amount()),
                        "price", plugin.economy().format(lot.price()));
                if (result.dropped()) {
                    plugin.messages().send(player, "buy-dropped");
                }
                break;
            }
            case NO_ECONOMY:
                plugin.messages().send(player, "economy-missing");
                break;
            case NOT_FOUND:
                plugin.messages().send(player, "buy-not-found");
                break;
            case NOT_ACTIVE:
                plugin.messages().send(player, "buy-not-active");
                break;
            case EXPIRED:
                plugin.messages().send(player, "buy-expired");
                break;
            case OWN_LOT:
                plugin.messages().send(player, "buy-own");
                break;
            case NO_MONEY: {
                AuctionLot found = plugin.lots().find(id);
                plugin.messages().send(player, "buy-no-money",
                        "price", found == null ? "-" : plugin.economy().format(plugin.auction().effectivePrice(found)));
                break;
            }
            case NOT_BUYABLE:
                plugin.messages().send(player, "not-buyable");
                break;
            case INVALID_AMOUNT:
                plugin.messages().send(player, "buy-usage");
                break;
            case CANCELLED:
                plugin.messages().send(player, "buy-cancelled");
                break;
            default:
                plugin.messages().send(player, "buy-usage");
                break;
        }
    }

    private void bid(Player player, String[] args) {        if (args.length < 3) {
            plugin.messages().send(player, "bid-usage");
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(player, "bid-usage");
            return;
        }
        double amount = Numbers.parse(args[2], -1d);
        if (amount < 0d) {
            plugin.messages().send(player, "bid-usage");
            return;
        }
        AuctionService.Result result = plugin.auction().bid(player, id, amount);
        switch (result.code()) {
            case OK: {
                AuctionLot lot = result.lot();
                if (lot != null && lot.status() == com.xuvigan.aurumauction.model.LotStatus.SOLD) {
                    plugin.messages().send(player, "buy-success",
                            "item", Items.displayName(lot.item()),
                            "amount", String.valueOf(lot.amount()),
                            "price", plugin.economy().format(plugin.auction().effectivePrice(lot)));
                    if (result.dropped()) {
                        plugin.messages().send(player, "buy-dropped");
                    }
                } else {
                    plugin.messages().send(player, "bid-success",
                            "item", Items.displayName(lot.item()),
                            "price", plugin.economy().format(amount));
                }
                break;
            }
            case BID_TOO_LOW:
                plugin.messages().send(player, "bid-too-low",
                        "min", plugin.economy().format(result.minBid()));
                break;
            case BIDS_DISABLED:
                plugin.messages().send(player, "bids-disabled");
                break;
            case NOT_BID:
                plugin.messages().send(player, "not-bid");
                break;
            case ALREADY_TOP:
                plugin.messages().send(player, "bid-already-top");
                break;
            case OWN_LOT:
                plugin.messages().send(player, "bid-own");
                break;
            case NOT_BUYABLE:
                plugin.messages().send(player, "not-buyable");
                break;
            case NO_ECONOMY:
                plugin.messages().send(player, "economy-missing");
                break;
            case NO_MONEY:
                plugin.messages().send(player, "buy-no-money",
                        "price", plugin.economy().format(amount));
                break;
            case NOT_FOUND:
                plugin.messages().send(player, "buy-not-found");
                break;
            case EXPIRED:
                plugin.messages().send(player, "buy-expired");
                break;
            case NOT_ACTIVE:
                plugin.messages().send(player, "buy-not-active");
                break;
            default:
                plugin.messages().send(player, "bid-usage");
                break;
        }
    }

    private void top(Player player, String[] args) {
        boolean buyers = args.length > 1
                && ("buyers".equalsIgnoreCase(args[1]) || "покупатели".equalsIgnoreCase(args[1]));
        List<TopEntry> entries = plugin.history().top(
                buyers ? HistoryEntry.Role.BUYER : HistoryEntry.Role.SELLER,
                plugin.config().historyTopSize());
        if (entries.isEmpty()) {
            plugin.messages().send(player, "top-empty");
            return;
        }
        player.sendMessage(plugin.messages().get(buyers ? "top-header-buyers" : "top-header-sellers"));
        int place = 1;
        for (TopEntry entry : entries) {
            player.sendMessage(plugin.messages().format("top-line",
                    "place", String.valueOf(place),
                    "name", entry.name(),
                    "total", plugin.economy().format(entry.total()),
                    "count", String.valueOf(entry.count())));
            place++;
        }
    }

    private void watch(Player player, String[] args) {
        if (args.length == 1 || "list".equalsIgnoreCase(args[1])) {
            sendWatchList(player);
            return;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("clear".equals(action)) {
            plugin.watches().clear(player.getUniqueId());
            plugin.messages().send(player, "watch-cleared");
            return;
        }
        boolean remove = "remove".equals(action) || "del".equals(action);
        int start = remove || "add".equals(action) ? 2 : 1;
        if (args.length <= start) {
            plugin.messages().send(player, "watch-usage");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[i]);
        }
        String query = text.toString().trim();
        if (query.length() < plugin.config().searchMinLength()) {
            plugin.messages().send(player, "watch-too-short",
                    "min", String.valueOf(plugin.config().searchMinLength()));
            return;
        }
        if (remove) {
            boolean removed = plugin.watches().remove(player.getUniqueId(), query);
            plugin.messages().send(player, removed ? "watch-removed" : "watch-not-found", "query", query);
            return;
        }
        WatchStore.Result result = plugin.watches().add(player.getUniqueId(), query);
        switch (result) {
            case ADDED:
                plugin.messages().send(player, "watch-added", "query", query,
                        "max", String.valueOf(plugin.watches().maxPerPlayer()));
                break;
            case EXISTS:
                plugin.messages().send(player, "watch-exists", "query", query);
                break;
            case LIMIT:
            default:
                plugin.messages().send(player, "watch-limit",
                        "max", String.valueOf(plugin.watches().maxPerPlayer()));
                break;
        }
    }

    private void sendWatchList(Player player) {
        List<String> list = plugin.watches().list(player.getUniqueId());
        if (list.isEmpty()) {
            plugin.messages().send(player, "watch-empty");
            return;
        }
        player.sendMessage(plugin.messages().get("watch-header"));
        for (String query : list) {
            player.sendMessage(plugin.messages().format("watch-line", "query", query));
        }
    }

    private void sendHelp(CommandSender sender) {
        List<String> lines = plugin.messages().list("help-lines");
        if (!lines.isEmpty()) {
            for (String line : lines) {
                sender.sendMessage(line);
            }
            return;
        }
        sender.sendMessage(plugin.messages().get("help-header"));
        sender.sendMessage(plugin.messages().get("help-line-open"));
        sender.sendMessage(plugin.messages().get("help-line-sell"));
        sender.sendMessage(plugin.messages().get("help-line-sellmenu"));
        sender.sendMessage(plugin.messages().get("help-line-buy"));
        sender.sendMessage(plugin.messages().get("help-line-auction"));
        sender.sendMessage(plugin.messages().get("help-line-bid"));
        sender.sendMessage(plugin.messages().get("help-line-search"));
        sender.sendMessage(plugin.messages().get("help-line-mine"));
        sender.sendMessage(plugin.messages().get("help-line-expired"));
        sender.sendMessage(plugin.messages().get("help-line-claims"));
        sender.sendMessage(plugin.messages().get("help-line-history"));
        sender.sendMessage(plugin.messages().get("help-line-top"));
        sender.sendMessage(plugin.messages().get("help-line-watch"));
        if (sender.hasPermission("auction.admin")) {
            sender.sendMessage(plugin.messages().get("help-line-reload"));
            sender.sendMessage(plugin.messages().get("help-line-admin"));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "admin-usage");
            return;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("reload".equals(action)) {
            plugin.reloadAll();
            plugin.messages().send(sender, "reload-done");
            return;
        }
        if ("payouts".equals(action)) {
            int paid = plugin.payouts().retry(plugin.economy());
            int pending = plugin.payouts().size();
            plugin.messages().send(sender, "admin-payouts",
                    "paid", String.valueOf(paid),
                    "pending", String.valueOf(pending));
            return;
        }
        if ("autobuy".equals(action)) {
            int bought = plugin.auction().autoBuyout();
            plugin.messages().send(sender, "admin-autobuy", "count", String.valueOf(bought));
            return;
        }
        if ("remove".equals(action) || "info".equals(action) || "end".equals(action)) {
            if (args.length < 3) {
                plugin.messages().send(sender, "admin-id-usage");
                return;
            }
            long id;
            try {
                id = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                plugin.messages().send(sender, "admin-id-usage");
                return;
            }
            AuctionLot lot = plugin.auction().adminInfo(id);
            if (lot == null) {
                plugin.messages().send(sender, "admin-not-found", "id", String.valueOf(id));
                return;
            }
            if ("remove".equals(action)) {
                plugin.auction().adminRemove(id);
                plugin.messages().send(sender, "admin-removed", "id", String.valueOf(id));
                return;
            }
            if ("end".equals(action)) {
                boolean ended = plugin.auction().adminEnd(id);
                plugin.messages().send(sender, ended ? "admin-ended" : "admin-not-found",
                        "id", String.valueOf(id));
                return;
            }
            plugin.messages().send(sender, "admin-info-header", "id", String.valueOf(id));
            plugin.messages().sendRaw(sender, plugin.messages().format("admin-info-line",
                    "seller", lot.sellerName(),
                    "status", lot.status().name(),
                    "price", plugin.economy().format(lot.price()),
                    "item", Items.displayName(lot.item()),
                    "amount", String.valueOf(lot.amount())));
            return;
        }
        plugin.messages().send(sender, "admin-usage");
    }

    private Player playerOrNull(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        plugin.messages().send(sender, "player-only");
        return null;
    }

    private boolean require(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(player, "no-permission");
        return false;
    }
}
