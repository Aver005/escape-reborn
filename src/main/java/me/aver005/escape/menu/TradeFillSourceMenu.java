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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Выбор жителя-источника для наполнения товаров другого жителя ({@link TradeFillMenu}).
 * {@code replace} — заменить товары цели полностью, иначе дополнить. Сама цель из
 * списка исключена. Цены товаров источника сохраняются (копируются независимо).
 */
public class TradeFillSourceMenu extends Menu
{
    private final EscapePlugin plugin;
    private final TraderType target;
    private final boolean replace;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public TradeFillSourceMenu(EscapePlugin plugin, TraderType target, boolean replace)
    {
        super(54, Msg.get(replace ? "trade-fill.source-title-replace" : "trade-fill.source-title-append"));
        this.plugin = plugin;
        this.target = target;
        this.replace = replace;
        render();
    }

    private List<TraderType> traders()
    {
        List<TraderType> out = new ArrayList<>();
        for (TraderType trader : plugin.traders().all())
        {
            if (!trader.getId().equals(target.getId())) {out.add(trader);}
        }
        return out;
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
        renderControls(page, pages, true, Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack icon(TraderType trader)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("trader-list.lore-trades", Msg.ph("count", trader.getTrades().size()))));
        lore.add(Items.flat(Msg.get("trader-list.lore-id", Msg.ph("id", trader.getId()))));
        lore.add(Items.flat(Msg.get(replace ? "trade-fill.source-pick-replace" : "trade-fill.source-pick-append")));
        return Items.named(Material.VILLAGER_SPAWN_EGG, trader.displayName(), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(traders().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_BACK) {new TradeFillMenu(plugin, target).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        String id = idBySlot.get(raw);
        if (id == null) {return;}
        TraderType src = plugin.traders().get(id);
        if (src == null) {render(); return;}
        if (src.getTrades().isEmpty()) {Msg.send(p, "trade-fill.empty-source", Msg.ph("trader", src.getId())); return;}

        int added = src.getTrades().size();
        if (replace) {target.getTrades().clear();}
        for (Trade trade : src.getTrades())
        {
            target.getTrades().add(new Trade(trade.item().clone(), trade.price()));
        }
        plugin.traders().save();
        DebugLog.log(Cat.ADMIN, "trade-fill admin=%s trader=%s mode=%s source=trader:%s added=%d total=%d",
            p.getName(), target.getId(), replace ? "replace" : "append", src.getId(), added, target.getTrades().size());
        if (replace) {Msg.send(p, "trade-fill.replaced", Msg.ph("trader", target.getId()), Msg.ph("n", target.getTrades().size()));}
        else {Msg.send(p, "trade-fill.appended", Msg.ph("trader", target.getId()), Msg.ph("n", added));}
        new TradeListEditorMenu(plugin, target).open(p);
    }
}
