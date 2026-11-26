package com.xuvigan.aurumauction.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.config.GuiConfig;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.model.HistoryEntry;
import com.xuvigan.aurumauction.model.LotCategory;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;
import com.xuvigan.aurumauction.service.AuctionService;
import com.xuvigan.aurumauction.util.Bedrock;
import com.xuvigan.aurumauction.util.Items;
import com.xuvigan.aurumauction.util.Numbers;
import com.xuvigan.aurumauction.util.Text;

/** Renders every auction menu and routes clicks by holder action key. */
public final class GuiManager {

    private static final LotCategory[] CATEGORY_CYCLE = new LotCategory[] {
        null,
        LotCategory.BLOCKS,
        LotCategory.TOOLS,
        LotCategory.COMBAT,
        LotCategory.FOOD,
        LotCategory.POTIONS,
        LotCategory.REDSTONE,
        LotCategory.OTHER
    };

    private final AurumAuction plugin;

    public GuiManager(AurumAuction plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, MenuType menu, int page) {
        open(player, menu, page, null);
    }

    public void open(Player player, MenuType menu, int page, String text) {
        open(player, menu, page, text, LotQuery.Sort.NEWEST, null);
    }

    public void open(Player player, MenuType menu, int page, String text, LotQuery.Sort sort) {
        open(player, menu, page, text, sort, null);
    }

    public void open(Player player, MenuType menu, int page, String text, LotQuery.Sort sort, LotCategory category) {
        if (player == null) {
            return;
        }
        render(player, new GuiHolder(menu, page).subject(text).sort(sort).category(category));
    }

    // ------------------------------------------------------------------ render

    private void render(Player player, GuiHolder holder) {
        if (holder.menu() == MenuType.CONFIRM) {
            renderConfirm(player, holder);
            return;
        }
        if (holder.menu() == MenuType.HISTORY) {
            renderHistory(player, holder);
            return;
        }
        GuiConfig gui = plugin.guiConfig();
        LotQuery query = queryFor(holder, player);
        holder.clearActions();
        int perPage = Math.max(1, gui.contentSlots());
        int total = plugin.lots().count(query);
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int page = Math.min(Math.max(1, holder.page()), pages);
        holder.page(page);

        String title = gui.title()
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages))
                .replace("%total%", String.valueOf(total))
                .replace("%query%", holder.subject() == null ? "" : holder.subject())
                .replace("%category%", categoryName(holder.category()));
        Inventory inventory = Bukkit.createInventory(holder, gui.rows() * 9, Text.color(title));
        holder.inventory(inventory);

        List<AuctionLot> pageLots = plugin.lots().search(query, page, perPage);
        int slot = 0;
        for (AuctionLot lot : pageLots) {
            if (slot >= perPage) {
                break;
            }
            inventory.setItem(slot, lotItem(lot, holder, player));
            holder.action(slot, "LOT:" + lot.id());
            slot++;
        }

        ItemStack filler = ItemBuilder.of(gui.filler()).name(" ").build();
        for (int i = gui.contentSlots(); i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        if (page > 1) {
            button(inventory, holder, "previous", null);
        }
        if (page < pages) {
            button(inventory, holder, "next", null);
        }
        button(inventory, holder, "search", null);
        button(inventory, holder, "category", map("category", categoryName(holder.category())));
        button(inventory, holder, "sort", map("sort",
                plugin.messages().get("sort-" + holder.sort().name().toLowerCase(Locale.ROOT))));
        button(inventory, holder, "mine", null);
        button(inventory, holder, "expired", null);
        button(inventory, holder, "history", null);
        button(inventory, holder, "close", null);

        player.openInventory(inventory);
        play(player, "click");
    }

    public void openSell(Player player, double price) {
        if (player == null) {
            return;
        }
        renderSell(player, new GuiHolder(MenuType.SELL, 1).subject(String.valueOf(price)));
    }

    private void renderSell(Player player, GuiHolder holder) {
        GuiConfig gui = plugin.guiConfig();
        double price = Numbers.parse(holder.subject(), 0d);
        int rows = gui.sellRows();
        int size = rows * 9;
        int storage = gui.sellStorageSlots();
        String title = gui.sellTitle().replace("%price%", plugin.economy().format(price));
        Inventory inventory = Bukkit.createInventory(holder, size, Text.color(title));
        holder.inventory(inventory);
        holder.clearActions();

        ItemStack filler = ItemBuilder.of(gui.filler()).name(" ").build();
        for (int i = storage; i < size; i++) {
            inventory.setItem(i, filler);
        }
        button(inventory, holder, "sell-confirm", null);
        button(inventory, holder, "sell-cancel", null);

        player.openInventory(inventory);
        play(player, "click");
    }

    /** Returns items left in the sell menu when it is closed. */
    public void returnSellItems(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        int storage = Math.min(plugin.guiConfig().sellStorageSlots(), inventory.getSize());
        for (int i = 0; i < storage; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) {
                continue;
            }
            inventory.setItem(i, null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    private void confirmSell(Player player, GuiHolder holder) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        int storage = Math.min(plugin.guiConfig().sellStorageSlots(), inventory.getSize());
        int slot = -1;
        for (int i = 0; i < storage; i++) {
            if (inventory.getItem(i) == null) {
                continue;
            }
            if (slot >= 0) {
                plugin.messages().send(player, "sell-menu-one-stack");
                return;
            }
            slot = i;
        }
        if (slot < 0) {
            plugin.messages().send(player, "sell-menu-empty");
            return;
        }
        ItemStack item = inventory.getItem(slot);
        double price = Numbers.parse(holder.subject(), -1d);
        if (price < 0d) {
            plugin.messages().send(player, "sell-menu-price");
            return;
        }
        AuctionService.Result result = plugin.auction().sellItem(player, item, price);
        if (result.success()) {
            inventory.setItem(slot, null);
            plugin.messages().send(player, "sell-success",
                    "id", String.valueOf(result.lot().id()),
                    "item", Items.displayName(result.lot().item()),
                    "amount", String.valueOf(result.lot().amount()),
                    "price", plugin.economy().format(result.lot().price()));
            player.closeInventory();
            return;
        }
        reportSellError(player, result.code());
    }

    private void reportSellError(Player player, AuctionService.Code code) {
        switch (code) {
            case NO_ECONOMY:
                plugin.messages().send(player, "economy-missing");
                break;
            case EMPTY_HAND:
                plugin.messages().send(player, "sell-menu-empty");
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
            default:
                plugin.messages().send(player, "sell-usage");
                break;
        }
    }

    private void renderHistory(Player player, GuiHolder holder) {        GuiConfig gui = plugin.guiConfig();
        int perPage = Math.max(1, gui.contentSlots());
        int total = plugin.history().count(player.getUniqueId());
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int page = Math.min(Math.max(1, holder.page()), pages);
        holder.page(page);

        String title = gui.historyTitle()
                .replace("%page%", String.valueOf(page))
                .replace("%pages%", String.valueOf(pages))
                .replace("%total%", String.valueOf(total));
        Inventory inventory = Bukkit.createInventory(holder, gui.rows() * 9, Text.color(title));
        holder.inventory(inventory);
        holder.clearActions();

        List<HistoryEntry> entries = plugin.history().page(player.getUniqueId(), page, perPage);
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        int slot = 0;
        for (HistoryEntry entry : entries) {
            if (slot >= perPage) {
                break;
            }
            Material material = entry.material() == null ? Material.PAPER : Material.matchMaterial(entry.material());
            if (material == null) {
                material = Material.PAPER;
            }
            ItemBuilder builder = ItemBuilder.of(material, Math.max(1, Math.min(64, entry.amount())))
                    .name("&f" + entry.item());
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("role", plugin.messages().get(entry.role() == HistoryEntry.Role.SELLER
                    ? "history-role-seller" : "history-role-buyer"));
            placeholders.put("item", entry.item());
            placeholders.put("amount", String.valueOf(entry.amount()));
            placeholders.put("price", plugin.economy().format(entry.price()));
            placeholders.put("tax", plugin.economy().format(entry.tax()));
            placeholders.put("counterparty", entry.counterparty());
            placeholders.put("id", String.valueOf(entry.lotId()));
            placeholders.put("date", format.format(new Date(entry.time())));
            List<String> lore = new ArrayList<String>();
            for (String line : gui.historyLore()) {
                lore.add(Text.color(Text.format(line, placeholders)));
            }
            builder.lore(trimLore(player, lore));
            inventory.setItem(slot, builder.build());
            slot++;
        }

        ItemStack filler = ItemBuilder.of(gui.filler()).name(" ").build();
        for (int i = gui.contentSlots(); i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        if (page > 1) {
            button(inventory, holder, "previous", null);
        }
        if (page < pages) {
            button(inventory, holder, "next", null);
        }
        button(inventory, holder, "back", null);
        button(inventory, holder, "close", null);

        player.openInventory(inventory);
        play(player, "click");
    }

    private void renderConfirm(Player player, GuiHolder holder) {        AuctionLot lot = findLot(holder.subject());
        long now = System.currentTimeMillis();
        if (lot == null || lot.status() != LotStatus.ACTIVE || lot.isExpired(now)) {
            plugin.messages().send(player, "buy-not-active");
            open(player, MenuType.MAIN, 1);
            return;
        }
        GuiConfig gui = plugin.guiConfig();
        int rows = gui.confirmRows();
        int size = rows * 9;
        double price = plugin.auction().effectivePrice(lot);
        String title = gui.confirmTitle()
                .replace("%price%", plugin.economy().format(price))
                .replace("%item%", Items.displayName(lot.item()));
        Inventory inventory = Bukkit.createInventory(holder, size, Text.color(title));
        holder.inventory(inventory);
        holder.clearActions();

        ItemStack filler = ItemBuilder.of(gui.filler()).name(" ").build();
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, filler);
        }

        Map<String, String> placeholders = lotPlaceholders(lot, holder);
        placeholders.put("price", plugin.economy().format(price));
        ItemStack preview = lot.item() == null
                ? ItemBuilder.of(Material.STONE).build()
                : lot.item().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            if (!lore.isEmpty()) {
                lore.add("");
            }
            for (String line : gui.confirmLore()) {
                lore.add(Text.color(Text.format(line, placeholders)));
            }
            meta.setLore(trimLore(player, lore));
            preview.setItemMeta(meta);
        }
        inventory.setItem(size / 2, preview);        button(inventory, holder, "confirm", map("price", plugin.economy().format(price)));
        button(inventory, holder, "cancel", null);

        player.openInventory(inventory);
        play(player, "click");
    }

    private LotQuery queryFor(GuiHolder holder, Player player) {
        LotQuery query = LotQuery.active().sort(holder.sort());
        if (holder.category() != null) {
            query.category(holder.category());
        }
        switch (holder.menu()) {
            case SEARCH:
                query.text(holder.subject());
                break;
            case MINE:
                query.seller(player.getUniqueId());
                break;
            case EXPIRED:
                query.status(LotStatus.EXPIRED).seller(player.getUniqueId());
                break;
            case MAIN:
            case CONFIRM:
            default:
                break;
        }
        return query;
    }

    private ItemStack lotItem(AuctionLot lot, GuiHolder holder, Player player) {
        ItemStack display = lot.item() == null
                ? ItemBuilder.of(Material.STONE).build()
                : lot.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }
        Map<String, String> placeholders = lotPlaceholders(lot, holder);

        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        if (!lore.isEmpty()) {
            lore.add("");
        }
        List<String> template = plugin.guiConfig().lotLore();
        int keep = template.size();
        while (keep > 0 && isHintLine(template.get(keep - 1))) {
            keep--;
        }
        for (int i = 0; i < keep; i++) {
            lore.add(Text.color(Text.format(template.get(i), placeholders)));
        }
        boolean own = lot.seller() != null && lot.seller().equals(player.getUniqueId());
        boolean bidWithoutBuyout = lot.isBid() && !lot.hasBuyout();
        if (holder.menu() == MenuType.EXPIRED) {
            lore.add(plugin.messages().get("hint-claim"));
        } else if (own) {
            lore.add(plugin.messages().get("hint-cancel"));
        } else if (!bidWithoutBuyout) {
            lore.add(plugin.messages().get("hint-buy"));
        }

        if (lot.isBid() && !plugin.guiConfig().bidLore().isEmpty()) {
            for (String line : plugin.guiConfig().bidLore()) {
                lore.add(Text.color(Text.format(line, placeholders)));
            }
        }

        List<ItemStack> contents = Items.shulkerContents(lot.item());
        if (!contents.isEmpty() && !plugin.guiConfig().shulkerLore().isEmpty()) {
            Map<String, String> withContents = new HashMap<String, String>(placeholders);
            withContents.put("contents", contentsLine(contents));
            for (String line : plugin.guiConfig().shulkerLore()) {
                lore.add(Text.color(Text.format(line, withContents)));
            }
        }

        meta.setLore(trimLore(player, lore));
        display.setItemMeta(meta);
        return display;
    }

    private static boolean isHintLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("лкм") || lower.contains("пкм")
                || lower.contains("left click") || lower.contains("right click");
    }

    /** Bedrock clients render huge lore badly; optionally trim it for Floodgate players. */    private List<String> trimLore(Player player, List<String> lore) {
        int max = plugin.config().bedrockLoreLines();
        if (max <= 0 || lore.size() <= max || !Bedrock.isBedrock(player)) {
            return lore;
        }
        return new ArrayList<String>(lore.subList(0, max));
    }

    private Map<String, String> lotPlaceholders(AuctionLot lot, GuiHolder holder) {
        long now = System.currentTimeMillis();
        Map<String, String> placeholders = new HashMap<String, String>();
        placeholders.put("id", String.valueOf(lot.id()));
        placeholders.put("category", plugin.messages().get(lot.category().messageKey()));
        placeholders.put("seller", lot.sellerName());
        placeholders.put("amount", String.valueOf(lot.amount()));
        placeholders.put("price", plugin.economy().format(lot.price()));
        placeholders.put("unit", plugin.economy().format(lot.unitPrice()));
        placeholders.put("item", Items.displayName(lot.item()));
        placeholders.put("time", lot.status() == LotStatus.EXPIRED ? "-" : Numbers.duration(lot.remainingMillis(now)));
        placeholders.put("mode", holder.menu() == null ? "" : holder.menu().name());
        placeholders.put("type", plugin.messages().get(lot.isBid() ? "type-bid" : "type-fixed"));
        placeholders.put("bids", String.valueOf(lot.bidCount()));
        placeholders.put("bidder", lot.bidderName() == null ? "-" : lot.bidderName());
        placeholders.put("buyout", lot.hasBuyout() ? plugin.economy().format(lot.buyoutPrice()) : "-");
        return placeholders;
    }

    private static String contentsLine(List<ItemStack> contents) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (ItemStack stack : contents) {
            if (shown >= 5) {
                break;
            }
            if (shown > 0) {
                out.append("&7, ");
            }
            out.append("&f").append(Items.displayName(stack)).append(" &7x&f").append(stack.getAmount());
            shown++;
        }
        if (contents.size() > 5) {
            out.append(" &7+").append(contents.size() - 5);
        }
        return out.toString();
    }

    private void button(Inventory inventory, GuiHolder holder, String key, Map<String, String> placeholders) {
        GuiConfig.Button button = plugin.guiConfig().button(key);
        if (button == null || button.slot() >= inventory.getSize()) {
            return;
        }
        String name = button.name();
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        inventory.setItem(button.slot(), ItemBuilder.of(button.material()).name(name).build());
        holder.action(button.slot(), key.toUpperCase(Locale.ROOT));
    }

    private static Map<String, String> map(String... pairs) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private String categoryName(LotCategory category) {
        return plugin.messages().get(category == null ? "category-all" : category.messageKey());
    }

    private static LotCategory nextCategory(LotCategory current) {
        for (int i = 0; i < CATEGORY_CYCLE.length; i++) {
            if (CATEGORY_CYCLE[i] == current) {
                return CATEGORY_CYCLE[(i + 1) % CATEGORY_CYCLE.length];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- click

    public void click(Player player, GuiHolder holder, int slot, ClickType clickType) {
        if (player == null || holder == null) {
            return;
        }
        if (!plugin.cooldowns().allow(player.getUniqueId(), "gui", plugin.config().clickCooldownMs())) {
            return;
        }
        String action = holder.action(slot);
        if (action == null) {
            return;
        }
        if (action.startsWith("LOT:")) {
            long id;
            try {
                id = Long.parseLong(action.substring(4));
            } catch (NumberFormatException ex) {
                return;
            }
            onClickLot(player, holder, id, clickType);
            return;
        }
        String command = action.toUpperCase(Locale.ROOT);
        if ("CLOSE".equals(command) || "CANCEL".equals(command)) {
            player.closeInventory();
            if ("CANCEL".equals(command)) {
                plugin.scheduler().runFor(player, new Runnable() {
                    @Override
                    public void run() {
                        open(player, MenuType.MAIN, 1);
                    }
                });
            }
            return;
        }
        if ("CONFIRM".equals(command)) {
            confirmPurchase(player, holder);
            return;
        }
        if ("SELL_CONFIRM".equals(command) || "SELL-CONFIRM".equals(command)) {
            confirmSell(player, holder);
            return;
        }
        if ("SELL_CANCEL".equals(command) || "SELL-CANCEL".equals(command)) {
            player.closeInventory();
            return;
        }
        if ("PREVIOUS".equals(command)) {
            holder.page(holder.page() - 1);
            render(player, holder);
            return;
        }
        if ("NEXT".equals(command)) {
            holder.page(holder.page() + 1);
            render(player, holder);
            return;
        }
        if ("SORT".equals(command)) {
            holder.sort(holder.sort().next());
            holder.page(1);
            render(player, holder);
            return;
        }
        if ("CATEGORY".equals(command)) {
            holder.category(nextCategory(holder.category()));
            holder.page(1);
            render(player, holder);
            return;
        }
        if ("SEARCH".equals(command)) {
            player.closeInventory();
            final LotQuery.Sort sort = holder.sort();
            final LotCategory category = holder.category();
            plugin.chatInput().request(player, "gui-search-prompt", new ChatInput.Handler() {
                @Override
                public void accept(String text) {
                    String query = text == null ? "" : text.trim();
                    if (query.length() < plugin.config().searchMinLength()) {
                        plugin.messages().send(player, "search-too-short",
                                "min", String.valueOf(plugin.config().searchMinLength()));
                        return;
                    }
                    if (plugin.lots().count(LotQuery.active().text(query)) == 0) {
                        plugin.messages().send(player, "search-none", "query", query);
                        return;
                    }
                    open(player, MenuType.SEARCH, 1, query, sort, category);
                }
            });
            return;
        }
        if ("MINE".equals(command)) {
            holder.menu(MenuType.MINE);
            holder.page(1);
            render(player, holder);
            return;
        }
        if ("EXPIRED".equals(command)) {
            holder.menu(MenuType.EXPIRED);
            holder.page(1);
            render(player, holder);
            return;
        }
        if ("HISTORY".equals(command)) {
            open(player, MenuType.HISTORY, 1);
            return;
        }
        if ("BACK".equals(command)) {
            open(player, MenuType.MAIN, 1);
            return;
        }
    }

    private void confirmPurchase(Player player, GuiHolder holder) {
        AuctionLot lot = findLot(holder.subject());
        if (lot == null) {
            plugin.messages().send(player, "buy-not-found");
            open(player, MenuType.MAIN, 1);
            return;
        }
        reportBuy(player, plugin.auction().buy(player, lot.id()), lot);
        player.closeInventory();
        plugin.scheduler().runFor(player, new Runnable() {
            @Override
            public void run() {
                open(player, MenuType.MAIN, 1);
            }
        });
    }

    private AuctionLot findLot(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return plugin.lots().find(Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void onClickLot(Player player, GuiHolder holder, long id, ClickType clickType) {
        AuctionLot lot = plugin.lots().find(id);
        if (lot == null) {
            plugin.messages().send(player, "buy-not-found");
            render(player, holder);
            return;
        }
        boolean own = lot.seller() != null && lot.seller().equals(player.getUniqueId());
        if (own) {
            if (lot.status() == LotStatus.EXPIRED) {
                reportClaim(player, plugin.auction().claim(player, id), lot);
            } else {
                reportCancel(player, plugin.auction().cancel(player, id), lot);
            }
            render(player, holder);
            return;
        }
        if (clickType == ClickType.SHIFT_LEFT && lot.amount() > 1 && !lot.isBid()) {
            reportBuy(player, plugin.auction().buyPartial(player, id, 1), lot);
            render(player, holder);
            return;
        }
        if (lot.isBid() && !lot.hasBuyout()) {
            plugin.messages().send(player, "gui-bid-hint",
                    "id", String.valueOf(lot.id()),
                    "min", plugin.economy().format(plugin.auction().minNextBid(lot)));
            return;
        }
        if (plugin.config().buyConfirm()) {
            render(player, new GuiHolder(MenuType.CONFIRM, 1).subject(String.valueOf(lot.id())));
            return;
        }
        reportBuy(player, plugin.auction().buy(player, id), lot);
        render(player, holder);
    }

    private void reportBuy(Player player, AuctionService.Result result, AuctionLot lot) {
        switch (result.code()) {
            case OK:
                plugin.messages().send(player, "buy-success",
                        "item", Items.displayName(result.lot() == null ? lot.item() : result.lot().item()),
                        "amount", String.valueOf(result.lot() == null ? lot.amount() : result.lot().amount()),
                        "price", plugin.economy().format(result.lot() == null ? lot.price() : result.lot().price()));
                if (result.dropped()) {
                    plugin.messages().send(player, "buy-dropped");
                }
                play(player, "purchase");
                break;
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
            case NO_MONEY:
                plugin.messages().send(player, "buy-no-money",
                        "price", plugin.economy().format(plugin.auction().effectivePrice(lot)));
                break;
            case CANCELLED:
                plugin.messages().send(player, "buy-cancelled");
                break;
            default:
                plugin.messages().send(player, "buy-not-active");
                break;
        }
    }

    private void reportCancel(Player player, AuctionService.Result result, AuctionLot lot) {
        switch (result.code()) {
            case OK:
                plugin.messages().send(player, "cancel-success", "id", String.valueOf(lot.id()));
                if (result.dropped()) {
                    plugin.messages().send(player, "buy-dropped");
                }
                play(player, "click");
                break;
            case NOT_OWNER:
                plugin.messages().send(player, "cancel-not-owner");
                break;
            case NOT_ACTIVE:
                plugin.messages().send(player, "cancel-not-active");
                break;
            case NOT_FOUND:
                plugin.messages().send(player, "buy-not-found");
                break;
            default:
                plugin.messages().send(player, "cancel-not-active");
                break;
        }
    }

    private void reportClaim(Player player, AuctionService.Result result, AuctionLot lot) {
        switch (result.code()) {
            case OK:
                plugin.messages().send(player, "claim-success",
                        "item", Items.displayName(lot.item()),
                        "amount", String.valueOf(lot.amount()));
                if (result.dropped()) {
                    plugin.messages().send(player, "buy-dropped");
                }
                play(player, "purchase");
                break;
            case NOT_OWNER:
                plugin.messages().send(player, "claim-not-owner");
                break;
            case NOT_ACTIVE:
                plugin.messages().send(player, "claim-not-active");
                break;
            case NOT_FOUND:
                plugin.messages().send(player, "buy-not-found");
                break;
            default:
                plugin.messages().send(player, "claim-not-active");
                break;
        }
    }

    private void play(Player player, String sound) {
        try {
            Sound value = "purchase".equals(sound) ? Sound.ENTITY_PLAYER_LEVELUP : Sound.UI_BUTTON_CLICK;
            player.playSound(player.getLocation(), value, 0.6f, "purchase".equals(sound) ? 1.4f : 1.2f);
        } catch (Throwable ignored) {
            // sounds are cosmetic
        }
    }
}
