package me.aver005.escape.menu;

import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Общий хаб настройки арены (/escape gui &lt;ID&gt;): из него доступна любая
 * настройка. GUI-разделы (параметры, точки, касты, контракты, глобальные
 * редакторы) открываются напрямую; действия с чат-выводом (проверка, worldsetup,
 * вкл/выкл, старт/стоп, жители, мастер сундуков) переиспользуют команды через
 * performCommand — без дублирования логики и гардов. Имя/описание — наковальня.
 */
public class ArenaHubMenu extends Menu
{
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_SETTINGS = 10;
    private static final int SLOT_POINTS = 11;
    private static final int SLOT_CHESTCAT = 12;
    private static final int SLOT_VILLAGERS = 13;
    private static final int SLOT_KITS = 14;
    private static final int SLOT_CONTRACTS = 15;
    private static final int SLOT_NAME = 16;
    private static final int SLOT_DESC = 17;
    private static final int SLOT_CHECK = 19;
    private static final int SLOT_WORLD = 20;
    private static final int SLOT_TOGGLE = 21;
    private static final int SLOT_START = 22;
    private static final int SLOT_STOP = 23;
    private static final int SLOT_LOOT = 28;
    private static final int SLOT_TRADERS = 29;
    private static final int SLOT_GCONTRACTS = 30;
    private static final int SLOT_THEMES = 31;
    private static final int SLOT_MODIFIERS = 32;
    private static final int SLOT_CLOSE = 49;

    private final EscapePlugin plugin;
    private final Arena arena;

    public ArenaHubMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("arena-hub.title").append(Component.text(arena.getId())));
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
        inventory.setItem(SLOT_STATUS, status());

        inventory.setItem(SLOT_SETTINGS, btn(Material.COMPARATOR, "settings"));
        inventory.setItem(SLOT_POINTS, btn(Material.BEACON, "points"));
        inventory.setItem(SLOT_CHESTCAT, btn(Material.CHEST, "chestcat"));
        inventory.setItem(SLOT_VILLAGERS, btn(Material.VILLAGER_SPAWN_EGG, "villagers"));
        inventory.setItem(SLOT_KITS, btn(Material.IRON_CHESTPLATE, "kits"));
        inventory.setItem(SLOT_CONTRACTS, btn(Material.WRITABLE_BOOK, "contracts"));
        inventory.setItem(SLOT_NAME, btn(Material.NAME_TAG, "name"));
        inventory.setItem(SLOT_DESC, btn(Material.OAK_SIGN, "desc"));

        inventory.setItem(SLOT_CHECK, btn(Material.SPYGLASS, "check"));
        inventory.setItem(SLOT_WORLD, btn(Material.GRASS_BLOCK, "world"));
        inventory.setItem(SLOT_TOGGLE, arena.isEnabled()
            ? btn(Material.GRAY_DYE, "disable") : btn(Material.LIME_DYE, "enable"));
        inventory.setItem(SLOT_START, btn(Material.LIME_CONCRETE, "start"));
        inventory.setItem(SLOT_STOP, btn(Material.RED_CONCRETE, "stop"));

        inventory.setItem(SLOT_LOOT, btn(Material.BUNDLE, "loot"));
        inventory.setItem(SLOT_TRADERS, btn(Material.EMERALD, "traders"));
        inventory.setItem(SLOT_GCONTRACTS, btn(Material.WRITTEN_BOOK, "gcontracts"));
        inventory.setItem(SLOT_THEMES, btn(Material.ENDER_EYE, "themes"));
        inventory.setItem(SLOT_MODIFIERS, btn(Material.SUNFLOWER, "modifiers"));

        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_CLOSE, Items.named(Material.BARRIER,
            Msg.get("arena-hub.close-name"), Msg.getList("arena-hub.close-lore")));
    }

    private ItemStack btn(Material mat, String key)
    {
        return Items.named(mat, Msg.get("arena-hub.btn-" + key + "-name"),
            Msg.getList("arena-hub.btn-" + key + "-lore"));
    }

    private ItemStack status()
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("arena-hub.status-id", Msg.ph("id", arena.getId()))));
        lore.add(Items.flat(Msg.get("arena-hub.status-world",
            Msg.ph("world", arena.getWorldName() == null ? Msg.raw("arena-hub.status-none") : arena.getWorldName()))));
        lore.add(Items.flat(Msg.get("arena-hub.status-enabled",
            Msg.ph("value", Msg.raw(arena.isEnabled() ? "arena-hub.status-yes" : "arena-hub.status-no")))));
        lore.add(Items.flat(Msg.get("arena-hub.status-players",
            Msg.ph("min", arena.getMinPlayers()), Msg.ph("max", arena.getMaxPlayers()))));
        lore.add(Items.flat(Msg.get("arena-hub.status-session",
            Msg.ph("value", Msg.raw(plugin.arenas().sessionOf(arena) != null ? "arena-hub.status-running" : "arena-hub.status-idle")))));
        lore.add(Items.flat(Msg.get("arena-hub.status-points",
            Msg.ph("spawns", arena.getSpawns().size()),
            Msg.ph("chests", EscapeArena.chestSpots(arena).size()),
            Msg.ph("traders", EscapeArena.traderSpots(arena).size()),
            Msg.ph("kits", plugin.kitsFor(arena).size()))));
        return Items.named(Material.WRITTEN_BOOK, Items.flat(Msg.mm(arena.getDisplayNameRaw())), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            // GUI-разделы — открываем напрямую
            case SLOT_SETTINGS -> new ArenaSettingsMenu(plugin, arena).open(p);
            case SLOT_POINTS -> new ArenaPointsMenu(plugin, arena).open(p);
            case SLOT_KITS -> new ArenaKitsMenu(plugin, arena).open(p);
            case SLOT_CONTRACTS -> new ArenaContractsMenu(plugin, arena).open(p);
            case SLOT_LOOT -> new LootEditorMenu(plugin).open(p);
            case SLOT_TRADERS -> new TraderListMenu(plugin).open(p);
            case SLOT_NAME -> editName(p);
            case SLOT_DESC -> editDesc(p);
            // действия/жители/сундуки — через команды (гарды и сообщения уже есть)
            case SLOT_CHESTCAT -> runCommand(p, "chestsetup");
            case SLOT_VILLAGERS -> runCommand(p, "villagers");
            case SLOT_CHECK -> runCommand(p, "check");
            case SLOT_WORLD -> runCommand(p, "worldsetup");
            case SLOT_TOGGLE -> runCommand(p, arena.isEnabled() ? "disable" : "enable");
            case SLOT_START -> runCommand(p, "start");
            case SLOT_STOP -> runCommand(p, "stop");
            // глобальные редакторы без GUI — подсказки
            case SLOT_GCONTRACTS -> Msg.send(p, "arena-hub.hint-gcontracts");
            case SLOT_THEMES -> Msg.send(p, "arena-hub.hint-themes");
            case SLOT_MODIFIERS -> Msg.send(p, "arena-hub.hint-modifiers");
            case SLOT_CLOSE -> p.closeInventory();
            default -> {}
        }
    }

    private void runCommand(Player p, String sub)
    {
        p.closeInventory();
        p.performCommand("escape " + sub + " " + arena.getId());
    }

    private void editName(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("arena-hub.name-anvil-title"), arena.getDisplayNameRaw(), text ->
        {
            arena.setDisplayNameRaw(text);
            plugin.arenas().save(arena);
            Msg.send(p, "admin.name-set", Msg.ph("arena", arena.getId()), Msg.phMm("name", text));
            new ArenaHubMenu(plugin, arena).open(p);
        }).open(p);
    }

    private void editDesc(Player p)
    {
        new AnvilInputMenu(plugin, Msg.get("arena-hub.desc-anvil-title"), arena.getDescriptionRaw(), text ->
        {
            arena.setDescriptionRaw(text);
            plugin.arenas().save(arena);
            Msg.send(p, "admin.desc-set", Msg.ph("arena", arena.getId()), Msg.phMm("description", text));
            new ArenaHubMenu(plugin, arena).open(p);
        }).open(p);
    }
}
