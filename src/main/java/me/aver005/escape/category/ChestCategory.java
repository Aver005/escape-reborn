package me.aver005.escape.category;

import java.util.Random;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Категория сундука: сколько лута кладётся (loot-min/max), сколько таких
 * сундуков появляется за матч (quota, крутится модификатором chest-count-mult)
 * и через сколько идёт рефилл (refill-seconds; 0 = без рефилла). Пул лута —
 * общий на арену (категория его не меняет). Хранится как касты: глобальный
 * шаблон chest-categories.yml копируется на арену
 * (arenas/&lt;id&gt;/chest-categories.yml) и балансится отдельно.
 */
public class ChestCategory
{
    /** Категория точек без явного назначения (обратная совместимость старых арен). */
    public static final String DEFAULT_ID = "default";

    private final String id;
    private String nameRaw;             // MiniMessage — имя в меню/на жезле
    private Material icon = Material.CHEST;
    private int lootMin = 2;            // мин. число заполненных слотов сундука
    private int lootMax = 4;            // макс. число заполненных слотов сундука
    private int refillSeconds = 180;   // время рефилла; 0 = без рефилла
    private int quota = 8;             // сколько сундуков этой категории за матч

    public ChestCategory(String id) {this.id = id;}

    // ===== persistence =====

    public static ChestCategory load(String id, ConfigurationSection sec)
    {
        ChestCategory cat = new ChestCategory(id);
        cat.nameRaw = sec.getString("name", id);
        Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
        cat.icon = icon != null ? icon : Material.CHEST;
        cat.lootMin = Math.max(0, sec.getInt("loot-min", 2));
        cat.lootMax = Math.max(cat.lootMin, sec.getInt("loot-max", 4));
        cat.refillSeconds = Math.max(0, sec.getInt("refill-seconds", 180));
        cat.quota = Math.max(0, sec.getInt("quota", 8));
        return cat;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("name", nameRaw);
        sec.set("icon", icon.name());
        sec.set("loot-min", lootMin);
        sec.set("loot-max", lootMax);
        sec.set("refill-seconds", refillSeconds);
        sec.set("quota", quota);
    }

    /** Глубокая копия под новым id — для «копии глобального шаблона на арену». */
    public ChestCategory copy(String newId)
    {
        ChestCategory cat = new ChestCategory(newId);
        cat.nameRaw = nameRaw;
        cat.icon = icon;
        cat.lootMin = lootMin;
        cat.lootMax = lootMax;
        cat.refillSeconds = refillSeconds;
        cat.quota = quota;
        return cat;
    }

    /** Случайное число заполненных слотов в пределах [loot-min, loot-max]. */
    public int rollItemCount(Random r)
    {
        if (lootMax <= lootMin) {return lootMin;}
        return lootMin + r.nextInt(lootMax - lootMin + 1);
    }

    // ===== accessors =====

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String v) {this.nameRaw = v;}
    public Material getIcon() {return icon;}
    public void setIcon(Material v) {this.icon = v;}
    public int getLootMin() {return lootMin;}
    public void setLootMin(int v) {this.lootMin = Math.max(0, v);}
    public int getLootMax() {return lootMax;}
    public void setLootMax(int v) {this.lootMax = Math.max(0, v);}
    public int getRefillSeconds() {return refillSeconds;}
    public void setRefillSeconds(int v) {this.refillSeconds = Math.max(0, v);}
    public int getQuota() {return quota;}
    public void setQuota(int v) {this.quota = Math.max(0, v);}
}
