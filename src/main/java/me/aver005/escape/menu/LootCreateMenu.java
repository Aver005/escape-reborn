package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.loot.LootCategory;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Мастер создания категории лута: три режима — из инвентаря, из сундука перед
 * игроком, копией другой категории. Id генерируется автоматически (свободный
 * «category…»); переименовать потом можно командой. Те же режимы доступны из
 * команды через {@link LootCreate}.
 */
public class LootCreateMenu extends Menu
{
    private static final int SLOT_FROM_INVENTORY = 11;
    private static final int SLOT_FROM_CHEST = 13;
    private static final int SLOT_FROM_COPY = 15;
    private static final int SLOT_BACK_CREATE = 22;
    private static final String BASE_ID = "category";

    private final EscapePlugin plugin;

    public LootCreateMenu(EscapePlugin plugin)
    {
        super(27, Msg.get("loot-create.title"));
        this.plugin = plugin;
        render();
    }

    private void render()
    {
        inventory.setItem(SLOT_FROM_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("loot-create.from-inventory-name"), Msg.getList("loot-create.from-inventory-lore")));
        inventory.setItem(SLOT_FROM_CHEST, Items.named(Material.CHEST,
            Msg.get("loot-create.from-chest-name"), Msg.getList("loot-create.from-chest-lore")));
        inventory.setItem(SLOT_FROM_COPY, Items.named(Material.WRITABLE_BOOK,
            Msg.get("loot-create.from-copy-name"), Msg.getList("loot-create.from-copy-lore")));
        inventory.setItem(SLOT_BACK_CREATE, Items.named(Material.OAK_DOOR,
            Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_FROM_INVENTORY -> createFromInventory(p);
            case SLOT_FROM_CHEST -> createFromChest(p);
            case SLOT_FROM_COPY -> new LootCopySourceMenu(plugin).open(p);
            case SLOT_BACK_CREATE -> new LootEditorMenu(plugin).open(p);
            default -> {}
        }
    }

    private void createFromInventory(Player p)
    {
        String id = LootCreate.freshId(plugin.loot(), BASE_ID);
        LootCategory cat = LootCreate.fromInventory(p, id);
        if (cat.getLoot().isEmpty()) {Msg.send(p, "loot-create.empty-inventory"); return;}
        plugin.loot().save(cat);
        Msg.send(p, "loot-create.created", Msg.ph("id", id));
        new LootEditorMenu(plugin).open(p);
    }

    private void createFromChest(Player p)
    {
        String id = LootCreate.freshId(plugin.loot(), BASE_ID);
        LootCategory cat = LootCreate.fromTargetChest(p, id);
        if (cat == null) {Msg.send(p, "loot-create.no-target-chest"); return;}
        plugin.loot().save(cat);
        Msg.send(p, "loot-create.created", Msg.ph("id", id));
        new LootEditorMenu(plugin).open(p);
    }
}
