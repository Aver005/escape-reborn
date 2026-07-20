package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ChestSetupManager;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.category.ChestCategory;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
            case "chest" -> arena.getChestSpots().put(loc, ChestCategory.DEFAULT_ID);
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
        if (ChestSetupManager.WAND_TAG.equals(tag))
        {
            e.setCancelled(true);
            String catId = e.getItem().getItemMeta().getPersistentDataContainer()
                .get(Keys.CATEGORY_ID, PersistentDataType.STRING);
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

    private Arena arenaInWorld(World world)
    {
        for (Arena arena : plugin.arenas().all().values())
        {
            if (world.getName().equals(arena.getWorldName())) {return arena;}
        }
        return null;
    }
}
