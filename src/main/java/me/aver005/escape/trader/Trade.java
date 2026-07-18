package me.aver005.escape.trader;

import org.bukkit.inventory.ItemStack;

/** Один товар торговца: предмет и цена в золоте. */
public record Trade(ItemStack item, int price) {}
