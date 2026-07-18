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
        if (!menu.allowsInteraction()) {e.setCancelled(true);}
        menu.onClick(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e)
    {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {return;}
        if (menu.allowsInteraction()) {return;}
        int topSize = e.getView().getTopInventory().getSize();
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
