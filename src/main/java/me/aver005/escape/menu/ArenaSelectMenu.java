package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.game.EscapeRules;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Главное меню выбора арены. */
public class ArenaSelectMenu extends Menu
{
    private final EscapePlugin plugin;
    private final Map<Integer, String> arenaBySlot = new HashMap<>();

    public ArenaSelectMenu(EscapePlugin plugin)
    {
        super(54, Msg.get("menu.title"));
        this.plugin = plugin;
        fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        render();
    }

    private void render()
    {
        for (Arena arena : plugin.arenas().all().values())
        {
            if (!arena.isEnabled()) {continue;}
            EscapeRules session = plugin.arenas().sessionOf(arena);
            if (session != null
                && (session.getPhase() == EscapeRules.Phase.RUNNING || session.getPhase() == EscapeRules.Phase.ENDING))
            {continue;}

            int current = session == null ? 0 : session.lobbySize();
            List<Component> lore = new ArrayList<>();
            for (String line : Msg.rawList("menu.arena-item-lore"))
            {
                lore.add(Msg.mm(line,
                    Msg.ph("current", current),
                    Msg.ph("max", arena.getMaxPlayers()),
                    Msg.phMm("description", arena.getDescriptionRaw())));
            }
            ItemStack item = Items.named(Material.LIME_WOOL,
                Msg.get("menu.arena-item-name", Msg.phMm("arena", arena.getDisplayNameRaw())), lore);

            int slot = firstFreeInnerSlot();
            if (slot == -1) {break;}
            inventory.setItem(slot, item);
            arenaBySlot.put(slot, arena.getId());
        }
    }

    private int firstFreeInnerSlot()
    {
        for (int i = 0; i < inventory.getSize(); i++)
        {
            boolean border = i < 9 || i >= inventory.getSize() - 9 || i % 9 == 0 || (i + 1) % 9 == 0;
            if (!border && inventory.getItem(i) == null) {return i;}
        }
        return -1;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        String arenaId = arenaBySlot.get(e.getRawSlot());
        if (arenaId == null) {return;}
        Arena arena = plugin.arenas().get(arenaId);
        if (arena == null) {Msg.send(p, "menu.arena-not-found"); return;}
        p.closeInventory();
        plugin.arenas().join(p, arena);
    }
}
