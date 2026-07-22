package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.trader.Trade;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.trader.TraderRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Логика трёх режимов создания/наполнения товаров жителя (переиспользуема из GUI).
 * Строит записи товаров, но НЕ сохраняет — сохранение на вызывающей стороне.
 * Товары из инвентаря/сундука получают цену {@link #DEFAULT_PRICE} (правится
 * кликом в списке); копия жителя цены сохраняет.
 */
public final class TradeCreate
{
    /** Стартовая цена товара, собранного из инвентаря/сундука. */
    public static final int DEFAULT_PRICE = 1;

    private TradeCreate() {}

    /** Товары (цена {@link #DEFAULT_PRICE}) из всех не-воздушных предметов инвентаря. */
    public static List<Trade> tradesFromInventory(Player p)
    {
        List<Trade> out = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            out.add(new Trade(item.clone(), DEFAULT_PRICE));
        }
        return out;
    }

    /**
     * Товары (цена {@link #DEFAULT_PRICE}) из сундука, на который смотрит игрок
     * (в пределах 6 блоков). null — перед игроком нет сундука.
     */
    public static List<Trade> tradesFromChest(Player p)
    {
        Block block = p.getTargetBlockExact(6);
        if (block == null) {return null;}
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {return null;}
        List<Trade> out = new ArrayList<>();
        for (ItemStack item : chest.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            out.add(new Trade(item.clone(), DEFAULT_PRICE));
        }
        return out;
    }

    /** Новый житель: товары = предметы инвентаря игрока (цена {@link #DEFAULT_PRICE}). */
    public static TraderType fromInventory(Player p, String id)
    {
        TraderType t = new TraderType(id);
        t.getTrades().addAll(tradesFromInventory(p));
        return t;
    }

    /** Новый житель из сундука перед игроком; null — сундука нет. */
    public static TraderType fromTargetChest(Player p, String id)
    {
        List<Trade> trades = tradesFromChest(p);
        if (trades == null) {return null;}
        TraderType t = new TraderType(id);
        t.getTrades().addAll(trades);
        return t;
    }

    /** Свободный id вида BASE, BASE2, BASE3... не занятый в реестре. */
    public static String freshId(TraderRegistry reg, String base)
    {
        if (!reg.exists(base)) {return base;}
        for (int i = 2; ; i++)
        {
            String candidate = base + i;
            if (!reg.exists(candidate)) {return candidate;}
        }
    }
}
