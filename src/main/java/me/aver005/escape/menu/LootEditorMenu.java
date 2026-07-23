package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.loot.LootCategory;
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
 * Верхнее меню лут-системы: список всех глобальных категорий (пагинация).
 * ЛКМ по категории — открыть её редактор ({@link LootCategoryMenu}).
 * Shift+ЛКМ — удалить (с подтверждением). Кнопка «создать» — мастер создания
 * ({@link LootCreateMenu}). Категория опознаётся по PDC {@link Keys#CATEGORY_ID}.
 */
public class LootEditorMenu extends Menu
{
    private static final int SLOT_CREATE = 48;

    private final EscapePlugin plugin;
    private int page;

    public LootEditorMenu(EscapePlugin plugin)
    {
        super(54, Msg.get("loot-editor.title"));
        this.plugin = plugin;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
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
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_CREATE, Items.named(Material.NETHER_STAR,
            Msg.get("loot-editor.create-name"), Msg.getList("loot-editor.create-lore")));
    }

    private ItemStack icon(LootCategory cat)
    {
        ItemStack item = new ItemStack(cat.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.flat(Msg.mm(cat.getNameRaw())));
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("loot-editor.lore-weight", Msg.ph("weight", cat.getWeight()))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-per-chest",
            Msg.ph("min", fmt(cat.getMinPerChest())), Msg.ph("max", fmt(cat.getMaxPerChest())))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-per-arena",
            Msg.ph("min", fmt(cat.getMinPerArena())), Msg.ph("max", fmt(cat.getMaxPerArena())))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-chests",
            Msg.ph("min", fmt(cat.getMinChests())), Msg.ph("max", fmt(cat.getMaxChests())))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-refill", Msg.ph("seconds", cat.getRefillSeconds()))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-items", Msg.ph("count", cat.getLoot().size()))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-id", Msg.ph("id", cat.getId()))));
        lore.add(Items.flat(Msg.get("loot-editor.lore-edit")));
        lore.add(Items.flat(Msg.get("loot-editor.lore-delete")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING, cat.getId());
        item.setItemMeta(meta);
        return item;
    }

    /** UNLIMITED показываем как «∞» (loot-editor.unlimited), иначе — число. */
    private String fmt(int v)
    {
        return v == LootCategory.UNLIMITED ? Msg.raw("loot-editor.unlimited") : String.valueOf(v);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(categories().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_CREATE) {new LootCreateMenu(plugin).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {return;}
        String id = clicked.getItemMeta().getPersistentDataContainer()
            .get(EscapeKeys.CATEGORY_ID, PersistentDataType.STRING);
        if (id == null) {return;}
        LootCategory cat = plugin.loot().get(id);
        if (cat == null) {render(); return;}
        if (e.isShiftClick()) {new LootDeleteConfirmMenu(plugin, id).open(p); return;}
        if (e.isLeftClick()) {new LootCategoryMenu(plugin, cat).open(p);}
    }
}
