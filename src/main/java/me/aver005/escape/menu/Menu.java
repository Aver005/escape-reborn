package me.aver005.escape.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;

/** Базовый GUI: свои инвентари через InventoryHolder (без сравнения заголовков). */
public abstract class Menu implements InventoryHolder
{
    // общий нижний ряд управления пагинацией (для 54-слотовых меню):
    // содержимое — слоты 0..44, управление — 45..53.
    public static final int PAGE_SIZE = 45;
    protected static final int SLOT_PREV = 45;
    protected static final int SLOT_BACK = 47;
    protected static final int SLOT_PAGE = 49;
    protected static final int SLOT_NEXT = 53;

    protected Inventory inventory;

    protected Menu(int size, Component title)
    {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /** Меню на базе типового инвентаря (например, ANVIL для ввода текста). */
    protected Menu(InventoryType type, Component title)
    {
        this.inventory = Bukkit.createInventory(this, type, title);
    }

    @Override
    public Inventory getInventory() {return inventory;}

    public void open(Player p)
    {
        p.openInventory(inventory);
    }

    /** true — разрешить свободное перемещение предметов (редакторы). */
    public boolean allowsInteraction() {return false;}

    /**
     * true — слот верхнего инвентаря является защищённой кнопкой управления даже
     * в интерактивном меню (клики/дроп по нему отменяются MenuListener'ом).
     * Пагинированные редакторы помечают так нижний ряд управления.
     */
    public boolean isProtectedSlot(int slot) {return false;}

    public abstract void onClick(InventoryClickEvent e);

    public void onClose(InventoryCloseEvent e) {}

    /** Хук для анвил-меню: подготовка слота-результата при вводе текста. */
    public void onPrepareAnvil(PrepareAnvilEvent e) {}

    // ===== пагинация =====

    /** Сколько страниц под items элементов (минимум 1). */
    protected static int pageCount(int items)
    {
        return Math.max(1, (items + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * Нарисовать нижний ряд управления (45..53): счётчик страниц, стрелки
     * «назад/вперёд» (когда есть куда листать) и «Назад» в другое меню
     * (если {@code back}). Остаток ряда — панели {@code fillerMat}.
     */
    protected void renderControls(int page, int pages, boolean back, Material fillerMat)
    {
        for (int i = PAGE_SIZE; i < inventory.getSize(); i++)
        {
            inventory.setItem(i, Items.filler(fillerMat));
        }
        inventory.setItem(SLOT_PAGE, Items.named(Material.PAPER,
            Msg.get("menu.page-info", Msg.ph("page", page + 1), Msg.ph("pages", pages))));
        if (page > 0)
        {
            inventory.setItem(SLOT_PREV, Items.named(Material.ARROW, Msg.get("menu.page-prev")));
        }
        if (page < pages - 1)
        {
            inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("menu.page-next")));
        }
        if (back)
        {
            inventory.setItem(SLOT_BACK, Items.named(Material.OAK_DOOR,
                Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        }
    }

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
