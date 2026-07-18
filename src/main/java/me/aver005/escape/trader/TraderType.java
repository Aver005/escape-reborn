package me.aver005.escape.trader;

import java.util.ArrayList;
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
            if (entry.get("type") instanceof String typeName)
            {
                // рукописный формат: {type: BREAD, amount: 3, price: 2}
                Material mat = Material.matchMaterial(typeName);
                if (mat == null || mat.isAir()) {continue;}
                int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
                item = new ItemStack(mat, amount);
            }
            else if (rawItem instanceof ItemStack is) {item = is;}
            else if (rawItem instanceof Map<?, ?> map) {item = ItemStack.deserialize((Map<String, Object>) map);}
            if (item == null || !(rawPrice instanceof Number price)) {continue;}
            t.trades.add(new Trade(item, Math.max(1, price.intValue())));
        }
        t.themes.addAll(sec.getStringList("themes"));
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
    }

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String nameRaw) {this.nameRaw = nameRaw;}
    public Component displayName() {return Msg.mm(nameRaw);}
    public List<Trade> getTrades() {return trades;}
    public List<String> getThemes() {return themes;}
    public boolean isShop() {return !trades.isEmpty();}
    public boolean isOverseer() {return !themes.isEmpty();}
}
