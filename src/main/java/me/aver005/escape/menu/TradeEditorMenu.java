package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.Trade;
import me.aver005.escape.trader.TraderType;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/** Админ-редактор: добавить товар торговцу (слот 16 — предмет, слот 12 — цена). */
public class TradeEditorMenu extends Menu
{
    private static final int SLOT_PRICE = 12;
    private static final int SLOT_ITEM = 16;
    private static final int SLOT_REMOVE_10 = 10;
    private static final int SLOT_REMOVE_1 = 11;
    private static final int SLOT_ADD_1 = 13;
    private static final int SLOT_ADD_10 = 14;
    private static final int SLOT_CANCEL = 18;
    private static final int SLOT_SAVE = 26;

    private final EscapePlugin plugin;
    private final TraderType trader;
    private int price = 1;

    public TradeEditorMenu(EscapePlugin plugin, TraderType trader)
    {
        super(27, Msg.get("trade-editor.title-prefix").append(Component.text(trader.getId())));
        this.plugin = plugin;
        this.trader = trader;
        inventory.setItem(SLOT_REMOVE_10, Items.named(Material.ORANGE_STAINED_GLASS_PANE, Msg.get("trade-editor.remove-10")));
        inventory.setItem(SLOT_REMOVE_1, Items.named(Material.YELLOW_STAINED_GLASS_PANE, Msg.get("trade-editor.remove-1")));
        inventory.setItem(SLOT_ADD_1, Items.named(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Msg.get("trade-editor.add-1")));
        inventory.setItem(SLOT_ADD_10, Items.named(Material.GREEN_STAINED_GLASS_PANE, Msg.get("trade-editor.add-10")));
        inventory.setItem(SLOT_CANCEL, Items.named(Material.RED_STAINED_GLASS_PANE, Msg.get("trade-editor.cancel")));
        inventory.setItem(SLOT_SAVE, Items.named(Material.LIME_STAINED_GLASS_PANE, Msg.get("trade-editor.save")));
        renderPrice();
        for (int i = 0; i < 27; i++)
        {
            if (i != SLOT_ITEM && inventory.getItem(i) == null)
            {
                inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }

    private void renderPrice()
    {
        ItemStack gold = Items.named(Material.GOLD_INGOT,
            Msg.get("trade-editor.price-name", Msg.ph("price", price)));
        gold.setAmount(Math.max(1, Math.min(64, price)));
        inventory.setItem(SLOT_PRICE, gold);
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int slot = e.getRawSlot();
        int topSize = inventory.getSize();

        // клики в своём инвентаре и по слоту предмета — свободные
        if (slot >= topSize) {return;}
        if (slot == SLOT_ITEM) {return;}

        e.setCancelled(true);
        switch (slot)
        {
            case SLOT_ADD_1 -> changePrice(p, 1);
            case SLOT_ADD_10 -> changePrice(p, 10);
            case SLOT_REMOVE_1 -> changePrice(p, -1);
            case SLOT_REMOVE_10 -> changePrice(p, -10);
            case SLOT_CANCEL -> p.closeInventory();
            case SLOT_SAVE -> save(p);
            default -> {}
        }
    }

    private void changePrice(Player p, int delta)
    {
        int next = price + delta;
        if (next > 255) {Msg.send(p, "admin.trade-max-price"); return;}
        if (next < 1) {Msg.send(p, "admin.trade-min-price"); return;}
        price = next;
        renderPrice();
    }

    private void save(Player p)
    {
        ItemStack item = inventory.getItem(SLOT_ITEM);
        if (item == null || item.getType().isAir()) {Msg.send(p, "admin.trade-no-item"); return;}
        trader.getTrades().add(new Trade(item.clone(), price));
        plugin.traders().save();
        inventory.setItem(SLOT_ITEM, null);
        p.closeInventory();
        Msg.send(p, "admin.trade-saved", Msg.ph("trader", trader.getId()));
    }

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        // вернуть предмет из слота, если не сохранили
        ItemStack item = inventory.getItem(SLOT_ITEM);
        if (item != null && !item.getType().isAir())
        {
            e.getPlayer().getInventory().addItem(item);
            inventory.setItem(SLOT_ITEM, null);
        }
    }
}
