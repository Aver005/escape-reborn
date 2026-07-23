package me.aver005.escape.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import me.aver005.escape.arena.WeightedItem;
import ru.kiviuly.mg.api.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Глобальная категория лута (файл {@code loot/&lt;id&gt;.yml}). Несёт в себе И лут,
 * И все лимиты распределения. Точка сундука на арене ссылается на категорию по id;
 * состав лута категория не делит с ареной (в отличие от старой модели общего пула).
 *
 * Лимиты: везде {@code -1} = без ограничения.
 *  - weight               вес среди категорий (1..1000): чем больше, тем чаще
 *                         точка с несколькими категориями достаётся этой, и тем
 *                         раньше она резервирует свой минимум сундуков.
 *  - min/max-per-chest    число заполняемых слотов в одном сундуке.
 *  - min/max-per-arena    число слотов этой категории на всю арену (бюджет).
 *  - min/max-chests       сколько сундуков этой категории появляется за матч.
 *  - refill-seconds       свой рефилл (0 = без рефилла).
 * Износ предмета — это его собственный damage (выставляется в спеке/GUI);
 * случайный износ арены (applyWear) такой предмет не трогает.
 */
public class LootCategory
{
    /** Значение любого лимита «без ограничения». */
    public static final int UNLIMITED = -1;
    private static final int CHEST_SLOTS = 27;

    private final String id;
    private String nameRaw;          // MiniMessage
    private Material icon = Material.CHEST;
    private int weight = 100;        // 1..1000
    private int minPerChest = UNLIMITED;
    private int maxPerChest = UNLIMITED;
    private int minPerArena = UNLIMITED;
    private int maxPerArena = UNLIMITED;
    private int minChests = UNLIMITED;
    private int maxChests = UNLIMITED;
    private int refillSeconds = 0;   // 0 = без рефилла
    private final List<WeightedItem> loot = new ArrayList<>();

    public LootCategory(String id)
    {
        this.id = id;
        this.nameRaw = id;
    }

    // ===== persistence (файл целиком = одна категория) =====

    public static LootCategory load(String id, YamlConfiguration cfg)
    {
        LootCategory cat = new LootCategory(id);
        cat.nameRaw = cfg.getString("name", id);
        Material icon = Material.matchMaterial(cfg.getString("icon", "CHEST"));
        cat.icon = icon != null ? icon : Material.CHEST;
        cat.weight = Math.max(1, Math.min(1000, cfg.getInt("weight", 100)));
        cat.minPerChest = limit(cfg.getInt("min-per-chest", UNLIMITED));
        cat.maxPerChest = limit(cfg.getInt("max-per-chest", UNLIMITED));
        cat.minPerArena = limit(cfg.getInt("min-per-arena", UNLIMITED));
        cat.maxPerArena = limit(cfg.getInt("max-per-arena", UNLIMITED));
        cat.minChests = limit(cfg.getInt("min-chests", UNLIMITED));
        cat.maxChests = limit(cfg.getInt("max-chests", UNLIMITED));
        cat.refillSeconds = Math.max(0, cfg.getInt("refill-seconds", 0));

        for (Map<?, ?> entry : cfg.getMapList("loot"))
        {
            // рукописный формат {type: STONE_SWORD, weight: 20, amount: 1, ...}
            // (см. Items.fromSpec) либо сериализованный {item: {...}, weight: N}
            ItemStack item;
            if (entry.get("type") instanceof String) {item = Items.fromSpec(entry);}
            else {item = readItem(entry.get("item"));}
            if (item == null) {continue;}
            Object weight = entry.get("weight");
            cat.loot.add(new WeightedItem(item, weight instanceof Number n ? Math.max(1, n.intValue()) : 1));
        }
        return cat;
    }

    public void save(YamlConfiguration cfg)
    {
        cfg.set("name", nameRaw);
        cfg.set("icon", icon.name());
        cfg.set("weight", weight);
        cfg.set("min-per-chest", minPerChest);
        cfg.set("max-per-chest", maxPerChest);
        cfg.set("min-per-arena", minPerArena);
        cfg.set("max-per-arena", maxPerArena);
        cfg.set("min-chests", minChests);
        cfg.set("max-chests", maxChests);
        cfg.set("refill-seconds", refillSeconds);
        List<Map<String, Object>> items = new ArrayList<>();
        for (WeightedItem entry : loot)
        {
            items.add(Map.of("item", entry.item().serialize(), "weight", entry.weight()));
        }
        cfg.set("loot", items);
    }

    /** Глубокая копия под новым id (предметы независимы от оригинала). */
    public LootCategory copyAs(String newId)
    {
        LootCategory cat = new LootCategory(newId);
        cat.nameRaw = nameRaw;
        cat.icon = icon;
        cat.weight = weight;
        cat.minPerChest = minPerChest;
        cat.maxPerChest = maxPerChest;
        cat.minPerArena = minPerArena;
        cat.maxPerArena = maxPerArena;
        cat.minChests = minChests;
        cat.maxChests = maxChests;
        cat.refillSeconds = refillSeconds;
        for (WeightedItem entry : loot)
        {
            cat.loot.add(new WeightedItem(entry.item().clone(), entry.weight()));
        }
        return cat;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack readItem(Object raw)
    {
        if (raw instanceof ItemStack is) {return is;}
        if (raw instanceof Map<?, ?> map) {return ItemStack.deserialize((Map<String, Object>) map);}
        return null;
    }

    private static int limit(int v) {return v < 0 ? UNLIMITED : v;}

    // ===== рантайм-хелперы =====

    /** Нижняя граница слотов в сундуке, разрешённая в 0..27 (UNLIMITED = 0). */
    public int effMinPerChest()
    {
        int v = minPerChest < 0 ? 0 : minPerChest;
        return Math.max(0, Math.min(CHEST_SLOTS, v));
    }

    /** Верхняя граница слотов в сундуке, разрешённая в 0..27 (UNLIMITED = 27). */
    public int effMaxPerChest()
    {
        int v = maxPerChest < 0 ? CHEST_SLOTS : maxPerChest;
        return Math.max(effMinPerChest(), Math.min(CHEST_SLOTS, v));
    }

    /** Случайное число слотов [effMin, effMax] для одного сундука. */
    public int rollSlotCount(Random r)
    {
        int min = effMinPerChest();
        int max = effMaxPerChest();
        return max <= min ? min : min + r.nextInt(max - min + 1);
    }

    public boolean hasLoot() {return !loot.isEmpty();}

    /** Взвешенно-случайный предмет из лута (клон) или null, если лута нет. */
    public ItemStack pickItem(Random r)
    {
        if (loot.isEmpty()) {return null;}
        int total = 0;
        for (WeightedItem entry : loot) {total += entry.weight();}
        int roll = r.nextInt(total);
        for (WeightedItem entry : loot)
        {
            roll -= entry.weight();
            if (roll < 0) {return entry.item().clone();}
        }
        return loot.get(loot.size() - 1).item().clone();
    }

    // ===== accessors =====

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String v) {this.nameRaw = v;}
    public Material getIcon() {return icon;}
    public void setIcon(Material v) {this.icon = v != null ? v : Material.CHEST;}
    public int getWeight() {return weight;}
    public void setWeight(int v) {this.weight = Math.max(1, Math.min(1000, v));}
    public int getMinPerChest() {return minPerChest;}
    public void setMinPerChest(int v) {this.minPerChest = limit(v);}
    public int getMaxPerChest() {return maxPerChest;}
    public void setMaxPerChest(int v) {this.maxPerChest = limit(v);}
    public int getMinPerArena() {return minPerArena;}
    public void setMinPerArena(int v) {this.minPerArena = limit(v);}
    public int getMaxPerArena() {return maxPerArena;}
    public void setMaxPerArena(int v) {this.maxPerArena = limit(v);}
    public int getMinChests() {return minChests;}
    public void setMinChests(int v) {this.minChests = limit(v);}
    public int getMaxChests() {return maxChests;}
    public void setMaxChests(int v) {this.maxChests = limit(v);}
    public int getRefillSeconds() {return refillSeconds;}
    public void setRefillSeconds(int v) {this.refillSeconds = Math.max(0, v);}
    public List<WeightedItem> getLoot() {return loot;}
}
