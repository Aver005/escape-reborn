package me.aver005.escape.arena;

import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.menu.ChestSetupMenu;
import me.aver005.escape.player.PlayerSnapshot;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Мастер настройки категорий сундуков: телепортирует админа по точкам арены и
 * выдаёт жезлы-раздатчики (ПКМ жезлом = назначить его категорию текущей точке и
 * перейти к следующей). Работает ТОЛЬКО вне матча. Инвентарь админа стешится
 * через {@link PlayerSnapshot} — крашоустойчиво: при выходе снапшот остаётся на
 * диске и его подхватывает восстановление в GameListener.onJoin.
 */
public class ChestSetupManager
{
    public static final String WAND_TAG = "chestwand";
    public static final String EXIT_TAG = "chestwand-exit";
    public static final String NEXT_TAG = "chestwand-next";
    private static final int EXIT_SLOT = 8;
    private static final int NEXT_SLOT = 7;

    private final EscapePlugin plugin;
    private final Map<UUID, WizardState> active = new HashMap<>();

    public ChestSetupManager(EscapePlugin plugin) {this.plugin = plugin;}

    public boolean isActive(Player p) {return active.containsKey(p.getUniqueId());}
    public boolean isActive(UUID id) {return active.containsKey(id);}

    /** Идёт ли настройка на этой арене (блокирует вход игроков / старт матча). */
    public boolean isArenaBusy(Arena arena)
    {
        for (WizardState st : active.values())
        {
            if (st.getArena() == arena) {return true;}
        }
        return false;
    }

    public void start(Player p, Arena arena)
    {
        if (isActive(p)) {Msg.send(p, "chestsetup.already"); return;}
        if (plugin.arenas().sessionOf(p) != null || plugin.arenas().sessionOf(arena) != null)
        {
            Msg.send(p, "chestsetup.arena-busy", Msg.ph("arena", arena.getId()));
            return;
        }
        if (plugin.loot().isEmpty())
        {
            Msg.send(p, "chestsetup.no-categories", Msg.ph("arena", arena.getId()));
            return;
        }
        if (EscapeArena.chestSpots(arena).isEmpty())
        {
            Msg.send(p, "chestsetup.no-points", Msg.ph("arena", arena.getId()));
            return;
        }
        if (PlayerSnapshot.exists(plugin, p.getUniqueId()))
        {
            Msg.send(p, "chestsetup.snapshot-exists");
            return;
        }

        PlayerSnapshot.save(plugin, p);
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE); // нельзя случайно сломать/поставить блок
        giveWands(p, arena);

        WizardState state = new WizardState(p.getUniqueId(), arena,
            new ArrayList<>(EscapeArena.chestSpots(arena).keySet()));
        active.put(p.getUniqueId(), state);
        DebugLog.log(Cat.ADMIN, "chestsetup-start admin=%s arena=%s points=%d categories=%d",
            p.getName(), arena.getId(), state.size(), plugin.loot().all().size());
        Msg.send(p, "chestsetup.started", Msg.ph("arena", arena.getId()), Msg.ph("n", state.size()));
        goTo(p, 0);
    }

    private void giveWands(Player p, Arena arena)
    {
        // спец-предметы на фикс. слоты, потом жезлы (addItem их не займёт)
        p.getInventory().setItem(EXIT_SLOT, Items.special(Material.BARRIER,
            Msg.get("chestsetup.exit-name"), Msg.getList("chestsetup.exit-lore"), EXIT_TAG));
        p.getInventory().setItem(NEXT_SLOT, Items.special(Material.SPECTRAL_ARROW,
            Msg.get("chestsetup.next-name"), Msg.getList("chestsetup.next-lore"), NEXT_TAG));
        // жезлы — по одному на каждую ГЛОБАЛЬНУЮ категорию лута
        for (LootCategory cat : plugin.loot().all())
        {
            ItemStack wand = Items.special(cat.getIcon(), Msg.mm(cat.getNameRaw()),
                Msg.getList("chestsetup.wand-lore",
                    Msg.ph("weight", cat.getWeight()),
                    Msg.ph("min-per-chest", lim(cat.getMinPerChest())),
                    Msg.ph("max-per-chest", lim(cat.getMaxPerChest())),
                    Msg.ph("min-chests", lim(cat.getMinChests())),
                    Msg.ph("max-chests", lim(cat.getMaxChests())),
                    Msg.ph("refill", cat.getRefillSeconds())),
                WAND_TAG);
            ItemMeta meta = wand.getItemMeta();
            meta.getPersistentDataContainer().set(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING, cat.getId());
            wand.setItemMeta(meta);
            p.getInventory().addItem(wand);
        }
    }

    /** Значение лимита для лора: {@code -1} → символ «без ограничения», иначе число. */
    private String lim(int v)
    {
        return v == LootCategory.UNLIMITED ? Msg.raw("chestsetup.unlimited") : String.valueOf(v);
    }

    /**
     * ПКМ жезлом: ПЕРЕКЛЮЧИТЬ его категорию на текущей точке (мультивыбор).
     * НЕ переходит к следующей точке — для этого есть отдельный предмет «дальше»
     * ({@link #NEXT_TAG}) и метод {@link #next(Player)}.
     */
    public void assign(Player p, String categoryId)
    {
        WizardState state = active.get(p.getUniqueId());
        if (state == null) {return;}
        Arena arena = state.getArena();
        LootCategory cat = plugin.loot().get(categoryId);
        if (cat == null) {Msg.send(p, "chestsetup.bad-category"); return;}
        Location point = state.current();
        if (point == null) {advance(p); return;}

        List<String> cats = EscapeArena.chestSpots(arena).computeIfAbsent(point, k -> new ArrayList<>());
        boolean added;
        if (cats.remove(cat.getId())) {added = false;}
        else {cats.add(cat.getId()); added = true;}
        plugin.arenas().save(arena);

        String key = added ? "chestsetup.assigned" : "chestsetup.removed";
        p.playSound(p.getLocation(), added ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS,
            1f, added ? 1.4f : 0.7f);
        p.sendActionBar(Msg.get(key,
            Msg.ph("index", state.getIndex() + 1), Msg.ph("total", state.size()),
            Msg.phMm("category", cat.getNameRaw()), Msg.ph("count", cats.size())));
        DebugLog.log(Cat.ADMIN, "chestsetup-toggle admin=%s arena=%s point=%s cat=%s added=%b count=%d",
            p.getName(), arena.getId(), DebugLog.at(point), cat.getId(), added, cats.size());
    }

    /** Явный переход к следующей точке (предмет «дальше»/финиш мастера). */
    public void next(Player p) {advance(p);}

    /** Переход к следующей точке; на последней — завершение мастера. */
    public void advance(Player p)
    {
        WizardState state = active.get(p.getUniqueId());
        if (state == null) {return;}
        int next = state.getIndex() + 1;
        if (next >= state.size())
        {
            Msg.send(p, "chestsetup.done",
                Msg.ph("arena", state.getArena().getId()), Msg.ph("n", state.size()));
            stop(p, true);
            return;
        }
        goTo(p, next);
    }

    private void goTo(Player p, int i)
    {
        WizardState state = active.get(p.getUniqueId());
        if (state == null) {return;}
        state.setIndex(i);
        Location point = state.getOrder().get(i);
        if (point.getWorld() == null) {advance(p); return;}
        point.getChunk().load();
        SetupMarkers.placePoint(state.getArena(), point, "chest"); // гарантируем видимый сундук
        p.teleport(point.clone().add(0.5, 1.0, 0.5));

        List<String> ids = EscapeArena.chestSpots(state.getArena()).get(point);
        p.sendActionBar(Msg.get("chestsetup.progress",
            Msg.ph("index", i + 1), Msg.ph("total", state.size()),
            Msg.ph("count", ids == null ? 0 : ids.size()),
            Msg.phC("categories", categoryList(ids))));
    }

    /** Список имён категорий точки через запятую или «без категории», если их нет. */
    private Component categoryList(List<String> ids)
    {
        if (ids == null || ids.isEmpty()) {return Msg.get("chestsetup.point-unset");}
        List<Component> names = new ArrayList<>();
        for (String id : ids)
        {
            LootCategory cat = plugin.loot().get(id);
            if (cat != null) {names.add(Msg.mm(cat.getNameRaw()));}
        }
        if (names.isEmpty()) {return Msg.get("chestsetup.point-unset");}
        return Component.join(JoinConfiguration.separator(Component.text(", ")), names);
    }

    /** Прыжок к точке по номеру (из GUI). */
    public void jumpTo(Player p, int i)
    {
        WizardState state = active.get(p.getUniqueId());
        if (state == null || i < 0 || i >= state.size()) {return;}
        goTo(p, i);
    }

    public void openGui(Player p, Arena arena)
    {
        if (!isActive(p)) {start(p, arena);}
        WizardState state = active.get(p.getUniqueId());
        if (state == null) {return;} // старт отклонён guard'ами (сообщение уже отправлено)
        new ChestSetupMenu(plugin, this, state).open(p);
    }

    /** Явный выход (предмет/команда/финиш): восстановить игрока и удалить снапшот. */
    public void stop(Player p, boolean completed)
    {
        WizardState state = active.remove(p.getUniqueId());
        if (state == null) {return;}
        PlayerSnapshot.restore(plugin, p);
        DebugLog.log(Cat.ADMIN, "chestsetup-stop admin=%s arena=%s completed=%b",
            p.getName(), state.getArena().getId(), completed);
        if (!completed) {Msg.send(p, "chestsetup.stopped");}
    }

    /**
     * Выход/дисконнект игрока в мастере: снимаем из активных, но снапшот на диске
     * НЕ трогаем — его восстановит GameListener.onJoin при следующем входе
     * (тот же путь, что и после краша сервера). Так вещи не теряются.
     */
    public void onQuit(UUID id)
    {
        if (active.remove(id) != null)
        {
            DebugLog.log(Cat.ADMIN, "chestsetup-quit uuid=%s (snapshot kept for onJoin restore)", id);
        }
    }

    /** onDisable/reload: восстановить всех, кто онлайн и в мастере. */
    public void stopAll()
    {
        for (UUID id : new ArrayList<>(active.keySet()))
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {stop(p, false);}
            else {active.remove(id);}
        }
    }
}
