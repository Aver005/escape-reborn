package me.aver005.escape.listener;

import me.aver005.escape.menu.Menu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Маршрутизация кликов в Menu. */
public class MenuListener implements Listener
{
    @EventHandler
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {return;}
        int topSize = e.getView().getTopInventory().getSize();
        int raw = e.getRawSlot();
        if (!menu.allowsInteraction()) {e.setCancelled(true);}
        else if (raw >= 0 && raw < topSize && menu.isProtectedSlot(raw)) {e.setCancelled(true);}
        menu.onClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e)
    {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {return;}
        int topSize = e.getView().getTopInventory().getSize();
        if (menu.allowsInteraction())
        {
            // в интерактивном меню тянуть можно, но НЕ на защищённые кнопки управления
            for (int slot : e.getRawSlots())
            {
                if (slot < topSize && menu.isProtectedSlot(slot)) {e.setCancelled(true); return;}
            }
            return;
        }
        for (int slot : e.getRawSlots())
        {
            if (slot < topSize) {e.setCancelled(true); return;}
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e)
    {
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) {menu.onClose(e);}
    }
}
