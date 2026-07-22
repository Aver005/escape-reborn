package me.aver005.escape.trader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Глобальный реестр типов торговцев (traders.yml). */
public class TraderRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, TraderType> traders = new LinkedHashMap<>();

    public TraderRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "traders.yml");}

    public void load()
    {
        traders.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection root = cfg.getConfigurationSection("traders");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            traders.put(id, TraderType.load(id, sec));
        }
    }

    public void save()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        for (TraderType trader : traders.values())
        {
            trader.save(cfg.createSection("traders." + trader.getId()));
        }
        try {cfg.save(file());}
        catch (IOException e) {plugin.getLogger().severe("Failed to save traders.yml: " + e.getMessage());}
    }

    public TraderType get(String id) {return traders.get(id);}
    public boolean exists(String id) {return traders.containsKey(id);}
    public void add(TraderType trader) {traders.put(trader.getId(), trader);}
    public Set<String> ids() {return traders.keySet();}
    public Collection<TraderType> all() {return traders.values();}
    public boolean isEmpty() {return traders.isEmpty();}

    /** Поиск по отображаемому имени (для клика по жителю). */
    public TraderType byDisplayName(Component name)
    {
        for (TraderType trader : traders.values())
        {
            if (trader.displayName().equals(name)) {return trader;}
        }
        return null;
    }
}
