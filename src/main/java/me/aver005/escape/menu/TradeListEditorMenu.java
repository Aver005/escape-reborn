package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.Trade;
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
 * Админ-редактор товаров жителя: пагинированный список трейдов. Клик по трейду
 * крутит цену (лкм +1 / shift +10, пкм -1 / shift -10, Q — убрать), кнопка
 * «Добавить» открывает штатный {@link TradeEditorMenu} (предмет из руки + цена).
 * Меню контролируемое (товары не вытащить); каждое изменение персистится сразу.
 */
public class TradeListEditorMenu extends Menu
{
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 47;
    private static final int SLOT_ADD = 49;
    private static final int SLOT_FILL = 51;
    private static final int SLOT_NEXT = 53;

    private final EscapePlugin plugin;
    private final TraderType trader;
    private final Map<Integer, Integer> tradeIndexBySlot = new HashMap<>();
    private int page;

    public TradeListEditorMenu(EscapePlugin plugin, TraderType trader)
    {
        super(54, Msg.get("trade-list.title-prefix").append(Component.text(trader.getId())));
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
        tradeIndexBySlot.clear();
        List<Trade> trades = trader.getTrades();
        int pages = Math.max(1, (trades.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}

        int from = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++)
        {
            int idx = from + slot;
            if (idx >= trades.size()) {break;}
            inventory.setItem(slot, icon(trades.get(idx)));
            tradeIndexBySlot.put(slot, idx);
        }

        for (int i = PAGE_SIZE; i < 54; i++)
        {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_INFO, Items.named(Material.PAPER,
            Msg.get("trade-list.page-info", Msg.ph("page", page + 1), Msg.ph("pages", pages),
                Msg.ph("n", trades.size()))));
        inventory.setItem(SLOT_ADD, Items.named(Material.EMERALD,
            Msg.get("trade-list.add-name"), Msg.getList("trade-list.add-lore")));
        inventory.setItem(SLOT_FILL, Items.named(Material.HOPPER,
            Msg.get("trade-list.fill-name"), Msg.getList("trade-list.fill-lore")));
        if (page > 0)
        {
            inventory.setItem(SLOT_PREV, Items.named(Material.ARROW, Msg.get("chestsetup.page-prev")));
        }
        if (page < pages - 1)
        {
            inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("chestsetup.page-next")));
        }
        if (trades.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("trade-list.empty-name"), Msg.getList("trade-list.empty-lore")));
        }
    }

    private ItemStack icon(Trade trade)
    {
        ItemStack display = trade.item().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Items.flat(Msg.get("trade-list.entry-price", Msg.ph("price", trade.price()))));
        lore.addAll(Msg.getList("trade-list.entry-lore"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inventory.getSize()) {return;}

        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT) {page++; render(); return;}
        if (raw == SLOT_ADD) {new TradeEditorMenu(plugin, trader).open(p); return;}
        if (raw == SLOT_FILL) {new TradeFillMenu(plugin, trader).open(p); return;}

        Integer idx = tradeIndexBySlot.get(raw);
        if (idx == null || idx < 0 || idx >= trader.getTrades().size()) {return;}

        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP)
        {
            Trade removed = trader.getTrades().remove((int) idx);
            plugin.traders().save();
            p.playSound(p.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.2f);
            DebugLog.log(Cat.ADMIN, "trade-remove admin=%s trader=%s item=%s price=%d",
                p.getName(), trader.getId(), DebugLog.item(removed.item()), removed.price());
            Msg.send(p, "trade-list.removed", Msg.ph("trader", trader.getId()));
            render();
            return;
        }

        int delta = e.isLeftClick() ? (e.isShiftClick() ? 10 : 1) : (e.isShiftClick() ? -10 : -1);
        Trade current = trader.getTrades().get(idx);
        int next = Math.max(1, Math.min(255, current.price() + delta));
        if (next == current.price()) {return;}
        trader.getTrades().set(idx, new Trade(current.item(), next));
        plugin.traders().save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, delta > 0 ? 1.4f : 0.9f);
        render();
    }
}
