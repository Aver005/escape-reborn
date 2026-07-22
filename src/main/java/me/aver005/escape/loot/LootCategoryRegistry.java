package me.aver005.escape.loot;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Глобальная библиотека категорий лута: одна категория = один файл
 * {@code loot/&lt;id&gt;.yml} в папке плагина. В отличие от старого
 * chest-categories.yml, категории здесь редактируются (GUI/команды) и сразу
 * пишутся на диск. Точки сундуков арен ссылаются на категории по id.
 */
public class LootCategoryRegistry
{
    private final JavaPlugin plugin;
    private final Map<String, LootCategory> categories = new LinkedHashMap<>();

    public LootCategoryRegistry(JavaPlugin plugin) {this.plugin = plugin;}

    /** Папка loot/ в каталоге плагина. */
    public File dir()
    {
        File dir = new File(plugin.getDataFolder(), "loot");
        if (!dir.exists()) {dir.mkdirs();}
        return dir;
    }

    private File fileOf(String id) {return new File(dir(), id + ".yml");}

    public void load()
    {
        categories.clear();
        File dir = dir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0)
        {
            // свежая установка: распакуем пример из jar, чтобы папка не пустовала
            try {plugin.saveResource("loot/example.yml", false);}
            catch (IllegalArgumentException ignored) {}
            files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        }
        if (files == null) {return;}
        for (File file : files)
        {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            categories.put(id, LootCategory.load(id, cfg));
        }
    }

    /** Записать категорию на диск (loot/<id>.yml) и в память. */
    public void save(LootCategory cat)
    {
        categories.put(cat.getId(), cat);
        YamlConfiguration cfg = new YamlConfiguration();
        cat.save(cfg);
        try {cfg.save(fileOf(cat.getId()));}
        catch (IOException e) {plugin.getLogger().severe("Failed to save loot category " + cat.getId() + ": " + e.getMessage());}
    }

    /** Удалить категорию из памяти и с диска. */
    public boolean delete(String id)
    {
        if (categories.remove(id) == null) {return false;}
        File file = fileOf(id);
        if (file.exists()) {file.delete();}
        return true;
    }

    public void add(LootCategory cat) {categories.put(cat.getId(), cat);}
    public LootCategory get(String id) {return id == null ? null : categories.get(id);}
    public boolean exists(String id) {return id != null && categories.containsKey(id);}
    public Set<String> ids() {return categories.keySet();}
    public Collection<LootCategory> all() {return categories.values();}
    public boolean isEmpty() {return categories.isEmpty();}

    /** Категория с наибольшим весом среди тех, у кого есть лут (для динамических сундуков). */
    public LootCategory highestWeightWithLoot()
    {
        LootCategory best = null;
        for (LootCategory cat : categories.values())
        {
            if (!cat.hasLoot()) {continue;}
            if (best == null || cat.getWeight() > best.getWeight()) {best = cat;}
        }
        return best;
    }
}
