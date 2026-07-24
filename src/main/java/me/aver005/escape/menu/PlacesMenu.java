package me.aver005.escape.menu;

import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.menu.Menu;
import me.aver005.escape.util.EscapeItems;

import java.util.HashMap;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.EscapeRules;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** «Отмеченные локации» — именованные рычаги арены. */
public class PlacesMenu extends Menu
{
    private final EscapePlugin plugin;
    private final EscapeRules session;
    private final Map<Integer, Location> locationBySlot = new HashMap<>();

    public PlacesMenu(EscapePlugin plugin, EscapeRules session)
    {
        super(27, Msg.get("assistant.places-title"));
        this.plugin = plugin;
        this.session = session;
        int slot = 0;
        for (Map.Entry<Location, String> entry : EscapeArena.levers(session.getArena()).entrySet())
        {
            if (slot >= 27) {break;}
            inventory.setItem(slot, Items.named(Material.GRASS_BLOCK,
                Msg.get("assistant.place-item-name", Msg.ph("place", entry.getValue())),
                Msg.getList("assistant.place-item-lore")));
            locationBySlot.put(slot, entry.getKey());
            slot++;
        }
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}
        Location loc = locationBySlot.get(e.getRawSlot());
        if (loc == null) {return;}

        String name = EscapeArena.levers(session.getArena()).get(loc);
        Msg.send(p, "assistant.used-header",
            Msg.phC("ability", Msg.get("assistant.place-item-name", Msg.ph("place", name))));
        if (EscapeItems.pointAssistantCompass(p, loc))
        {
            Msg.send(p, "assistant.compass-points");
        }
        else
        {
            Msg.send(p, "assistant.coords",
                Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()));
        }

        int seconds = plugin.getConfig().getInt("assistant.places", 90);
        session.setCooldown(p.getUniqueId() + ":places", seconds * 1000L);
        p.closeInventory();
    }
}
