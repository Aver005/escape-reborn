package me.aver005.escape.trader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/** Тип торговца (глобальный): имя и список товаров. */
public class TraderType
{
    private final String id;
    private String nameRaw; // MiniMessage
    private final List<Trade> trades = new ArrayList<>();

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
            if (rawItem instanceof ItemStack is) {item = is;}
            else if (rawItem instanceof Map<?, ?> map) {item = ItemStack.deserialize((Map<String, Object>) map);}
            if (item == null || !(rawPrice instanceof Number price)) {continue;}
            t.trades.add(new Trade(item, price.intValue()));
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
    }

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String nameRaw) {this.nameRaw = nameRaw;}
    public Component displayName() {return Msg.mm(nameRaw);}
    public List<Trade> getTrades() {return trades;}
}
