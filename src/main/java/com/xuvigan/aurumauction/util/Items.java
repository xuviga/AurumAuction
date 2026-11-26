package com.xuvigan.aurumauction.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Item (de)serialisation and shulker helpers. Bukkit's object streams keep the
 * full item meta (NBT, custom model data, enchantments) without extra libs.
 */
public final class Items {

    private Items() {
    }

    public static String encode(ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            out.writeObject(item);
            out.flush();
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static ItemStack decode(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(data);
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
            Object value = in.readObject();
            in.close();
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static boolean isShulker(ItemStack item) {
        if (item == null || item.getType() == null) {
            return false;
        }
        return item.getType().name().endsWith("SHULKER_BOX");
    }

    /** Contents of a shulker box item; empty list when the item is not one. */
    public static List<ItemStack> shulkerContents(ItemStack item) {
        if (!isShulker(item)) {
            return Collections.emptyList();
        }
        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof BlockStateMeta)) {
                return Collections.emptyList();
            }
            BlockState state = ((BlockStateMeta) meta).getBlockState();
            if (!(state instanceof ShulkerBox)) {
                return Collections.emptyList();
            }
            Inventory inventory = ((ShulkerBox) state).getInventory();
            List<ItemStack> out = new ArrayList<ItemStack>();
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack != null && stack.getType() != Material.AIR) {
                    out.add(stack.clone());
                }
            }
            return out;
        } catch (Throwable throwable) {
            return Collections.emptyList();
        }
    }

    /** Plain display name: custom name without colours or a humanised material name. */
    public static String displayName(ItemStack item) {
        if (item == null || item.getType() == null) {
            return "?";
        }
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String stripped = ChatColor.stripColor(meta.getDisplayName());
                if (stripped != null && !stripped.trim().isEmpty()) {
                    return stripped.trim();
                }
            }
        } catch (Throwable ignored) {
            // fall through to the material name
        }
        return humanise(item.getType());
    }

    public static String humanise(Material material) {
        if (material == null) {
            return "?";
        }
        String[] parts = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return out.toString();
    }
}
