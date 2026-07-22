package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.loot.LootCategoryRegistry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Логика трёх режимов создания/наполнения категории лута (переиспользуема из GUI
 * и команд). Строит записи лута, но НЕ сохраняет — сохранение (save) на вызывающей
 * стороне. Предметы из инвентаря/сундука попадают в лут с весом 1 (вес в GUI не
 * редактируется); копия категории веса сохраняет.
 */
public final class LootCreate
{
    private LootCreate() {}

    /** Записи лута (вес 1) из всех не-воздушных предметов инвентаря игрока. */
    public static List<WeightedItem> lootFromInventory(Player p)
    {
        List<WeightedItem> out = new ArrayList<>();
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            out.add(new WeightedItem(item.clone(), 1));
        }
        return out;
    }

    /**
     * Записи лута (вес 1) из сундука, на который смотрит игрок (в пределах 6 блоков).
     * null — перед игроком нет сундука (пустой список — сундук пуст).
     */
    public static List<WeightedItem> lootFromChest(Player p)
    {
        Block block = p.getTargetBlockExact(6);
        if (block == null) {return null;}
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) {return null;}
        List<WeightedItem> out = new ArrayList<>();
        for (ItemStack item : chest.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            out.add(new WeightedItem(item.clone(), 1));
        }
        return out;
    }

    /** Новая категория: лут = все не-воздушные предметы инвентаря игрока (вес 1). */
    public static LootCategory fromInventory(Player p, String id)
    {
        LootCategory cat = new LootCategory(id);
        cat.getLoot().addAll(lootFromInventory(p));
        return cat;
    }

    /**
     * Новая категория из сундука, на который смотрит игрок (в пределах 6 блоков).
     * null — перед игроком нет сундука.
     */
    public static LootCategory fromTargetChest(Player p, String id)
    {
        List<WeightedItem> loot = lootFromChest(p);
        if (loot == null) {return null;}
        LootCategory cat = new LootCategory(id);
        cat.getLoot().addAll(loot);
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
