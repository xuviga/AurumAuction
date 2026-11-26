package com.xuvigan.aurumauction.gui;

import java.util.HashMap;
import java.util.Map;

import com.xuvigan.aurumauction.model.LotCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.xuvigan.aurumauction.model.LotQuery;

/**
 * Inventory holder identifying an AurumAuction menu. Carries page, search text
 * and sort order, so the click listener never parses the inventory title.
 */
public final class GuiHolder implements InventoryHolder {

    private final Map<Integer, String> actions = new HashMap<Integer, String>();
    private Inventory inventory;
    private MenuType menu;
    private int page = 1;
    private String subject;
    private LotQuery.Sort sort = LotQuery.Sort.NEWEST;
    private LotCategory category;

    public GuiHolder(MenuType menu) {
        this.menu = menu == null ? MenuType.MAIN : menu;
    }

    public GuiHolder(MenuType menu, int page) {
        this(menu);
        this.page = Math.max(1, page);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType menu() {
        return menu;
    }

    public void menu(MenuType menu) {
        this.menu = menu == null ? MenuType.MAIN : menu;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(1, page);
    }

    public String subject() {
        return subject;
    }

    public GuiHolder subject(String subject) {
        this.subject = subject;
        return this;
    }

    public LotQuery.Sort sort() {
        return sort;
    }

    public GuiHolder sort(LotQuery.Sort sort) {
        this.sort = sort == null ? LotQuery.Sort.NEWEST : sort;
        return this;
    }

    public LotCategory category() {
        return category;
    }

    public GuiHolder category(LotCategory category) {
        this.category = category;
        return this;
    }

    public GuiHolder action(int slot, String key) {
        if (key != null) {
            actions.put(Integer.valueOf(slot), key);
        }
        return this;
    }

    public void clearActions() {
        actions.clear();
    }

    public String action(int slot) {
        return actions.get(Integer.valueOf(slot));
    }
}
