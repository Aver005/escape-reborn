package me.aver005.escape.menu;

import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Админ-навигатор по точкам жителей арены (аналог GUI точек сундуков): показывает
 * все точки торговцев с назначенным типом. ЛКМ — телепорт к точке, ПКМ —
 * открыть редактор товаров этого жителя. Только вне матча (телепорт в мир арены).
 */
public class VillagerPointsMenu extends Menu
{
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final EscapePlugin plugin;
    private final Arena arena;
    private final List<Location> order;
    private final Map<Integer, Integer> pointBySlot = new HashMap<>();
    private int page;

    public VillagerPointsMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("villagers.menu-title").append(Component.text(" " + arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
        this.order = new ArrayList<>(EscapeArena.traderSpots(arena).keySet());
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        pointBySlot.clear();
        int pages = Math.max(1, (order.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}

        int from = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++)
        {
            int idx = from + slot;
            if (idx >= order.size()) {break;}
            Location point = order.get(idx);
            String traderId = EscapeArena.traderSpots(arena).get(point);
            TraderType trader = traderId != null ? plugin.traders().get(traderId) : null;

            Component name = Msg.get("villagers.point-name",
                Msg.ph("index", idx + 1),
                Msg.ph("x", point.getBlockX()), Msg.ph("y", point.getBlockY()), Msg.ph("z", point.getBlockZ()));
            List<Component> lore = new ArrayList<>();
            if (trader != null)
            {
                lore.add(Msg.get("villagers.point-trader",
                    Msg.ph("id", traderId), Msg.phC("name", trader.displayName())));
            }
            else
            {
                lore.add(Msg.get("villagers.point-orphan", Msg.ph("id", String.valueOf(traderId))));
            }
            lore.addAll(Msg.getList("villagers.point-lore"));
            inventory.setItem(slot, Items.named(Material.VILLAGER_SPAWN_EGG, name, lore));
            pointBySlot.put(slot, idx);
        }

        inventory.setItem(SLOT_INFO, Items.named(Material.PAPER,
            Msg.get("villagers.page-info", Msg.ph("page", page + 1), Msg.ph("pages", pages),
                Msg.ph("n", order.size()))));
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
        int raw = e.getRawSlot();
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT) {page++; render(); return;}

        Integer idx = pointBySlot.get(raw);
        if (idx == null || idx < 0 || idx >= order.size()) {return;}
        Location point = order.get(idx);
        if (plugin.arenas().sessionOf(arena) != null) {Msg.send(p, "villagers.arena-busy", Msg.ph("arena", arena.getId())); return;}

        if (e.isRightClick())
        {
            String traderId = EscapeArena.traderSpots(arena).get(point);
            TraderType trader = traderId != null ? plugin.traders().get(traderId) : null;
            if (trader == null) {Msg.send(p, "villagers.no-trader"); return;}
            new TradeListEditorMenu(plugin, trader).open(p);
            return;
        }

        if (point.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return;}
        point.getChunk().load();
        p.closeInventory();
        p.teleport(point.clone().add(0.5, 1.0, 0.5));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        DebugLog.log(Cat.ADMIN, "villager-teleport admin=%s arena=%s point=%s",
            p.getName(), arena.getId(), DebugLog.at(point));
        Msg.send(p, "villagers.teleported",
            Msg.ph("index", idx + 1),
            Msg.ph("x", point.getBlockX()), Msg.ph("y", point.getBlockY()), Msg.ph("z", point.getBlockZ()));
    }
}
