package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Выбор жителя-источника для режима «копия» ({@link TraderCreateMenu}).
 * ЛКМ по жителю — создать его независимую копию под свободным id «<id>_COPY…».
 */
public class TraderCopySourceMenu extends Menu
{
    private final EscapePlugin plugin;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public TraderCopySourceMenu(EscapePlugin plugin)
    {
        super(54, Msg.get("trader-create.copy-title"));
        this.plugin = plugin;
        render();
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
        renderControls(page, pages, true, Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack icon(TraderType trader)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("trader-list.lore-trades", Msg.ph("count", trader.getTrades().size()))));
        lore.add(Items.flat(Msg.get("trader-list.lore-id", Msg.ph("id", trader.getId()))));
        lore.add(Items.flat(Msg.get("trader-create.copy-pick")));
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
        if (raw == SLOT_BACK) {new TraderCreateMenu(plugin).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        String id = idBySlot.get(raw);
        if (id == null) {return;}
        TraderType src = plugin.traders().get(id);
        if (src == null) {render(); return;}
        String newId = TradeCreate.freshId(plugin.traders(), id + "_COPY");
        TraderType copy = src.copyAs(newId);
        plugin.traders().add(copy);
        plugin.traders().save();
        DebugLog.log(Cat.ADMIN, "trader-create admin=%s trader=%s source=copy:%s trades=%d",
            p.getName(), newId, src.getId(), copy.getTrades().size());
        Msg.send(p, "trader-create.created", Msg.ph("id", newId));
        new TradeListEditorMenu(plugin, copy).open(p);
    }
}
