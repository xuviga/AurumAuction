package com.xuvigan.aurumauction.data;

import org.bukkit.inventory.ItemStack;

/** Pluggable item (de)serialisation, overridable in tests. */
public interface ItemCodec {

    String encode(ItemStack item);

    ItemStack decode(String data);
}
