package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.game.MatchPlayer;
import me.aver005.escape.trader.Trade;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Магазин торговца. */
public class ShopMenu extends Menu
{
    private final EscapePlugin plugin;
    private final GameSession session;
    private final Map<Integer, Trade> tradeBySlot = new HashMap<>();

    public ShopMenu(EscapePlugin plugin, GameSession session, TraderType trader)
    {
        super(rowsFor(trader) * 9, Msg.get("shop.title-prefix").append(trader.displayName()));
        this.plugin = plugin;
        this.session = session;
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);

        int slot = 10;
        for (Trade trade : trader.getTrades())
        {
            while (isBorder(slot)) {slot++;}
            if (slot >= inventory.getSize()) {break;}

            ItemStack display = trade.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Items.flat(Msg.get("shop.price-lore", Msg.ph("price", trade.price()))));
            meta.lore(lore);
            display.setItemMeta(meta);

            inventory.setItem(slot, display);
            tradeBySlot.put(slot, trade);
            slot++;
        }
    }

    private static int rowsFor(TraderType trader)
    {
        int rows = trader.getTrades().size() / 7 + 3;
        return Math.min(6, Math.max(3, rows));
    }

    private boolean isBorder(int slot)
    {
        return slot < 9 || slot >= inventory.getSize() - 9 || slot % 9 == 0 || (slot + 1) % 9 == 0;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}
        Trade trade = tradeBySlot.get(e.getRawSlot());
        if (trade == null) {return;}

        int gold = Items.countMaterial(p, Material.GOLD_INGOT);
        if (gold < trade.price()) {Msg.send(p, "shop.not-enough-gold"); return;}
        if (p.getInventory().firstEmpty() == -1) {Msg.send(p, "shop.no-space"); return;}

        Items.takeMaterial(p, Material.GOLD_INGOT, trade.price());
        p.getInventory().addItem(trade.item().clone());

        MatchPlayer data = session.matchData(p.getUniqueId());
        if (data != null) {data.trades++;}
        plugin.stats().add(p.getUniqueId(), p.getName(), "trades_completed", 1);

        Msg.send(p, "shop.bought",
            Msg.ph("price", trade.price()),
            Msg.ph("rest", Items.countMaterial(p, Material.GOLD_INGOT)));
    }
}
