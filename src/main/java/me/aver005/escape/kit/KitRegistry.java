package me.aver005.escape.kit;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Глобальная библиотека кастов (kits.yml в корне плагина). Только чтение:
 * это шаблоны, которые копируются на арены (/escape kit copy). Оригинал
 * командами не редактируется, поэтому рукописный формат и комментарии целы.
 */
public class KitRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "kits.yml");}

    public void load()
    {
        kits.clear();
        File file = file();
        if (!file.exists()) {plugin.saveResource("kits.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("kits");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            kits.put(id, Kit.load(id, sec));
        }
    }

    public Kit get(String id) {return id == null ? null : kits.get(id);}
    public boolean exists(String id) {return id != null && kits.containsKey(id);}
    public Set<String> ids() {return kits.keySet();}
}
