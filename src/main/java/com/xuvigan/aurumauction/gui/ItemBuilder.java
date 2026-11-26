package com.xuvigan.aurumauction.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.xuvigan.aurumauction.util.Text;

/** Small 1.16-safe item factory (no Adventure, no item components). */
public final class ItemBuilder {

    private static final Material FALLBACK = Material.matchMaterial("GRAY_STAINED_GLASS_PANE") == null
            ? Material.STONE : Material.matchMaterial("GRAY_STAINED_GLASS_PANE");

    private final ItemStack stack;

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material == null ? FALLBACK : material, 1));
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(new ItemStack(material == null ? FALLBACK : material, Math.max(1, Math.min(64, amount))));
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name == null ? " " : name));
            stack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return this;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(Text.color(lore));
            stack.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> list = new ArrayList<String>();
        if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
                if (lines[i] != null) {
                    list.add(lines[i]);
                }
            }
        }
        return lore(list);
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder customModelData(int modelData) {
        if (modelData <= 0) {
            return this;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            try {
                meta.setCustomModelData(Integer.valueOf(modelData));
                stack.setItemMeta(meta);
            } catch (Throwable ignored) {
                // pre-1.14 server
            }
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (!glow) {
            return this;
        }
        org.bukkit.enchantments.Enchantment enchantment = resolveEnchantment();
        if (enchantment == null) {
            return this;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return this;
        }
        try {
            meta.addEnchant(enchantment, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        } catch (Throwable ignored) {
            // leave the item plain
        }
        return this;
    }

    private static org.bukkit.enchantments.Enchantment resolveEnchantment() {
        String[] names = {"DURABILITY", "UNBREAKING"};
        for (int i = 0; i < names.length; i++) {
            try {
                Object value = org.bukkit.enchantments.Enchantment.class.getField(names[i]).get(null);
                if (value instanceof org.bukkit.enchantments.Enchantment) {
                    return (org.bukkit.enchantments.Enchantment) value;
                }
            } catch (Throwable ignored) {
                // try the next constant
            }
        }
        return null;
    }

    public ItemStack build() {
        return stack;
    }
}
