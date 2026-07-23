package me.aver005.escape.listener;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ChestSetupManager;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Установка блоков-маркеров: добавление точек арены и удаление их сломом. */
public class SetupListener implements Listener
{
    private final EscapePlugin plugin;

    public SetupListener(EscapePlugin plugin) {this.plugin = plugin;}

    @EventHandler
    public void onPlace(BlockPlaceEvent e)
    {
        ItemStack item = e.getItemInHand();
        if (item == null || !item.hasItemMeta()) {return;}
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(Keys.MARKER_TYPE, PersistentDataType.STRING);
        if (type == null) {return;}
        if (!e.getPlayer().hasPermission("escape.admin")) {e.setCancelled(true); return;}

        String arenaId = pdc.get(Keys.MARKER_ARENA, PersistentDataType.STRING);
        String extra = pdc.get(Keys.MARKER_EXTRA, PersistentDataType.STRING);
        Arena arena = plugin.arenas().get(arenaId);
        Player p = e.getPlayer();
        if (arena == null) {Msg.send(p, "errors.arena-not-exists"); e.setCancelled(true); return;}

        Location loc = e.getBlock().getLocation();
        boolean placeReal = false;
        switch (type)
        {
            case "spawn" -> arena.getSpawns().add(loc);
            case "finalspawn" -> arena.getFinalSpawns().add(loc);
            case "chest" ->
            {
                arena.getChestSpots().put(loc, new ArrayList<>());
                // запоминаем, как админ поставил сундук (в BlockPlaceEvent блок уже стоит)
                if (e.getBlockPlaced().getBlockData() instanceof Directional dir)
                {
                    arena.getChestFacings().put(loc, dir.getFacing());
                }
            }
            case "table" -> arena.getTableSpots().add(loc);
            case "ore" -> {arena.getOreSpots().add(loc); placeReal = true;}
            case "lever" -> {arena.getLevers().put(loc, extra == null ? "?" : extra); placeReal = true;}
            case "villager" -> arena.getTraderSpots().put(loc, extra == null ? "?" : extra);
            default -> {return;}
        }
        if (!placeReal) {e.setCancelled(true);}
        plugin.arenas().save(arena);

        // отмена события возвращает блок в прежнее состояние — подсказку ставим следующим тиком
        Bukkit.getScheduler().runTask(plugin, () -> SetupMarkers.placePoint(arena, loc, type));

        Msg.send(p, "admin.marker-added",
            Msg.ph("type", Msg.raw("marker-types." + type)),
            Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()),
            Msg.ph("world", loc.getWorld().getName()));
    }

    /** Слом блока-подсказки вне матча = удаление точки. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e)
    {
        Player p = e.getPlayer();
        if (plugin.arenas().sessionOf(p) != null) {return;} // участников матча ведёт GameListener

        Arena arena = arenaInWorld(e.getBlock().getWorld());
        if (arena == null || arena.getSession() != null) {return;}

        // отметчик категории в руке: ломание сундука-точки = присвоить категорию, а не удалить точку
        if (tryChestTag(p, e.getBlock(), arena, p.getInventory().getItemInMainHand()))
        {
            e.setCancelled(true);
            return;
        }

        Location loc = e.getBlock().getLocation();
        if (SetupMarkers.pointTypeAt(arena, loc) == null) {return;}
        if (!p.hasPermission("escape.admin")) {e.setCancelled(true); return;}

        String type = SetupMarkers.removeAt(arena, loc);
        if (type == null) {return;}
        plugin.arenas().save(arena);
        e.setDropItems(false);

        Msg.send(p, "admin.marker-removed",
            Msg.ph("type", Msg.raw("marker-types." + type)),
            Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()),
            Msg.ph("world", loc.getWorld().getName()));
    }

    // ===== мастер настройки категорий сундуков (/escape chestsetup) =====

    /** ПКМ жезлом мастера: назначить его категорию текущей точке / выйти. */
    @EventHandler
    public void onWizardInteract(PlayerInteractEvent e)
    {
        Player p = e.getPlayer();
        if (!plugin.chestSetup().isActive(p)) {return;}
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {return;}
        String tag = Items.specialTag(e.getItem());
        if (ChestSetupManager.EXIT_TAG.equals(tag))
        {
            e.setCancelled(true);
            plugin.chestSetup().stop(p, false);
            return;
        }
        if (ChestSetupManager.NEXT_TAG.equals(tag))
        {
            e.setCancelled(true);
            plugin.chestSetup().next(p);
            return;
        }
        if (ChestSetupManager.WAND_TAG.equals(tag))
        {
            e.setCancelled(true);
            String catId = e.getItem().getItemMeta().getPersistentDataContainer()
                .get(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING);
            plugin.chestSetup().assign(p, catId);
        }
    }

    /** В мастере жезлы/предмет выхода нельзя выбросить. */
    @EventHandler(ignoreCancelled = true)
    public void onWizardDrop(PlayerDropItemEvent e)
    {
        if (plugin.chestSetup().isActive(e.getPlayer())) {e.setCancelled(true);}
    }

    /** Выход в мастере: снапшот на диске восстановит onJoin при следующем входе. */
    @EventHandler
    public void onWizardQuit(PlayerQuitEvent e)
    {
        plugin.chestSetup().onQuit(e.getPlayer().getUniqueId());
    }

    // ===== отметчики категорий (/escape chesttag) =====

    /** ПКМ отметчиком по сундуку-точке вне матча = присвоить его категорию точке. */
    @EventHandler
    public void onChestTagInteract(PlayerInteractEvent e)
    {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {return;}
        if (!Items.isSpecial(e.getItem(), "chesttag")) {return;}
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) {return;}
        Player p = e.getPlayer();
        if (plugin.arenas().sessionOf(p) != null) {return;}
        Arena arena = arenaInWorld(block.getWorld());
        if (arena == null || arena.getSession() != null) {return;}
        e.setCancelled(true); // не открываем сундук / не ставим блок
        tryChestTag(p, block, arena, e.getItem());
    }

    // ===== жезл ломаемых блоков (/escape breakable) =====

    /** ПКМ жезлом-меткой по блоку вне матча = пометить/снять блок как ломаемый (обе половины структур). */
    @EventHandler
    public void onBreakableWand(PlayerInteractEvent e)
    {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {return;}
        if (!Items.isSpecial(e.getItem(), "breakwand")) {return;}
        Block block = e.getClickedBlock();
        if (block == null) {return;}
        Player p = e.getPlayer();
        e.setCancelled(true);
        if (plugin.arenas().sessionOf(p) != null) {return;}
        if (!p.hasPermission("escape.admin")) {return;}

        String tagArena = e.getItem().getItemMeta().getPersistentDataContainer()
            .get(Keys.MARKER_ARENA, PersistentDataType.STRING);
        Arena arena = plugin.arenas().get(tagArena);
        if (arena == null) {Msg.send(p, "errors.arena-not-exists"); return;}
        if (arena.getSession() != null) {Msg.send(p, "breakable.arena-busy", Msg.ph("arena", arena.getId())); return;}
        if (!block.getWorld().getName().equals(arena.getWorldName()))
        {
            Msg.send(p, "breakable.wrong-world", Msg.ph("arena", arena.getId()));
            return;
        }
        toggleBreakable(p, block, arena);
    }

    /** Пометить/снять блок и его вторую половину (дверь/кровать) как ломаемый. */
    private void toggleBreakable(Player p, Block block, Arena arena)
    {
        List<Location> locs = new ArrayList<>();
        locs.add(block.getLocation());
        Block partner = SetupMarkers.structurePartner(block);
        if (partner != null) {locs.add(partner.getLocation());}

        boolean marked = arena.getBreakables().contains(block.getLocation());
        if (marked)
        {
            arena.getBreakables().removeAll(locs);
            p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.0f);
        }
        else
        {
            for (Location l : locs)
            {
                if (!arena.getBreakables().contains(l)) {arena.getBreakables().add(l);}
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }
        plugin.arenas().save(arena);
        DebugLog.log(Cat.ADMIN, "breakable-toggle admin=%s arena=%s at=%s marked=%b total=%d",
            p.getName(), arena.getId(), DebugLog.at(block.getLocation()), !marked, arena.getBreakables().size());
        Msg.send(p, marked ? "breakable.unmarked" : "breakable.marked",
            Msg.ph("block", block.getType().name()),
            Msg.ph("x", block.getX()), Msg.ph("y", block.getY()), Msg.ph("z", block.getZ()),
            Msg.ph("n", arena.getBreakables().size()));
    }

    /**
     * Обработать отметчик глобальной категории в руке: если это сундук-точка арены —
     * переключить эту категорию в списке точки (нет — добавить, есть — убрать).
     * Возвращает true, если действие «поглощено» отметчиком (вызывающий должен
     * отменить событие). Арену сохраняет сам.
     */
    private boolean tryChestTag(Player p, Block block, Arena arena, ItemStack item)
    {
        if (!Items.isSpecial(item, "chesttag")) {return false;}
        if (block.getType() != Material.CHEST) {return false;}
        if (!p.hasPermission("escape.admin")) {return true;}

        var pdc = item.getItemMeta().getPersistentDataContainer();
        String tagArena = pdc.get(Keys.MARKER_ARENA, PersistentDataType.STRING);
        if (tagArena != null && !tagArena.equalsIgnoreCase(arena.getId()))
        {
            Msg.send(p, "chesttag.wrong-arena", Msg.ph("arena", arena.getId()), Msg.ph("tag", tagArena));
            return true;
        }
        Location point = block.getLocation();
        if (!arena.getChestSpots().containsKey(point))
        {
            Msg.send(p, "chesttag.not-a-point");
            return true;
        }
        // сундук-точка в мире — источник истины по стороне: синхронизируем
        if (block.getBlockData() instanceof Directional dir)
        {
            arena.getChestFacings().put(point, dir.getFacing());
        }
        String catId = pdc.get(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING);
        if (!plugin.loot().exists(catId)) {Msg.send(p, "chesttag.bad-category"); return true;}
        LootCategory cat = plugin.loot().get(catId);

        List<String> cats = arena.getChestSpots().get(point);
        if (cats == null)
        {
            cats = new ArrayList<>();
            arena.getChestSpots().put(point, cats);
        }
        boolean added;
        if (cats.remove(cat.getId())) {added = false;}
        else {cats.add(cat.getId()); added = true;}
        plugin.arenas().save(arena);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, added ? 1.4f : 1.0f);
        DebugLog.log(Cat.ADMIN, "chesttag-toggle admin=%s arena=%s point=%s cat=%s added=%b count=%d",
            p.getName(), arena.getId(), DebugLog.at(point), cat.getId(), added, cats.size());
        Msg.send(p, added ? "chesttag.added" : "chesttag.removed",
            Msg.phMm("category", cat.getNameRaw()),
            Msg.ph("x", point.getBlockX()), Msg.ph("y", point.getBlockY()), Msg.ph("z", point.getBlockZ()),
            Msg.ph("n", cats.size()));
        return true;
    }

    private Arena arenaInWorld(World world)
    {
        for (Arena arena : plugin.arenas().all().values())
        {
            if (world.getName().equals(arena.getWorldName())) {return arena;}
        }
        return null;
    }
}
