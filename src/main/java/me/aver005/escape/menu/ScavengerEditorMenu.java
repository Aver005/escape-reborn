package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Админ-редактор Мусорщика (§14): быстрая правка прайса скрапа и порога износа.
 * Пагинация по 45 материалов на страницу. Клики по иконке материала крутят
 * базовую цену (ЛКМ +1 / Shift +10, ПКМ -1 / Shift -10, Q — убрать). Порог износа
 * — кнопкой. Добавление: <b>Shift-клик по предмету с прочностью в своём инвентаре</b>
 * — не нужно брать в руку и переоткрывать меню, можно накидать сразу пачку.
 * Меню контролируемое (предметы не вытащить); каждое изменение сразу
 * персистится через TraderRegistry.save.
 */
public class ScavengerEditorMenu extends Menu
{
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_WEAR = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;
    private static final int DEFAULT_PRICE = 10;

    private final EscapePlugin plugin;
    private final TraderType trader;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();
    private int page;

    public ScavengerEditorMenu(EscapePlugin plugin, TraderType trader)
    {
        super(54, Msg.get("scrap-editor.title-prefix").append(Component.text(trader.getId())));
        this.plugin = plugin;
        this.trader = trader;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        slotToMaterial.clear();

        List<Map.Entry<Material, Integer>> entries = new ArrayList<>(trader.getScrapPrices().entrySet());
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}

        int from = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++)
        {
            int idx = from + slot;
            if (idx >= entries.size()) {break;}
            Map.Entry<Material, Integer> entry = entries.get(idx);
            inventory.setItem(slot, icon(entry.getKey(), entry.getValue()));
            slotToMaterial.put(slot, entry.getKey());
        }

        for (int i = PAGE_SIZE; i < 54; i++)
        {
            inventory.setItem(i, Items.filler(Material.BROWN_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_WEAR, Items.named(Material.SHEARS,
            Msg.get("scrap-editor.wear-name", Msg.ph("wear", trader.getScrapMinWearPercent())),
            Msg.getList("scrap-editor.wear-lore")));
        inventory.setItem(SLOT_INFO, Items.named(Material.CAULDRON,
            Msg.get("scrap-editor.info-name"),
            Msg.getList("scrap-editor.info-lore",
                Msg.ph("wear", trader.getScrapMinWearPercent()),
                Msg.ph("n", trader.getScrapPrices().size()),
                Msg.ph("page", page + 1), Msg.ph("pages", pages))));
        if (page > 0)
        {
            inventory.setItem(SLOT_PREV, Items.named(Material.ARROW, Msg.get("chestsetup.page-prev")));
        }
        if (page < pages - 1)
        {
            inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("chestsetup.page-next")));
        }
        if (entries.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("scrap-editor.empty-name"), Msg.getList("scrap-editor.empty-lore")));
        }
    }

    private ItemStack icon(Material mat, int price)
    {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("scrap-editor.entry-price", Msg.ph("price", price))));
        lore.addAll(Msg.getList("scrap-editor.entry-lore"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();

        // Shift-клик по предмету в своём инвентаре = добавить его материал в прайс
        if (raw >= inventory.getSize())
        {
            if (e.isShiftClick()) {addFromItem(p, e.getCurrentItem());}
            return;
        }

        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT) {page++; render(); return;}
        if (raw == SLOT_WEAR) {changeWear(p, e); return;}

        Material mat = slotToMaterial.get(raw);
        if (mat == null) {return;}

        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP)
        {
            trader.getScrapPrices().remove(mat);
            plugin.traders().save();
            p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.2f);
            DebugLog.log(Cat.ADMIN, "scrap-editor-remove admin=%s trader=%s item=%s",
                p.getName(), trader.getId(), mat.name());
            Msg.send(p, "scrap-editor.removed", Msg.ph("item", mat.name()));
            render();
            return;
        }

        int delta = e.isLeftClick() ? (e.isShiftClick() ? 10 : 1) : (e.isShiftClick() ? -10 : -1);
        int current = trader.getScrapPrices().getOrDefault(mat, DEFAULT_PRICE);
        int next = Math.max(1, Math.min(100000, current + delta));
        if (next == current) {return;}
        trader.getScrapPrices().put(mat, next);
        plugin.traders().save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.4f : 0.9f);
        render();
    }

    private void changeWear(Player p, InventoryClickEvent e)
    {
        int step = e.isShiftClick() ? 1 : 5;
        int delta = e.isLeftClick() ? step : -step;
        int next = Math.max(0, Math.min(99, trader.getScrapMinWearPercent() + delta));
        if (next == trader.getScrapMinWearPercent()) {return;}
        trader.setScrapMinWearPercent(next);
        plugin.traders().save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.4f : 0.9f);
        render();
    }

    /** Добавить материал предмета (Shift-клик в своём инвентаре) в прайс по дефолт-цене. */
    private void addFromItem(Player p, ItemStack item)
    {
        if (item == null || item.getType().isAir()) {return;}
        Material mat = item.getType();
        if (mat.getMaxDurability() <= 0)
        {
            Msg.send(p, "admin.scrap-not-durable", Msg.ph("item", mat.name()));
            return;
        }
        if (trader.getScrapPrices().containsKey(mat))
        {
            Msg.send(p, "scrap-editor.already", Msg.ph("item", mat.name()));
            return;
        }
        trader.getScrapPrices().put(mat, DEFAULT_PRICE);
        plugin.traders().save();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
        DebugLog.log(Cat.ADMIN, "scrap-editor-add admin=%s trader=%s item=%s price=%d",
            p.getName(), trader.getId(), mat.name(), DEFAULT_PRICE);
        Msg.send(p, "scrap-editor.added", Msg.ph("item", mat.name()), Msg.ph("price", DEFAULT_PRICE));
        render();
    }
}
