package me.aver005.escape.loot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.WeightedItem;
import ru.kiviuly.mg.api.util.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * One-time migration for the loot-system redesign.
 *
 * OLD model: each arena stored its own loot pool in {@code arenas/<id>/loot.yml}
 * (key {@code items:}) and its own categories in {@code arenas/<id>/chest-categories.yml}
 * (section {@code categories:}); chest points referenced a per-arena category id.
 *
 * NEW model: loot lives in GLOBAL categories, one file per category at
 * {@code loot/<id>.yml}; chest points hold a LIST of global category ids.
 *
 * This migration reads the old per-arena files directly from disk (Arena no longer
 * loads them), creates global categories {@code <arenaId>_<oldCatId>}, and rewrites
 * every arena's chest points to reference the new global ids. It runs at most once,
 * guarded by the marker file {@code loot/.migrated}.
 */
public final class LootMigration
{
    private static final String MARKER = ".migrated";

    private LootMigration() {}

    public static void run(EscapePlugin plugin)
    {
        File marker = new File(plugin.loot().dir(), MARKER);
        if (marker.exists()) {return;}

        plugin.getLogger().info("Loot migration: starting one-time conversion to global categories");

        int arenasProcessed = 0;
        int categoriesCreated = 0;

        for (Arena arena : plugin.arenas().all().values())
        {
            try
            {
                categoriesCreated += migrateArena(plugin, arena);
                arenasProcessed++;
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Loot migration: arena " + arena.getId() + " failed: " + e.getMessage());
            }
        }

        // pick up everything just written to loot/*.yml
        plugin.loot().load();

        try {marker.createNewFile();}
        catch (IOException e) {plugin.getLogger().warning("Loot migration: could not write marker file: " + e.getMessage());}

        plugin.getLogger().info("Loot migration: done. Arenas processed: " + arenasProcessed
            + ", categories created: " + categoriesCreated);
    }

    /** Migrate a single arena; returns the number of global categories created for it. */
    private static int migrateArena(EscapePlugin plugin, Arena arena)
    {
        File arenaDir = new File(plugin.getDataFolder(), "arenas/" + arena.getId());
        File lootFile = new File(arenaDir, "loot.yml");
        File catFile = new File(arenaDir, "chest-categories.yml");

        List<WeightedItem> pool = readPool(lootFile);
        int created = 0;
        String defaultId = null;

        YamlConfiguration catCfg = YamlConfiguration.loadConfiguration(catFile);
        ConfigurationSection catRoot = catFile.exists() ? catCfg.getConfigurationSection("categories") : null;

        boolean anyCategory = false;
        if (catRoot != null)
        {
            for (String oldCatId : catRoot.getKeys(false))
            {
                ConfigurationSection sec = catRoot.getConfigurationSection(oldCatId);
                if (sec == null) {continue;}
                anyCategory = true;

                String newId = sanitize(arena.getId() + "_" + oldCatId);
                // старая «default» = категория плоских точек: запоминаем её как дефолт арены
                if (oldCatId.equalsIgnoreCase("default")) {defaultId = newId;}
                if (plugin.loot().exists(newId)) {continue;}      // do not clobber a re-run

                LootCategory cat = new LootCategory(newId);
                cat.setNameRaw(sec.getString("name", oldCatId));
                Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
                cat.setIcon(icon != null ? icon : Material.CHEST);
                cat.setWeight(100);
                cat.setMinPerChest(sec.getInt("loot-min", LootCategory.UNLIMITED));
                cat.setMaxPerChest(sec.getInt("loot-max", LootCategory.UNLIMITED));
                cat.setRefillSeconds(Math.max(0, sec.getInt("refill-seconds", 0)));
                cat.setMaxChests(sec.getInt("quota", LootCategory.UNLIMITED));
                // min-chests / per-arena stay UNLIMITED (-1)
                copyPool(cat, pool);

                plugin.loot().save(cat);
                created++;
            }
        }

        // no categories but a real pool -> one sensible fallback category
        if (!anyCategory && !pool.isEmpty())
        {
            String fallbackId = sanitize(arena.getId() + "_default");
            defaultId = fallbackId;
            if (!plugin.loot().exists(fallbackId))
            {
                LootCategory cat = new LootCategory(fallbackId);
                cat.setNameRaw(arena.getId() + " default");
                cat.setIcon(Material.CHEST);
                cat.setWeight(100);
                cat.setMinPerChest(2);
                cat.setMaxPerChest(4);
                cat.setRefillSeconds(180);
                cat.setMaxChests(LootCategory.UNLIMITED);
                copyPool(cat, pool);
                plugin.loot().save(cat);
                created++;
            }
        }

        remapChestSpots(arena, defaultId);
        plugin.arenas().save(arena);
        return created;
    }

    /** Read the arena's old loot pool from loot.yml ({@code items:} list). Missing file -> empty. */
    private static List<WeightedItem> readPool(File lootFile)
    {
        List<WeightedItem> pool = new ArrayList<>();
        if (!lootFile.exists()) {return pool;}

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(lootFile);
        for (Map<?, ?> entry : cfg.getMapList("items"))
        {
            if (entry == null) {continue;}
            ItemStack item;
            if (entry.get("type") instanceof String) {item = Items.fromSpec(entry);}
            else {item = readItem(entry.get("item"));}
            if (item == null) {continue;}
            Object weight = entry.get("weight");
            pool.add(new WeightedItem(item, weight instanceof Number n ? Math.max(1, n.intValue()) : 1));
        }
        return pool;
    }

    /** Clone the pool into a category's loot list (items stay independent of the source). */
    private static void copyPool(LootCategory cat, List<WeightedItem> pool)
    {
        for (WeightedItem entry : pool)
        {
            cat.getLoot().add(new WeightedItem(entry.item().clone(), entry.weight()));
        }
    }

    /**
     * Rewrite each chest point's category list in place: every old category id becomes
     * the global id {@code <arenaId>_<oldCatId>}; an old {@code default}/blank id becomes
     * the fallback id if one was created, otherwise it is dropped. A точка с ПУСТЫМ
     * списком — это старая плоская точка (в старой модели = «default»): её тоже
     * привязываем к дефолтной категории арены, если та есть.
     */
    private static void remapChestSpots(Arena arena, String defaultId)
    {
        for (Map.Entry<Location, List<String>> spot : arena.getChestSpots().entrySet())
        {
            List<String> mapped = new ArrayList<>();
            List<String> old = spot.getValue();
            if (old == null || old.isEmpty())
            {
                if (defaultId != null) {mapped.add(defaultId);}   // плоская точка -> дефолт арены
            }
            else
            {
                for (String oldId : old)
                {
                    String newId = newIdFor(arena.getId(), oldId, defaultId);
                    if (newId != null && !mapped.contains(newId)) {mapped.add(newId);}
                }
            }
            spot.setValue(mapped);
        }
    }

    /** Old chest-point category id -> new global id, or null to drop (blank/default with no fallback). */
    private static String newIdFor(String arenaId, String oldId, String defaultId)
    {
        if (oldId == null || oldId.isBlank() || oldId.equalsIgnoreCase("default")) {return defaultId;}
        return sanitize(arenaId + "_" + oldId);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack readItem(Object raw)
    {
        if (raw instanceof ItemStack is) {return is;}
        if (raw instanceof Map<?, ?> map) {return ItemStack.deserialize((Map<String, Object>) map);}
        return null;
    }

    /** Lowercase and reduce to safe filename chars [a-z0-9_-]; anything else becomes '_'. */
    private static String sanitize(String raw)
    {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++)
        {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {sb.append(c);}
            else {sb.append('_');}
        }
        return sb.toString();
    }
}
