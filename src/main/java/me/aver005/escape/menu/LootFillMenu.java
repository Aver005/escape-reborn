package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Наполнение существующей категории лута теми же тремя способами, что и создание:
 * из инвентаря, из просматриваемого сундука, из другой категории. Два режима —
 * «Дополнить» (добавить к текущему луту) и «Заменить» (полностью перезаписать).
 * Замена применяется сразу; пустой источник НЕ стирает содержимое. Вес предметов
 * из инвентаря/сундука = 1 (в GUI вес не редактируется); копия категории вес хранит.
 */
public class LootFillMenu extends Menu
{
    private static final int APPEND_INVENTORY = 10;
    private static final int APPEND_CHEST = 11;
    private static final int APPEND_CATEGORY = 12;
    private static final int REPLACE_INVENTORY = 14;
    private static final int REPLACE_CHEST = 15;
    private static final int REPLACE_CATEGORY = 16;
    private static final int SLOT_BACK_FILL = 22;

    private final EscapePlugin plugin;
    private final LootCategory cat;

    public LootFillMenu(EscapePlugin plugin, LootCategory cat)
    {
        super(27, Msg.get("loot-fill.title").append(Component.text(cat.getId())));
        this.plugin = plugin;
        this.cat = cat;
        render();
    }

    private void render()
    {
        inventory.setItem(APPEND_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("loot-fill.append-inventory-name"), Msg.getList("loot-fill.append-inventory-lore")));
        inventory.setItem(APPEND_CHEST, Items.named(Material.CHEST,
            Msg.get("loot-fill.append-chest-name"), Msg.getList("loot-fill.append-chest-lore")));
        inventory.setItem(APPEND_CATEGORY, Items.named(Material.WRITABLE_BOOK,
            Msg.get("loot-fill.append-category-name"), Msg.getList("loot-fill.append-category-lore")));
        inventory.setItem(REPLACE_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("loot-fill.replace-inventory-name"), Msg.getList("loot-fill.replace-inventory-lore")));
        inventory.setItem(REPLACE_CHEST, Items.named(Material.CHEST,
            Msg.get("loot-fill.replace-chest-name"), Msg.getList("loot-fill.replace-chest-lore")));
        inventory.setItem(REPLACE_CATEGORY, Items.named(Material.WRITABLE_BOOK,
            Msg.get("loot-fill.replace-category-name"), Msg.getList("loot-fill.replace-category-lore")));
        inventory.setItem(SLOT_BACK_FILL, Items.named(Material.OAK_DOOR,
            Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case APPEND_INVENTORY -> applyInventory(p, false);
            case APPEND_CHEST -> applyChest(p, false);
            case APPEND_CATEGORY -> new LootFillSourceMenu(plugin, cat, false).open(p);
            case REPLACE_INVENTORY -> applyInventory(p, true);
            case REPLACE_CHEST -> applyChest(p, true);
            case REPLACE_CATEGORY -> new LootFillSourceMenu(plugin, cat, true).open(p);
            case SLOT_BACK_FILL -> new LootCategoryMenu(plugin, cat).open(p);
            default -> {}
        }
    }

    private void applyInventory(Player p, boolean replace)
    {
        List<WeightedItem> items = LootCreate.lootFromInventory(p);
        if (items.isEmpty()) {Msg.send(p, "loot-fill.empty-inventory"); return;}
        apply(p, items, replace, "inventory");
    }

    private void applyChest(Player p, boolean replace)
    {
        List<WeightedItem> items = LootCreate.lootFromChest(p);
        if (items == null) {Msg.send(p, "loot-fill.no-chest"); return;}
        if (items.isEmpty()) {Msg.send(p, "loot-fill.empty-chest"); return;}
        apply(p, items, replace, "chest");
    }

    /** Дополнить или заменить лут категории и сохранить. Возврат в редактор категории. */
    private void apply(Player p, List<WeightedItem> items, boolean replace, String source)
    {
        if (replace) {cat.getLoot().clear();}
        cat.getLoot().addAll(items);
        plugin.loot().save(cat);
        DebugLog.log(Cat.ADMIN, "loot-fill admin=%s cat=%s mode=%s source=%s added=%d total=%d",
            p.getName(), cat.getId(), replace ? "replace" : "append", source, items.size(), cat.getLoot().size());
        if (replace) {Msg.send(p, "loot-fill.replaced", Msg.ph("id", cat.getId()), Msg.ph("n", cat.getLoot().size()));}
        else {Msg.send(p, "loot-fill.appended", Msg.ph("id", cat.getId()), Msg.ph("n", items.size()));}
        new LootCategoryMenu(plugin, cat).open(p);
    }
}
