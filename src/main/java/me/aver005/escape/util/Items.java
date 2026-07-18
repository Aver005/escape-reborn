package me.aver005.escape.util;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Хелперы создания предметов. */
public final class Items
{
    private Items() {}

    /** Убирает автокурсив кастомных имён/лора. */
    public static Component flat(Component c)
    {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack named(Material mat, Component name, List<Component> lore)
    {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {meta.displayName(flat(name));}
        if (lore != null && !lore.isEmpty())
        {
            List<Component> flatLore = new ArrayList<>();
            for (Component line : lore) {flatLore.add(flat(line));}
            meta.lore(flatLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack named(Material mat, Component name)
    {
        return named(mat, name, null);
    }

    public static ItemStack special(Material mat, Component name, List<Component> lore, String specialTag)
    {
        ItemStack item = named(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.SPECIAL_ITEM, PersistentDataType.STRING, specialTag);
        item.setItemMeta(meta);
        return item;
    }

    public static String specialTag(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return null;}
        return item.getItemMeta().getPersistentDataContainer().get(Keys.SPECIAL_ITEM, PersistentDataType.STRING);
    }

    public static boolean isSpecial(ItemStack item, String tag)
    {
        return tag.equals(specialTag(item));
    }

    /** Панель-заполнитель без имени. */
    public static ItemStack filler(Material mat)
    {
        return named(mat, Component.space());
    }

    /**
     * Навести компас-проводник игрока на цель (lodestone-трекинг без маяка).
     * false — компаса в инвентаре нет (выпал при смерти и т.п.).
     */
    public static boolean pointAssistantCompass(org.bukkit.entity.Player p, org.bukkit.Location target)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (!isSpecial(item, "assistant")) {continue;}
            if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta meta)) {continue;}
            meta.setLodestoneTracked(false);
            meta.setLodestone(target);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /** Сколько предметов материала в инвентаре игрока. */
    public static int countMaterial(org.bukkit.entity.Player p, Material mat)
    {
        int amount = 0;
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item != null && item.getType() == mat) {amount += item.getAmount();}
        }
        return amount;
    }

    /** Изъять amount предметов материала из инвентаря. */
    public static void takeMaterial(org.bukkit.entity.Player p, Material mat, int amount)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (amount <= 0) {return;}
            if (item == null || item.getType() != mat) {continue;}
            int take = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - take);
            amount -= take;
        }
    }
}
