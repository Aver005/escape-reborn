package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.TraderType;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Верхнее меню жителей: список всех глобальных типов торговцев (пагинация).
 * ЛКМ по жителю — открыть редактор его товаров ({@link TradeListEditorMenu}).
 * Кнопка «Создать» — мастер создания жителя из инвентаря/сундука/копии
 * ({@link TraderCreateMenu}). Житель опознаётся по слоту (без PDC на иконке).
 */
public class TraderListMenu extends Menu
{
    private static final int SLOT_CREATE = 48;

    private final EscapePlugin plugin;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public TraderListMenu(EscapePlugin plugin)
    {
        super(54, Msg.get("trader-list.title"));
        this.plugin = plugin;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private List<TraderType> traders()
    {
        return new ArrayList<>(plugin.traders().all());
    }

    private void render()
    {
        inventory.clear();
        idBySlot.clear();
        List<TraderType> list = traders();
        int pages = pageCount(list.size());
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            int index = page * PAGE_SIZE + i;
            if (index >= list.size()) {break;}
            TraderType trader = list.get(index);
            inventory.setItem(i, icon(trader));
            idBySlot.put(i, trader.getId());
        }
        renderControls(page, pages, false, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_CREATE, Items.named(Material.NETHER_STAR,
            Msg.get("trader-list.create-name"), Msg.getList("trader-list.create-lore")));
        if (list.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("trader-list.empty-name"), Msg.getList("trader-list.empty-lore")));
        }
    }

    private ItemStack icon(TraderType trader)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("trader-list.lore-trades", Msg.ph("count", trader.getTrades().size()))));
        lore.add(Items.flat(Msg.get("trader-list.lore-roles",
            Msg.ph("shop", flag(trader.isShop())), Msg.ph("overseer", flag(trader.isOverseer())),
            Msg.ph("scrap", flag(trader.isScavenger())))));
        lore.add(Items.flat(Msg.get("trader-list.lore-id", Msg.ph("id", trader.getId()))));
        lore.add(Items.flat(Msg.get("trader-list.lore-edit")));
        return Items.named(Material.VILLAGER_SPAWN_EGG, trader.displayName(), lore);
    }

    private String flag(boolean on)
    {
        return Msg.raw(on ? "trader-list.flag-yes" : "trader-list.flag-no");
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(traders().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_CREATE) {new TraderCreateMenu(plugin).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        String id = idBySlot.get(raw);
        if (id == null) {return;}
        TraderType trader = plugin.traders().get(id);
        if (trader == null) {render(); return;}
        new TradeListEditorMenu(plugin, trader).open(p);
    }
}
