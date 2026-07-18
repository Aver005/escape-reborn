package me.aver005.escape.arena;

import org.bukkit.inventory.ItemStack;

/** Запись пула лута: предмет и вес (1–250). */
public record WeightedItem(ItemStack item, int weight) {}
