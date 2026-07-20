package me.aver005.escape.modifier;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Глобальный пул модификаторов сессии (modifiers.yml). Только чтение: каждый
 * матч случайно берёт один как кандидата на голосование в лобби (§15).
 */
public class ModifierRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, Modifier> modifiers = new LinkedHashMap<>();

    public ModifierRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    private File file() {return new File(plugin.getDataFolder(), "modifiers.yml");}

    public void load()
    {
        modifiers.clear();
        File file = file();
        if (!file.exists()) {plugin.saveResource("modifiers.yml", false);}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("modifiers");
        if (root == null) {return;}
        for (String id : root.getKeys(false))
        {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {continue;}
            modifiers.put(id, Modifier.load(id, sec));
        }
    }

    /** Случайный модификатор или null, если пул пуст. */
    public Modifier random(Random random)
    {
        if (modifiers.isEmpty()) {return null;}
        List<Modifier> list = new ArrayList<>(modifiers.values());
        return list.get(random.nextInt(list.size()));
    }

    public Modifier get(String id) {return id == null ? null : modifiers.get(id);}
    public Set<String> ids() {return modifiers.keySet();}
}
