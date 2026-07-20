package me.aver005.escape.modifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Модификатор сессии («Режим дня», §15): один глобальный твист, который
 * раскатывается на весь матч, если лобби за него проголосовало. Эффекты — это
 * множители/добавки к УЖЕ существующим числам матча (зарплата, интервал событий,
 * износ, рефилл, кол-во сундуков и т.п.), поэтому балансно безопасно и не грузит
 * игрока новыми механиками. Только чтение (шаблоны из modifiers.yml).
 */
public class Modifier
{
    private final String id;
    private String nameRaw = "";            // MiniMessage
    private List<String> descRaw = new ArrayList<>();
    private Material icon = Material.PAPER;
    private final Map<String, Double> effects = new LinkedHashMap<>();

    public Modifier(String id) {this.id = id;}

    public static Modifier load(String id, ConfigurationSection sec)
    {
        Modifier m = new Modifier(id);
        m.nameRaw = sec.getString("name", id);
        m.descRaw = new ArrayList<>(sec.getStringList("desc"));
        Material icon = Material.matchMaterial(sec.getString("icon", "PAPER"));
        m.icon = icon != null ? icon : Material.PAPER;
        ConfigurationSection eff = sec.getConfigurationSection("effects");
        if (eff != null)
        {
            for (String key : eff.getKeys(false)) {m.effects.put(key, eff.getDouble(key));}
        }
        return m;
    }

    /** Множитель (по умолчанию 1.0 — нейтрально). */
    public double mult(String key) {return effects.getOrDefault(key, 1.0);}

    /** Аддитивная добавка (по умолчанию 0.0). */
    public double add(String key) {return effects.getOrDefault(key, 0.0);}

    /** Флаг (значение > 0). */
    public boolean flag(String key) {return effects.getOrDefault(key, 0.0) > 0;}

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public List<String> getDescRaw() {return descRaw;}
    public Material getIcon() {return icon;}
}
