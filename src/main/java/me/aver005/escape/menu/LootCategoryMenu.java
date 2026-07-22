package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Редактор одной категории лута. Верхний ряд (слоты 0..8) — настройки-кнопки
 * (защищены): вес, 6 лимитов, рефилл и инфо об имени/иконке. Область 9..44 —
 * свободная раскладка предметов лута (пагинация, нижний ряд — управление).
 * ЛКМ по числовой кнопке = +1 (Shift +10), ПКМ = -1 (Shift -10); -1 = ∞.
 * Новые предметы получают вес 1, у существующих вес сохраняется через
 * {@link Keys#LOOT_INDEX}. Всё пишется на диск при закрытии меню.
 */
public class LootCategoryMenu extends Menu
{
    private static final int ITEM_START = 9;
    private static final int ITEMS_PER_PAGE = 36; // слоты 9..44
    private static final int SLOT_FILL = 51;      // «Наполнение» в ряду управления

    private final EscapePlugin plugin;
    private final LootCategory cat;
    private final List<WeightedItem> original;
    private final Map<Integer, List<ItemStack>> itemPages = new HashMap<>();
    private int itemPage;
    private int maxItemPage;

    public LootCategoryMenu(EscapePlugin plugin, LootCategory cat)
    {
        super(54, Msg.get("loot-category.title-prefix").append(Component.text(cat.getId())));
        this.plugin = plugin;
        this.cat = cat;
        this.original = new ArrayList<>(cat.getLoot());
        List<ItemStack> displays = buildDisplays();
        for (int i = 0; i < displays.size(); i++)
        {
            itemPages.computeIfAbsent(i / ITEMS_PER_PAGE, k -> new ArrayList<>()).add(displays.get(i));
        }
        maxItemPage = displays.isEmpty() ? 0 : (displays.size() - 1) / ITEMS_PER_PAGE;
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public boolean isProtectedSlot(int slot) {return slot < ITEM_START || slot >= PAGE_SIZE;}

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    /** Предметы лута с довеском: строка веса в лоре + PDC-индекс исходной записи. */
    private List<ItemStack> buildDisplays()
    {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < original.size(); i++)
        {
            WeightedItem entry = original.get(i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Items.flat(Msg.get("loot-editor.weight-lore", Msg.ph("weight", entry.weight()))));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.LOOT_INDEX, PersistentDataType.INTEGER, i);
            display.setItemMeta(meta);
            out.add(display);
        }
        return out;
    }

    private void render()
    {
        inventory.clear();
        for (int slot = 0; slot <= 8; slot++) {inventory.setItem(slot, settingButton(slot));}
        List<ItemStack> content = itemPages.getOrDefault(itemPage, List.of());
        for (int i = 0; i < ITEMS_PER_PAGE && i < content.size(); i++)
        {
            inventory.setItem(ITEM_START + i, content.get(i));
        }
        renderControls(itemPage, maxItemPage + 1, true, Material.BLACK_STAINED_GLASS_PANE);
        // «вперёд» всегда: с последней страницы можно завести новую под новые предметы
        inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("menu.page-next")));
        inventory.setItem(SLOT_FILL, Items.named(Material.HOPPER,
            Msg.get("loot-category.btn-fill-name"), Msg.getList("loot-category.btn-fill-lore")));
    }

    private ItemStack settingButton(int slot)
    {
        return switch (slot)
        {
            case 0 -> numeric(Material.GOLD_INGOT, "loot-category.btn-weight",
                String.valueOf(cat.getWeight()), "loot-category.adjust-lore-weight");
            case 1 -> numeric(Material.CHEST, "loot-category.btn-min-per-chest",
                fmt(cat.getMinPerChest()), "loot-category.adjust-lore-limit");
            case 2 -> numeric(Material.TRAPPED_CHEST, "loot-category.btn-max-per-chest",
                fmt(cat.getMaxPerChest()), "loot-category.adjust-lore-limit");
            case 3 -> numeric(Material.MAP, "loot-category.btn-min-per-arena",
                fmt(cat.getMinPerArena()), "loot-category.adjust-lore-limit");
            case 4 -> numeric(Material.FILLED_MAP, "loot-category.btn-max-per-arena",
                fmt(cat.getMaxPerArena()), "loot-category.adjust-lore-limit");
            case 5 -> numeric(Material.BARREL, "loot-category.btn-min-chests",
                fmt(cat.getMinChests()), "loot-category.adjust-lore-limit");
            case 6 -> numeric(Material.ENDER_CHEST, "loot-category.btn-max-chests",
                fmt(cat.getMaxChests()), "loot-category.adjust-lore-limit");
            case 7 -> numeric(Material.CLOCK, "loot-category.btn-refill",
                String.valueOf(cat.getRefillSeconds()), "loot-category.adjust-lore-refill");
            default -> nameIconButton();
        };
    }

    private ItemStack numeric(Material mat, String nameKey, String value, String adjustKey)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("loot-category.value-lore", Msg.ph("value", value)));
        lore.addAll(Msg.getList(adjustKey));
        return Items.named(mat, Msg.get(nameKey), lore);
    }

    private ItemStack nameIconButton()
    {
        List<Component> lore = Msg.getList("loot-category.btn-nameicon-lore",
            Msg.phMm("name", cat.getNameRaw()),
            Msg.ph("icon", cat.getIcon().name()),
            Msg.ph("id", cat.getId()));
        return Items.named(cat.getIcon(), Msg.get("loot-category.btn-nameicon-name"), lore);
    }

    /** UNLIMITED показываем как «∞» (loot-editor.unlimited), иначе — число. */
    private String fmt(int v)
    {
        return v == LootCategory.UNLIMITED ? Msg.raw("loot-editor.unlimited") : String.valueOf(v);
    }

    private void adjust(int slot, InventoryClickEvent e)
    {
        int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
        switch (slot)
        {
            case 0 -> cat.setWeight(cat.getWeight() + delta);
            case 1 -> cat.setMinPerChest(cat.getMinPerChest() + delta);
            case 2 -> cat.setMaxPerChest(cat.getMaxPerChest() + delta);
            case 3 -> cat.setMinPerArena(cat.getMinPerArena() + delta);
            case 4 -> cat.setMaxPerArena(cat.getMaxPerArena() + delta);
            case 5 -> cat.setMinChests(cat.getMinChests() + delta);
            case 6 -> cat.setMaxChests(cat.getMaxChests() + delta);
            case 7 -> cat.setRefillSeconds(cat.getRefillSeconds() + delta);
            default -> {return;}
        }
        inventory.setItem(slot, settingButton(slot));
    }

    /** Считать область предметов (9..44) текущей страницы обратно в модель. */
    private void capture()
    {
        List<ItemStack> list = new ArrayList<>();
        for (int i = ITEM_START; i < PAGE_SIZE; i++)
        {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {list.add(item.clone());}
        }
        itemPages.put(itemPage, list);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_PREV && itemPage > 0) {capture(); itemPage--; render(); return;}
        if (raw == SLOT_NEXT) {capture(); itemPage++; if (itemPage > maxItemPage) {maxItemPage = itemPage;} render(); return;}
        if (raw == SLOT_BACK) {new LootEditorMenu(plugin).open(p); return;}
        if (raw == SLOT_FILL) {new LootFillMenu(plugin, cat).open(p); return;}
        if (raw >= 0 && raw <= 7) {adjust(raw, e); return;}
        if (raw == 8) {Msg.send(p, "loot-category.nameicon-hint", Msg.ph("id", cat.getId())); return;}
        // область предметов (9..44) свободна; ряд управления защищён isProtectedSlot
    }

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        capture();
        List<WeightedItem> next = new ArrayList<>();
        for (int pg = 0; pg <= maxItemPage; pg++)
        {
            for (ItemStack item : itemPages.getOrDefault(pg, List.of()))
            {
                Integer index = item.hasItemMeta()
                    ? item.getItemMeta().getPersistentDataContainer().get(Keys.LOOT_INDEX, PersistentDataType.INTEGER)
                    : null;
                if (index != null && index >= 0 && index < original.size())
                {
                    next.add(original.get(index));
                }
                else
                {
                    next.add(new WeightedItem(item.clone(), 1));
                }
            }
        }
        cat.getLoot().clear();
        cat.getLoot().addAll(next);
        plugin.loot().save(cat);
        if (e.getPlayer() instanceof Player p) {Msg.send(p, "loot-category.saved", Msg.ph("id", cat.getId()));}
    }
}
