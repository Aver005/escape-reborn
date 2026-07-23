package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.List;

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

/**
 * Наполнение товаров существующего жителя теми же тремя способами, что и создание:
 * из инвентаря, из просматриваемого сундука, из другого жителя. Режимы «Дополнить»
 * и «Заменить» (полная перезапись). Замена применяется сразу; пустой источник НЕ
 * стирает товары. Цена новых товаров = {@link TradeCreate#DEFAULT_PRICE}; копия
 * жителя цены сохраняет.
 */
public class TradeFillMenu extends Menu
{
    private static final int APPEND_INVENTORY = 10;
    private static final int APPEND_CHEST = 11;
    private static final int APPEND_TRADER = 12;
    private static final int REPLACE_INVENTORY = 14;
    private static final int REPLACE_CHEST = 15;
    private static final int REPLACE_TRADER = 16;
    private static final int SLOT_BACK_FILL = 22;

    private final EscapePlugin plugin;
    private final TraderType trader;

    public TradeFillMenu(EscapePlugin plugin, TraderType trader)
    {
        super(27, Msg.get("trade-fill.title").append(Component.text(trader.getId())));
        this.plugin = plugin;
        this.trader = trader;
        render();
    }

    private void render()
    {
        inventory.setItem(APPEND_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("trade-fill.append-inventory-name"), Msg.getList("trade-fill.append-inventory-lore")));
        inventory.setItem(APPEND_CHEST, Items.named(Material.CHEST,
            Msg.get("trade-fill.append-chest-name"), Msg.getList("trade-fill.append-chest-lore")));
        inventory.setItem(APPEND_TRADER, Items.named(Material.WRITABLE_BOOK,
            Msg.get("trade-fill.append-trader-name"), Msg.getList("trade-fill.append-trader-lore")));
        inventory.setItem(REPLACE_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("trade-fill.replace-inventory-name"), Msg.getList("trade-fill.replace-inventory-lore")));
        inventory.setItem(REPLACE_CHEST, Items.named(Material.CHEST,
            Msg.get("trade-fill.replace-chest-name"), Msg.getList("trade-fill.replace-chest-lore")));
        inventory.setItem(REPLACE_TRADER, Items.named(Material.WRITABLE_BOOK,
            Msg.get("trade-fill.replace-trader-name"), Msg.getList("trade-fill.replace-trader-lore")));
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
            case APPEND_TRADER -> new TradeFillSourceMenu(plugin, trader, false).open(p);
            case REPLACE_INVENTORY -> applyInventory(p, true);
            case REPLACE_CHEST -> applyChest(p, true);
            case REPLACE_TRADER -> new TradeFillSourceMenu(plugin, trader, true).open(p);
            case SLOT_BACK_FILL -> new TradeListEditorMenu(plugin, trader).open(p);
            default -> {}
        }
    }

    private void applyInventory(Player p, boolean replace)
    {
        List<Trade> trades = TradeCreate.tradesFromInventory(p);
        if (trades.isEmpty()) {Msg.send(p, "trade-fill.empty-inventory"); return;}
        apply(p, trades, replace, "inventory");
    }

    private void applyChest(Player p, boolean replace)
    {
        List<Trade> trades = TradeCreate.tradesFromChest(p);
        if (trades == null) {Msg.send(p, "trade-fill.no-chest"); return;}
        if (trades.isEmpty()) {Msg.send(p, "trade-fill.empty-chest"); return;}
        apply(p, trades, replace, "chest");
    }

    /** Дополнить или заменить товары жителя и сохранить. Возврат в список товаров. */
    private void apply(Player p, List<Trade> trades, boolean replace, String source)
    {
        if (replace) {trader.getTrades().clear();}
        trader.getTrades().addAll(trades);
        plugin.traders().save();
        DebugLog.log(Cat.ADMIN, "trade-fill admin=%s trader=%s mode=%s source=%s added=%d total=%d",
            p.getName(), trader.getId(), replace ? "replace" : "append", source, trades.size(), trader.getTrades().size());
        if (replace) {Msg.send(p, "trade-fill.replaced", Msg.ph("trader", trader.getId()), Msg.ph("n", trader.getTrades().size()));}
        else {Msg.send(p, "trade-fill.appended", Msg.ph("trader", trader.getId()), Msg.ph("n", trades.size()));}
        new TradeListEditorMenu(plugin, trader).open(p);
    }
}
