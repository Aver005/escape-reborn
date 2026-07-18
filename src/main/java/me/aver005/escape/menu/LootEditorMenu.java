package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Редактор пула лута арены: свободно добавляйте/убирайте предметы,
 * при закрытии пул пересобирается. Новые предметы получают вес 1
 * (точный вес — через /escape additem <ID> <вес>).
 */
public class LootEditorMenu extends Menu
{
    private final EscapePlugin plugin;
    private final Arena arena;

    public LootEditorMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("loot-editor.title-prefix").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;

        List<WeightedItem> loot = arena.getLoot();
        for (int i = 0; i < loot.size() && i < 54; i++)
        {
            WeightedItem entry = loot.get(i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Items.flat(Msg.get("loot-editor.weight-lore", Msg.ph("weight", entry.weight()))));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.LOOT_INDEX, PersistentDataType.INTEGER, i);
            display.setItemMeta(meta);
            inventory.setItem(i, display);
        }
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public void onClick(InventoryClickEvent e) {}

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        List<WeightedItem> old = new ArrayList<>(arena.getLoot());
        List<WeightedItem> next = new ArrayList<>();
        for (ItemStack item : inventory.getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            Integer index = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(Keys.LOOT_INDEX, PersistentDataType.INTEGER)
                : null;
            if (index != null && index >= 0 && index < old.size())
            {
                next.add(old.get(index));
            }
            else
            {
                next.add(new WeightedItem(item.clone(), 1));
            }
        }
        arena.getLoot().clear();
        arena.getLoot().addAll(next);
        plugin.arenas().save(arena);
        if (e.getPlayer() instanceof Player p)
        {
            Msg.send(p, "admin.loot-saved", Msg.ph("arena", arena.getId()));
        }
    }
}
