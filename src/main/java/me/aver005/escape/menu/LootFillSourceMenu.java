package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Выбор категории-источника для наполнения другой категории ({@link LootFillMenu}).
 * {@code replace} — заменить лут цели полностью, иначе дополнить. Сама цель из
 * списка исключена. Вес записей источника сохраняется (копируются независимо).
 */
public class LootFillSourceMenu extends Menu
{
    private final EscapePlugin plugin;
    private final LootCategory target;
    private final boolean replace;
    private int page;

    public LootFillSourceMenu(EscapePlugin plugin, LootCategory target, boolean replace)
    {
        super(54, Msg.get(replace ? "loot-fill.source-title-replace" : "loot-fill.source-title-append"));
        this.plugin = plugin;
        this.target = target;
        this.replace = replace;
        render();
    }

    private List<LootCategory> categories()
    {
        List<LootCategory> out = new ArrayList<>();
        for (LootCategory cat : plugin.loot().all())
        {
            if (!cat.getId().equals(target.getId())) {out.add(cat);}
        }
        return out;
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
        lore.add(Items.flat(Msg.get(replace ? "loot-fill.source-pick-replace" : "loot-fill.source-pick-append")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING, cat.getId());
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
        if (raw == SLOT_BACK) {new LootFillMenu(plugin, target).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {return;}
        String id = clicked.getItemMeta().getPersistentDataContainer()
            .get(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING);
        if (id == null) {return;}
        LootCategory src = plugin.loot().get(id);
        if (src == null) {render(); return;}
        if (src.getLoot().isEmpty()) {Msg.send(p, "loot-fill.empty-source", Msg.ph("id", src.getId())); return;}

        int added = src.getLoot().size();
        if (replace) {target.getLoot().clear();}
        for (WeightedItem entry : src.getLoot())
        {
            target.getLoot().add(new WeightedItem(entry.item().clone(), entry.weight()));
        }
        plugin.loot().save(target);
        DebugLog.log(Cat.ADMIN, "loot-fill admin=%s cat=%s mode=%s source=cat:%s added=%d total=%d",
            p.getName(), target.getId(), replace ? "replace" : "append", src.getId(), added, target.getLoot().size());
        if (replace) {Msg.send(p, "loot-fill.replaced", Msg.ph("id", target.getId()), Msg.ph("n", target.getLoot().size()));}
        else {Msg.send(p, "loot-fill.appended", Msg.ph("id", target.getId()), Msg.ph("n", added));}
        new LootCategoryMenu(plugin, target).open(p);
    }
}
