package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Выбор категории-источника для режима «копия» ({@link LootCreateMenu}).
 * ЛКМ по категории — создать её независимую копию под свободным id «<id>-copy…».
 */
public class LootCopySourceMenu extends Menu
{
    private final EscapePlugin plugin;
    private int page;

    public LootCopySourceMenu(EscapePlugin plugin)
    {
        super(54, Msg.get("loot-create.copy-title"));
        this.plugin = plugin;
        render();
    }

    private List<LootCategory> categories()
    {
        return new ArrayList<>(plugin.loot().all());
    }

    private void render()
    {
        inventory.clear();
        List<LootCategory> cats = categories();
        int pages = pageCount(cats.size());
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            int index = page * PAGE_SIZE + i;
            if (index >= cats.size()) {break;}
            inventory.setItem(i, icon(cats.get(index)));
        }
        renderControls(page, pages, true, Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack icon(LootCategory cat)
    {
        ItemStack item = new ItemStack(cat.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.flat(Msg.mm(cat.getNameRaw())));
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("loot-editor.lore-items", Msg.ph("count", cat.getLoot().size()))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-id", Msg.ph("id", cat.getId()))));
        lore.add(Items.flat(Msg.get("loot-create.copy-pick")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.CATEGORY_ID, PersistentDataType.STRING, cat.getId());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(categories().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_BACK) {new LootCreateMenu(plugin).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {return;}
        String id = clicked.getItemMeta().getPersistentDataContainer()
            .get(Keys.CATEGORY_ID, PersistentDataType.STRING);
        if (id == null) {return;}
        LootCategory src = plugin.loot().get(id);
        if (src == null) {render(); return;}
        String newId = LootCreate.freshId(plugin.loot(), id + "-copy");
        LootCategory copy = LootCreate.copyFrom(src, newId);
        plugin.loot().save(copy);
        Msg.send(p, "loot-create.created", Msg.ph("id", newId));
        new LootEditorMenu(plugin).open(p);
    }
}
