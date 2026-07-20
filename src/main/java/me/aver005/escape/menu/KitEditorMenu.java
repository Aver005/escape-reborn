package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.kit.Kit;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Редактор предметов каста: разложите предметы каста и закройте меню —
 * набор пересобирается из содержимого. Броня при выдаче каста
 * авто-надевается в слоты. Имя/иконка/золото — через команды /escape kit.
 */
public class KitEditorMenu extends Menu
{
    private final EscapePlugin plugin;
    private final Arena arena;
    private final Kit kit;

    public KitEditorMenu(EscapePlugin plugin, Arena arena, Kit kit)
    {
        super(54, Msg.get("kit.editor-title-prefix").append(Component.text(kit.getId())));
        this.plugin = plugin;
        this.arena = arena;
        this.kit = kit;

        List<ItemStack> items = kit.getItems();
        for (int i = 0; i < items.size() && i < 54; i++)
        {
            inventory.setItem(i, items.get(i).clone());
        }
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public void onClick(InventoryClickEvent e) {}

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        List<ItemStack> next = new ArrayList<>();
        for (ItemStack item : inventory.getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            next.add(item.clone());
        }
        kit.getItems().clear();
        kit.getItems().addAll(next);
        plugin.arenas().save(arena);
        if (e.getPlayer() instanceof Player p)
        {
            Msg.send(p, "kit.editor-saved", Msg.ph("id", kit.getId()), Msg.ph("arena", arena.getId()));
        }
    }
}
