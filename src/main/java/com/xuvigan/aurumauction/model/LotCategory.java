package com.xuvigan.aurumauction.model;

import org.bukkit.Material;

/** Coarse category used by the GUI and the category filter. */
public enum LotCategory {

    TOOLS,
    COMBAT,
    FOOD,
    POTIONS,
    REDSTONE,
    BLOCKS,
    OTHER;

    public static LotCategory of(Material material) {
        if (material == null || material == Material.AIR) {
            return OTHER;
        }
        String name = material.name();
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL")
                || name.contains("HOE") || name.contains("SHEARS") || name.contains("FISHING_ROD")
                || name.contains("FLINT_AND_STEEL") || name.contains("BRUSH")) {
            return TOOLS;
        }
        if (name.contains("SWORD") || name.contains("BOW") || name.contains("ARROW")
                || name.contains("TRIDENT") || name.contains("SHIELD") || name.contains("HELMET")
                || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS")
                || name.contains("ELYTRA") || name.contains("TOTEM")) {
            return COMBAT;
        }
        if (name.contains("POTION") || name.contains("BOTTLE") || name.contains("SPIDER_EYE")
                || name.contains("FERMENTED")) {
            return POTIONS;
        }
        if (material.isEdible()) {
            return FOOD;
        }
        if (name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("PISTON") || name.contains("OBSERVER") || name.contains("DISPENSER")
                || name.contains("DROPPER") || name.contains("HOPPER") || name.contains("RAIL")
                || name.contains("MINECART") || name.contains("BUTTON") || name.contains("LEVER")
                || name.contains("PRESSURE_PLATE") || name.contains("TRIPWIRE") || name.contains("TNT")) {
            return REDSTONE;
        }
        if (material.isBlock()) {
            return BLOCKS;
        }
        return OTHER;
    }

    public String messageKey() {
        return "category-" + name().toLowerCase(java.util.Locale.ROOT);
    }
}
