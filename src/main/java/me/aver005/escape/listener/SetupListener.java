package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Установка блоков-маркеров: добавление точек арены. */
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
            case "chest" -> arena.getChestSpots().add(loc);
            case "table" -> arena.getTableSpots().add(loc);
            case "ore" -> {arena.getOreSpots().add(loc); placeReal = true;}
            case "lever" -> {arena.getLevers().put(loc, extra == null ? "?" : extra); placeReal = true;}
            case "villager" -> arena.getTraderSpots().put(loc, extra == null ? "?" : extra);
            default -> {return;}
        }
        if (!placeReal) {e.setCancelled(true);}
        plugin.arenas().save(arena);

        Msg.send(p, "admin.marker-added",
            Msg.ph("type", Msg.raw("marker-types." + type)),
            Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()),
            Msg.ph("world", loc.getWorld().getName()));
    }
}
