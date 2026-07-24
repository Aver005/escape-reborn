package me.aver005.escape.menu;

import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Точки арены: выдача маркеров-предметов для разметки (spawn/finalspawn/chest/
 * table/ore/breakable), счётчики уже размеченных точек, установка лобби в текущую
 * позицию и расстановка блоков-подсказок. Рычаг/житель требуют имя/тип — для них
 * подсказка-команда. Управление точками жителей и категориями сундуков — в хабе.
 */
public class ArenaPointsMenu extends Menu
{
    private static final int SLOT_SPAWN = 10;
    private static final int SLOT_FINAL = 11;
    private static final int SLOT_CHEST = 12;
    private static final int SLOT_TABLE = 13;
    private static final int SLOT_ORE = 14;
    private static final int SLOT_BREAKABLE = 15;
    private static final int SLOT_LEVER = 16;
    private static final int SLOT_VILLAGER = 17;
    private static final int SLOT_LOBBY = 30;
    private static final int SLOT_MARKERS = 32;
    private static final int SLOT_BACK_HUB = 49;

    private final EscapePlugin plugin;
    private final Arena arena;

    public ArenaPointsMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("arena-points.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
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
        inventory.setItem(SLOT_SPAWN, marker(Material.BEACON, "spawn", arena.getSpawns().size(), false));
        inventory.setItem(SLOT_FINAL, marker(Material.LODESTONE, "finalspawn", EscapeArena.finalSpawns(arena).size(), false));
        inventory.setItem(SLOT_CHEST, marker(Material.CHEST, "chest", EscapeArena.chestSpots(arena).size(), true));
        inventory.setItem(SLOT_TABLE, marker(Material.ENCHANTING_TABLE, "table", EscapeArena.tableSpots(arena).size(), false));
        inventory.setItem(SLOT_ORE, marker(Material.STONE, "ore", EscapeArena.oreSpots(arena).size(), false));
        inventory.setItem(SLOT_BREAKABLE, marker(Material.IRON_AXE, "breakable", EscapeArena.breakables(arena).size(), false));
        inventory.setItem(SLOT_LEVER, hintButton(Material.LEVER, "lever", EscapeArena.levers(arena).size()));
        inventory.setItem(SLOT_VILLAGER, hintButton(Material.VILLAGER_SPAWN_EGG, "villager", EscapeArena.traderSpots(arena).size()));

        List<Component> lobbyLore = new ArrayList<>();
        lobbyLore.add(Msg.get("arena-points.lobby-current", Msg.ph("value", Msg.raw(arena.getLobby() != null ? "arena-points.lobby-yes" : "arena-points.lobby-no"))));
        lobbyLore.addAll(Msg.getList("arena-points.lobby-lore"));
        inventory.setItem(SLOT_LOBBY, Items.named(Material.RED_BED, Msg.get("arena-points.lobby-name"), lobbyLore));
        inventory.setItem(SLOT_MARKERS, Items.named(Material.ARMOR_STAND,
            Msg.get("arena-points.markers-name"), Msg.getList("arena-points.markers-lore")));

        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK_HUB, Items.named(Material.OAK_DOOR,
            Msg.get("arena-hub.back-name"), Msg.getList("arena-hub.back-lore")));
    }

    /** Кнопка выдачи маркера точки: имя + счётчик + подсказка «получить маркер». */
    private ItemStack marker(Material mat, String type, int count, boolean chestExtra)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("arena-points.count", Msg.ph("n", count)));
        if (chestExtra) {lore.addAll(Msg.getList("arena-points.chest-extra"));}
        lore.addAll(Msg.getList("arena-points.give-lore"));
        return Items.named(mat, Msg.get("arena-points.name-" + type), lore);
    }

    /** Кнопка-подсказка (рычаг/житель): требует имя/тип — только команда. */
    private ItemStack hintButton(Material mat, String type, int count)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("arena-points.count", Msg.ph("n", count)));
        lore.addAll(Msg.getList("arena-points." + type + "-lore"));
        return Items.named(mat, Msg.get("arena-points.name-" + type), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_SPAWN -> give(p, "spawn", Material.BEACON);
            case SLOT_FINAL -> give(p, "finalspawn", Material.LODESTONE);
            case SLOT_CHEST -> give(p, "chest", Material.CHEST);
            case SLOT_TABLE -> give(p, "table", Material.ENCHANTING_TABLE);
            case SLOT_ORE -> give(p, "ore", Material.STONE);
            case SLOT_BREAKABLE -> giveBreakable(p);
            case SLOT_LEVER -> Msg.send(p, "arena-points.lever-hint", Msg.ph("arena", arena.getId()));
            case SLOT_VILLAGER -> Msg.send(p, "arena-points.villager-hint", Msg.ph("arena", arena.getId()));
            case SLOT_LOBBY -> setLobby(p);
            case SLOT_MARKERS -> placeMarkers(p);
            case SLOT_BACK_HUB -> new ArenaHubMenu(plugin, arena).open(p);
            default -> {}
        }
    }

    private void give(Player p, String type, Material material)
    {
        p.getInventory().addItem(SetupMarkers.markerItem(arena, type, material, null));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        Msg.send(p, "admin.marker-given", Msg.ph("type", Msg.raw("marker-types." + type)));
    }

    private void giveBreakable(Player p)
    {
        p.getInventory().addItem(SetupMarkers.breakWand(arena));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        Msg.send(p, "breakable.wand-given", Msg.ph("arena", arena.getId()), Msg.ph("n", EscapeArena.breakables(arena).size()));
    }

    private void setLobby(Player p)
    {
        arena.setLobby(p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
        arena.setWorldName(p.getWorld().getName());
        plugin.arenas().save(arena);
        DebugLog.log(Cat.ADMIN, "arena-lobby admin=%s arena=%s at=%s", p.getName(), arena.getId(), DebugLog.at(p.getLocation()));
        Msg.send(p, "admin.lobby-set", Msg.ph("arena", arena.getId()));
        render();
    }

    private void placeMarkers(Player p)
    {
        if (arena.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return;}
        if (plugin.arenas().sessionOf(arena) != null) {Msg.send(p, "admin.markers-busy", Msg.ph("arena", arena.getId())); return;}
        int n = SetupMarkers.placeAll(arena);
        Msg.send(p, "admin.markers-placed", Msg.ph("arena", arena.getId()), Msg.ph("n", n));
    }
}
