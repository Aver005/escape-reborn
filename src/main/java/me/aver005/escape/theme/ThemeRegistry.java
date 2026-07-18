package me.aver005.escape.theme;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Глобальный реестр темок (themes.yml). */
public class ThemeRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, Theme> themes = new LinkedHashMap<>();

    public ThemeRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "themes.yml");}

    public void load()
    {
        themes.clear();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection root = cfg.getConfigurationSection("themes");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            themes.put(id, Theme.load(id, sec));
        }
    }

    public void save()
    {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Theme theme : themes.values())
        {
            theme.save(cfg.createSection("themes." + theme.getId()));
        }
        try {cfg.save(file());}
        catch (IOException e) {plugin.getLogger().severe("Failed to save themes.yml: " + e.getMessage());}
    }

    public Theme get(String id) {return themes.get(id);}
    public boolean exists(String id) {return themes.containsKey(id);}
    public void add(Theme theme) {themes.put(theme.getId(), theme);}
    public Set<String> ids() {return themes.keySet();}
}
