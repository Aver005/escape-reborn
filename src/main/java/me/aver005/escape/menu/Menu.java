package me.aver005.escape.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import me.aver005.escape.util.Items;

/** Базовый GUI: свои инвентари через InventoryHolder (без сравнения заголовков). */
public abstract class Menu implements InventoryHolder
{
    protected Inventory inventory;

    protected Menu(int size, Component title)
    {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {return inventory;}

    public void open(Player p)
    {
        p.openInventory(inventory);
    }

    /** true — разрешить свободное перемещение предметов (редакторы). */
    public boolean allowsInteraction() {return false;}

    public abstract void onClick(InventoryClickEvent e);

    public void onClose(InventoryCloseEvent e) {}

    /** Рамка из панелей по периметру. */
    protected void fillBorder(Material material)
    {
        int size = inventory.getSize();
        ItemStack filler = Items.filler(material);
        for (int i = 0; i < size; i++)
        {
            boolean border = i < 9 || i >= size - 9 || i % 9 == 0 || (i + 1) % 9 == 0;
            if (border && inventory.getItem(i) == null) {inventory.setItem(i, filler);}
        }
    }

    protected void fillAll(Material material)
    {
        ItemStack filler = Items.filler(material);
        for (int i = 0; i < inventory.getSize(); i++)
        {
            if (inventory.getItem(i) == null) {inventory.setItem(i, filler);}
        }
    }
}
