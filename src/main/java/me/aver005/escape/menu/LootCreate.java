package me.aver005.escape.menu;

import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.loot.LootCategoryRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Логика трёх режимов создания категории лута (переиспользуема из GUI и команд).
 * Строит {@link LootCategory}, но НЕ сохраняет — сохранение (save) на вызывающей
 * стороне. Все предметы попадают в лут с весом 1.
 */
public final class LootCreate
{
    private LootCreate() {}

    /** Новая категория: лут = все не-воздушные предметы инвентаря игрока (вес 1). */
    public static LootCategory fromInventory(Player p, String id)
    {
        LootCategory cat = new LootCategory(id);
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            cat.getLoot().add(new WeightedItem(item.clone(), 1));
        }
        return cat;
    }

    /**
     * Новая категория из сундука, на который смотрит игрок (в пределах 6 блоков).
     * null — перед игроком нет сундука.
     */
    public static LootCategory fromTargetChest(Player p, String id)
    {
        Block block = p.getTargetBlockExact(6);
        if (block == null) {return null;}
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {return null;}
        LootCategory cat = new LootCategory(id);
        for (ItemStack item : chest.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            cat.getLoot().add(new WeightedItem(item.clone(), 1));
        }
        return cat;
    }

    /** Глубокая независимая копия категории-источника под новым id. */
    public static LootCategory copyFrom(LootCategory src, String id)
    {
        return src.copyAs(id);
    }

    /** Свободный id вида base, base2, base3... не занятый в реестре. */
    public static String freshId(LootCategoryRegistry reg, String base)
    {
        if (!reg.exists(base)) {return base;}
        for (int i = 2; ; i++)
        {
            String candidate = base + i;
            if (!reg.exists(candidate)) {return candidate;}
        }
    }
}
