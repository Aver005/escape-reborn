package me.aver005.escape.trader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Тип NPC (глобальный): имя, товары и/или темки.
 * Есть товары — торговец, есть темки — смотрящий; можно совмещать (§10).
 */
public class TraderType
{
    private final String id;
    private String nameRaw; // MiniMessage
    private final List<Trade> trades = new ArrayList<>();
    private final List<String> themes = new ArrayList<>(); // id темок из ThemeRegistry
    // «Мусорщик» (§14): скупка сломанного за золото. Цена = база(материал) x доля износа.
    private final Map<Material, Integer> scrap = new LinkedHashMap<>(); // материал -> базовая цена скрапа
    private int scrapMinWearPercent = 25;                               // берёт только вещи, изношенные >= %

    public TraderType(String id)
    {
        this.id = id;
        this.nameRaw = id;
    }

    @SuppressWarnings("unchecked")
    public static TraderType load(String id, ConfigurationSection sec)
    {
        TraderType t = new TraderType(id);
        t.nameRaw = sec.getString("name", id);
        for (Map<?, ?> entry : sec.getMapList("trades"))
        {
            Object rawItem = entry.get("item");
            Object rawPrice = entry.get("price");
            ItemStack item = null;
            if (entry.get("type") instanceof String)
            {
                // рукописный формат: {type: BREAD, amount: 3, price: 2}
                // + potion/effects/enchants/name (см. Items.fromSpec)
                item = me.aver005.escape.util.Items.fromSpec(entry);
            }
            else if (rawItem instanceof ItemStack is) {item = is;}
            else if (rawItem instanceof Map<?, ?> map) {item = ItemStack.deserialize((Map<String, Object>) map);}
            if (item == null || !(rawPrice instanceof Number price)) {continue;}
            t.trades.add(new Trade(item, Math.max(1, price.intValue())));
        }
        t.themes.addAll(sec.getStringList("themes"));

        ConfigurationSection scrapSec = sec.getConfigurationSection("scrap");
        if (scrapSec != null)
        {
            t.scrapMinWearPercent = Math.max(0, Math.min(99, scrapSec.getInt("min-wear-percent", 25)));
            ConfigurationSection prices = scrapSec.getConfigurationSection("prices");
            if (prices != null)
            {
                for (String key : prices.getKeys(false))
                {
                    Material mat = Material.matchMaterial(key);
                    if (mat != null && mat.getMaxDurability() > 0)
                    {
                        t.scrap.put(mat, Math.max(1, prices.getInt(key)));
                    }
                }
            }
        }
        return t;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("name", nameRaw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Trade trade : trades)
        {
            list.add(Map.of("item", trade.item().serialize(), "price", trade.price()));
        }
        sec.set("trades", list);
        sec.set("themes", themes);

        if (!scrap.isEmpty())
        {
            ConfigurationSection scrapSec = sec.createSection("scrap");
            scrapSec.set("min-wear-percent", scrapMinWearPercent);
            ConfigurationSection prices = scrapSec.createSection("prices");
            for (Map.Entry<Material, Integer> e : scrap.entrySet())
            {
                prices.set(e.getKey().name(), e.getValue());
            }
        }
    }

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String nameRaw) {this.nameRaw = nameRaw;}
    public Component displayName() {return Msg.mm(nameRaw);}
    public List<Trade> getTrades() {return trades;}
    public List<String> getThemes() {return themes;}
    public Map<Material, Integer> getScrapPrices() {return scrap;}
    public int getScrapMinWearPercent() {return scrapMinWearPercent;}
    public void setScrapMinWearPercent(int v) {this.scrapMinWearPercent = Math.max(0, Math.min(99, v));}
    public boolean isShop() {return !trades.isEmpty();}
    public boolean isOverseer() {return !themes.isEmpty();}
    public boolean isScavenger() {return !scrap.isEmpty();}
}
