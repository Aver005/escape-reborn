package me.aver005.escape.category;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Глобальная библиотека категорий сундуков (chest-categories.yml в корне
 * плагина). Только чтение: это шаблоны, которые копируются на арены
 * (/escape chestcat copy). Оригинал командами не редактируется, поэтому
 * рукописный формат и комментарии целы.
 */
public class CategoryRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, ChestCategory> categories = new LinkedHashMap<>();

    public CategoryRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "chest-categories.yml");}

    public void load()
    {
        categories.clear();
        File file = file();
        if (!file.exists()) {plugin.saveResource("chest-categories.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("categories");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            categories.put(id, ChestCategory.load(id, sec));
        }
    }

    public ChestCategory get(String id) {return id == null ? null : categories.get(id);}
    public boolean exists(String id) {return id != null && categories.containsKey(id);}
    public Set<String> ids() {return categories.keySet();}
}
