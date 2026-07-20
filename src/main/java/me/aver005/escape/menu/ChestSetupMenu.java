package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ChestSetupManager;
import me.aver005.escape.arena.WizardState;
import me.aver005.escape.category.ChestCategory;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Выбор точки сундука в мастере: пагинированный список всех точек арены
 * (иконка = текущая категория точки), клик = прыжок мастера к этой точке.
 */
public class ChestSetupMenu extends Menu
{
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final ChestSetupManager manager;
    private final WizardState state;
    private final Map<Integer, Integer> pointBySlot = new HashMap<>();
    private int page;

    public ChestSetupMenu(EscapePlugin plugin, ChestSetupManager manager, WizardState state)
    {
        super(54, Msg.get("chestsetup.menu-title"));
        this.manager = manager;
        this.state = state;
        render();
    }

    private void render()
    {
        inventory.clear();
        pointBySlot.clear();
        Arena arena = state.getArena();
        List<Location> order = state.getOrder();
        int pages = Math.max(1, (order.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}

        int from = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++)
        {
            int idx = from + slot;
            if (idx >= order.size()) {break;}
            Location point = order.get(idx);
            String catId = arena.getChestSpots().get(point);
            ChestCategory cat = arena.getChestCategory(catId);
            Material icon = cat != null ? cat.getIcon() : Material.CHEST;
            Component name = Msg.get("chestsetup.point-name",
                Msg.ph("index", idx + 1),
                Msg.ph("x", point.getBlockX()), Msg.ph("y", point.getBlockY()), Msg.ph("z", point.getBlockZ()));
            List<Component> lore = new ArrayList<>();
            lore.add(cat != null
                ? Msg.get("chestsetup.point-category", Msg.phMm("category", cat.getNameRaw()))
                : Msg.get("chestsetup.point-orphan", Msg.ph("id", String.valueOf(catId))));
            lore.addAll(Msg.getList("chestsetup.point-lore"));
            inventory.setItem(slot, Items.named(icon, name, lore));
            pointBySlot.put(slot, idx);
        }

        inventory.setItem(SLOT_INFO, Items.named(Material.PAPER,
            Msg.get("chestsetup.page-info", Msg.ph("page", page + 1), Msg.ph("pages", pages))));
        if (page > 0)
        {
            inventory.setItem(SLOT_PREV, Items.named(Material.ARROW, Msg.get("chestsetup.page-prev")));
        }
        if (page < pages - 1)
        {
            inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("chestsetup.page-next")));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!manager.isActive(p)) {p.closeInventory(); return;}
        int slot = e.getRawSlot();
        if (slot == SLOT_PREV && page > 0) {page--; render(); return;}
        if (slot == SLOT_NEXT) {page++; render(); return;}
        Integer idx = pointBySlot.get(slot);
        if (idx == null) {return;}
        manager.jumpTo(p, idx);
        p.closeInventory();
    }
}
