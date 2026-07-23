package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.loot.LootCategory;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Подтверждение удаления категории лута. «Удалить» — {@code plugin.loot().delete}
 * и возврат к списку; «Отмена» — просто возврат к списку.
 */
public class LootDeleteConfirmMenu extends Menu
{
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_CANCEL = 15;

    private final EscapePlugin plugin;
    private final String id;

    public LootDeleteConfirmMenu(EscapePlugin plugin, String id)
    {
        super(27, Msg.get("loot-editor.confirm-title"));
        this.plugin = plugin;
        this.id = id;
        render();
    }

    private void render()
    {
        inventory.setItem(SLOT_CONFIRM, Items.named(Material.RED_WOOL,
            Msg.get("loot-editor.confirm-yes-name"), Msg.getList("loot-editor.confirm-yes-lore")));
        inventory.setItem(SLOT_CANCEL, Items.named(Material.GREEN_WOOL,
            Msg.get("loot-editor.confirm-no-name"), Msg.getList("loot-editor.confirm-no-lore")));
        LootCategory cat = plugin.loot().get(id);
        Material icon = cat != null ? cat.getIcon() : Material.CHEST;
        Component name = cat != null ? Msg.mm(cat.getNameRaw()) : Component.text(id);
        List<Component> lore = Msg.getList("loot-editor.confirm-info-lore", Msg.ph("id", id));
        inventory.setItem(SLOT_INFO, Items.named(icon, name, lore));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_CONFIRM ->
            {
                if (plugin.loot().delete(id)) {Msg.send(p, "loot-editor.deleted", Msg.ph("id", id));}
                new LootEditorMenu(plugin).open(p);
            }
            case SLOT_CANCEL -> new LootEditorMenu(plugin).open(p);
            default -> {}
        }
    }
}
