package me.aver005.escape.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.category.ChestCategory;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Арена: настройки + сконфигурированные точки.
 * Хранится в папке arenas/<id>/ тремя файлами: arena.yml, locations.yml, loot.yml.
 */
public class Arena
{
    private final String id;

    // arena.yml
    private String displayNameRaw;
    private String descriptionRaw = "";
    private String worldName;
    private boolean enabled = false;
    private int minPlayers = 2;
    private int maxPlayers = 12;
    private int chestCount = 75;
    private int traderCount = 32;
    private int tableCount = 5;
    private int durationSeconds = 1200;
    private int eventIntervalSeconds = 210;
    private int salaryIntervalSeconds = 600;
    private int salaryGold = 16;
    private int glowSecondsBeforeEnd = 600;
    private int glowBonusGold = 18;
    private int startDelaySeconds = 60;
    private int startDelayFullSeconds = 10;
    private int forkUses = 1;
    private int startGold = 24;
    private int wearMinPercent = 40;  // случайный износ лута с прочностью, %
    private int wearMaxPercent = 90;  // 0 = износ выключен
    private boolean dynamicChests = false; // немаркированный сундук при открытии становится игровым
    private Location lobby;
    private List<String> contractIds = new ArrayList<>();
    private List<String> deadMessages = new ArrayList<>();

    // locations.yml
    private List<Location> spawns = new ArrayList<>();
    private List<Location> finalSpawns = new ArrayList<>();
    private final Map<Location, String> chestSpots = new LinkedHashMap<>();   // точка -> id категории
    private List<Location> tableSpots = new ArrayList<>();
    private List<Location> oreSpots = new ArrayList<>();
    private final Map<Location, String> levers = new LinkedHashMap<>();       // точка -> имя локации
    private final Map<Location, String> traderSpots = new LinkedHashMap<>();  // точка -> тип торговца

    // loot.yml
    private final List<WeightedItem> loot = new ArrayList<>();

    // kits.yml — стартовые наборы («касты»), копии глобальных шаблонов
    private final List<Kit> kits = new ArrayList<>();
    private String defaultKit = "random";  // выбор по умолчанию: random | none | <id каста>

    // chest-categories.yml — категории сундуков (квоты/лут/рефилл), копии глобальных шаблонов
    private final List<ChestCategory> chestCategories = new ArrayList<>();

    // runtime
    private GameSession session;

    public Arena(String id)
    {
        this.id = id;
        this.displayNameRaw = id;
    }

    // ===== persistence =====

    public static Arena load(File folder)
    {
        Arena arena = new Arena(folder.getName());
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(folder, "arena.yml"));
        arena.displayNameRaw = cfg.getString("display-name", arena.id);
        arena.descriptionRaw = cfg.getString("description", "");
        arena.worldName = cfg.getString("world");
        arena.enabled = cfg.getBoolean("enabled", false);
        arena.minPlayers = cfg.getInt("min-players", 2);
        arena.maxPlayers = cfg.getInt("max-players", 12);
        arena.chestCount = cfg.getInt("chest-count", 75);
        arena.traderCount = cfg.getInt("trader-count", 32);
        arena.tableCount = cfg.getInt("table-count", 5);
        arena.durationSeconds = cfg.getInt("duration-seconds", 1200);
        arena.eventIntervalSeconds = cfg.getInt("event-interval-seconds", 210);
        arena.salaryIntervalSeconds = cfg.getInt("salary-interval-seconds", 600);
        arena.salaryGold = cfg.getInt("salary-gold", 16);
        arena.glowSecondsBeforeEnd = cfg.getInt("glow-seconds-before-end", 600);
        arena.glowBonusGold = cfg.getInt("glow-bonus-gold", 18);
        arena.startDelaySeconds = cfg.getInt("start-delay-seconds", 60);
        arena.startDelayFullSeconds = cfg.getInt("start-delay-full-seconds", 10);
        arena.forkUses = cfg.getInt("fork-uses", 1);
        arena.startGold = cfg.getInt("start-gold", 24);
        arena.wearMinPercent = cfg.getInt("wear-min-percent", 40);
        arena.wearMaxPercent = cfg.getInt("wear-max-percent", 90);
        arena.dynamicChests = cfg.getBoolean("dynamic-chests", false);
        arena.lobby = cfg.getLocation("lobby");
        arena.contractIds = new ArrayList<>(cfg.getStringList("contracts"));
        arena.deadMessages = new ArrayList<>(cfg.getStringList("dead-messages"));

        YamlConfiguration locs = YamlConfiguration.loadConfiguration(new File(folder, "locations.yml"));
        arena.spawns = readLocations(locs, "spawns");
        arena.finalSpawns = readLocations(locs, "final-spawns");
        readChestSpots(locs, arena.chestSpots);
        arena.tableSpots = readLocations(locs, "tables");
        arena.oreSpots = readLocations(locs, "ores");
        readNamedLocations(locs, "levers", "name", arena.levers);
        readNamedLocations(locs, "traders", "type", arena.traderSpots);

        YamlConfiguration lootCfg = YamlConfiguration.loadConfiguration(new File(folder, "loot.yml"));
        for (Map<?, ?> entry : lootCfg.getMapList("items"))
        {
            // простой рукописный формат: {type: STONE_SWORD, weight: 20, amount: 1}
            // + potion/effects/enchants/name (см. Items.fromSpec)
            ItemStack item;
            if (entry.get("type") instanceof String)
            {
                item = me.aver005.escape.util.Items.fromSpec(entry);
            }
            else
            {
                item = readItem(entry.get("item"));
            }
            Object weight = entry.get("weight");
            if (item == null) {continue;}
            arena.loot.add(new WeightedItem(item, weight instanceof Number n ? Math.max(1, n.intValue()) : 1));
        }

        YamlConfiguration kitsCfg = YamlConfiguration.loadConfiguration(new File(folder, "kits.yml"));
        arena.defaultKit = kitsCfg.getString("default-kit", "random");
        ConfigurationSection kitsRoot = kitsCfg.getConfigurationSection("kits");
        if (kitsRoot != null)
        {
            for (String kid : kitsRoot.getKeys(false))
            {
                ConfigurationSection ks = kitsRoot.getConfigurationSection(kid);
                if (ks != null) {arena.kits.add(Kit.load(kid, ks));}
            }
        }

        YamlConfiguration catCfg = YamlConfiguration.loadConfiguration(new File(folder, "chest-categories.yml"));
        ConfigurationSection catRoot = catCfg.getConfigurationSection("categories");
        if (catRoot != null)
        {
            for (String cid : catRoot.getKeys(false))
            {
                ConfigurationSection cs = catRoot.getConfigurationSection(cid);
                if (cs != null) {arena.chestCategories.add(ChestCategory.load(cid, cs));}
            }
        }
        return arena;
    }

    public void save(File folder)
    {
        folder.mkdirs();
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("display-name", displayNameRaw);
        cfg.set("description", descriptionRaw);
        cfg.set("world", worldName);
        cfg.set("enabled", enabled);
        cfg.set("min-players", minPlayers);
        cfg.set("max-players", maxPlayers);
        cfg.set("chest-count", chestCount);
        cfg.set("trader-count", traderCount);
        cfg.set("table-count", tableCount);
        cfg.set("duration-seconds", durationSeconds);
        cfg.set("event-interval-seconds", eventIntervalSeconds);
        cfg.set("salary-interval-seconds", salaryIntervalSeconds);
        cfg.set("salary-gold", salaryGold);
        cfg.set("glow-seconds-before-end", glowSecondsBeforeEnd);
        cfg.set("glow-bonus-gold", glowBonusGold);
        cfg.set("start-delay-seconds", startDelaySeconds);
        cfg.set("start-delay-full-seconds", startDelayFullSeconds);
        cfg.set("fork-uses", forkUses);
        cfg.set("start-gold", startGold);
        cfg.set("wear-min-percent", wearMinPercent);
        cfg.set("wear-max-percent", wearMaxPercent);
        cfg.set("dynamic-chests", dynamicChests);
        cfg.set("lobby", lobby);
        cfg.set("contracts", contractIds);
        cfg.set("dead-messages", deadMessages);

        YamlConfiguration locs = new YamlConfiguration();
        locs.set("spawns", spawns);
        locs.set("final-spawns", finalSpawns);
        locs.set("chests", writeNamedLocations(chestSpots, "category"));
        locs.set("tables", tableSpots);
        locs.set("ores", oreSpots);
        locs.set("levers", writeNamedLocations(levers, "name"));
        locs.set("traders", writeNamedLocations(traderSpots, "type"));

        YamlConfiguration lootCfg = new YamlConfiguration();
        List<Map<String, Object>> items = new ArrayList<>();
        for (WeightedItem entry : loot)
        {
            items.add(Map.of("item", entry.item().serialize(), "weight", entry.weight()));
        }
        lootCfg.set("items", items);

        YamlConfiguration kitsCfg = new YamlConfiguration();
        kitsCfg.set("default-kit", defaultKit);
        for (Kit kit : kits)
        {
            kit.save(kitsCfg.createSection("kits." + kit.getId()));
        }

        YamlConfiguration catCfg = new YamlConfiguration();
        for (ChestCategory cat : chestCategories)
        {
            cat.save(catCfg.createSection("categories." + cat.getId()));
        }

        try
        {
            cfg.save(new File(folder, "arena.yml"));
            locs.save(new File(folder, "locations.yml"));
            lootCfg.save(new File(folder, "loot.yml"));
            kitsCfg.save(new File(folder, "kits.yml"));
            catCfg.save(new File(folder, "chest-categories.yml"));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to save arena " + id, e);
        }
    }

    private static List<Location> readLocations(ConfigurationSection sec, String path)
    {
        List<Location> out = new ArrayList<>();
        for (Object o : sec.getList(path, List.of()))
        {
            if (o instanceof Location l) {out.add(l);}
        }
        return out;
    }

    private static void readNamedLocations(ConfigurationSection sec, String path, String valueKey, Map<Location, String> target)
    {
        for (Map<?, ?> entry : sec.getMapList(path))
        {
            Object loc = entry.get("location");
            Object value = entry.get(valueKey);
            if (loc instanceof Location l && value != null) {target.put(l, String.valueOf(value));}
        }
    }

    /**
     * Точки сундуков: НОВЫЙ формат — список {location, category}, СТАРЫЙ — плоский
     * список Location (тогда категория = default). Так старые арены не теряют точки
     * при первой загрузке новой версии; пере-сейв апгрейдит файл до нового формата.
     */
    private static void readChestSpots(ConfigurationSection sec, Map<Location, String> target)
    {
        for (Object o : sec.getList("chests", List.of()))
        {
            if (o instanceof Location l)
            {
                target.put(l, ChestCategory.DEFAULT_ID);
            }
            else if (o instanceof Map<?, ?> m && m.get("location") instanceof Location l)
            {
                Object cat = m.get("category");
                target.put(l, cat != null ? String.valueOf(cat) : ChestCategory.DEFAULT_ID);
            }
        }
    }

    private List<Map<String, Object>> writeNamedLocations(Map<Location, String> source, String valueKey)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Location, String> entry : source.entrySet())
        {
            out.add(Map.of("location", entry.getKey(), valueKey, entry.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack readItem(Object raw)
    {
        if (raw instanceof ItemStack is) {return is;}
        if (raw instanceof Map<?, ?> map) {return ItemStack.deserialize((Map<String, Object>) map);}
        return null;
    }

    // ===== accessors =====

    public String getId() {return id;}
    public String getDisplayNameRaw() {return displayNameRaw;}
    public void setDisplayNameRaw(String v) {this.displayNameRaw = v;}
    public String getDescriptionRaw() {return descriptionRaw;}
    public void setDescriptionRaw(String v) {this.descriptionRaw = v;}
    public String getWorldName() {return worldName;}
    public void setWorldName(String v) {this.worldName = v;}
    public World getWorld() {return worldName == null ? null : Bukkit.getWorld(worldName);}
    public boolean isEnabled() {return enabled;}
    public void setEnabled(boolean v) {this.enabled = v;}
    public int getMinPlayers() {return minPlayers;}
    public void setMinPlayers(int v) {this.minPlayers = v;}
    public int getMaxPlayers() {return maxPlayers;}
    public void setMaxPlayers(int v) {this.maxPlayers = v;}
    public int getChestCount() {return chestCount;}
    public void setChestCount(int v) {this.chestCount = v;}
    public int getTraderCount() {return traderCount;}
    public void setTraderCount(int v) {this.traderCount = v;}
    public int getTableCount() {return tableCount;}
    public void setTableCount(int v) {this.tableCount = v;}
    public int getDurationSeconds() {return durationSeconds;}
    public void setDurationSeconds(int v) {this.durationSeconds = v;}
    public int getEventIntervalSeconds() {return eventIntervalSeconds;}
    public void setEventIntervalSeconds(int v) {this.eventIntervalSeconds = v;}
    public int getSalaryIntervalSeconds() {return salaryIntervalSeconds;}
    public void setSalaryIntervalSeconds(int v) {this.salaryIntervalSeconds = v;}
    public int getSalaryGold() {return salaryGold;}
    public void setSalaryGold(int v) {this.salaryGold = v;}
    public int getGlowSecondsBeforeEnd() {return glowSecondsBeforeEnd;}
    public void setGlowSecondsBeforeEnd(int v) {this.glowSecondsBeforeEnd = v;}
    public int getGlowBonusGold() {return glowBonusGold;}
    public void setGlowBonusGold(int v) {this.glowBonusGold = v;}
    public int getStartDelaySeconds() {return startDelaySeconds;}
    public void setStartDelaySeconds(int v) {this.startDelaySeconds = v;}
    public int getStartDelayFullSeconds() {return startDelayFullSeconds;}
    public void setStartDelayFullSeconds(int v) {this.startDelayFullSeconds = v;}
    public int getForkUses() {return forkUses;}
    public void setForkUses(int v) {this.forkUses = v;}
    public int getStartGold() {return startGold;}
    public void setStartGold(int v) {this.startGold = v;}
    public int getWearMinPercent() {return wearMinPercent;}
    public void setWearMinPercent(int v) {this.wearMinPercent = v;}
    public int getWearMaxPercent() {return wearMaxPercent;}
    public void setWearMaxPercent(int v) {this.wearMaxPercent = v;}
    public boolean isDynamicChests() {return dynamicChests;}
    public void setDynamicChests(boolean v) {this.dynamicChests = v;}
    public Location getLobby() {return lobby;}
    public void setLobby(Location v) {this.lobby = v;}
    public List<String> getContractIds() {return contractIds;}
    public List<String> getDeadMessages() {return deadMessages;}
    public List<Location> getSpawns() {return spawns;}
    public List<Location> getFinalSpawns() {return finalSpawns;}
    public Map<Location, String> getChestSpots() {return chestSpots;}
    public List<Location> getTableSpots() {return tableSpots;}
    public List<Location> getOreSpots() {return oreSpots;}
    public Map<Location, String> getLevers() {return levers;}
    public Map<Location, String> getTraderSpots() {return traderSpots;}
    public List<WeightedItem> getLoot() {return loot;}
    public List<Kit> getKits() {return kits;}
    public String getDefaultKit() {return defaultKit;}
    public void setDefaultKit(String v) {this.defaultKit = v;}

    /** Каст арены по id (без учёта регистра) или null. */
    public Kit getKit(String id)
    {
        if (id == null) {return null;}
        for (Kit kit : kits)
        {
            if (kit.getId().equalsIgnoreCase(id)) {return kit;}
        }
        return null;
    }

    public void addKit(Kit kit) {kits.add(kit);}

    public boolean removeKit(String id)
    {
        return kits.removeIf(kit -> kit.getId().equalsIgnoreCase(id));
    }

    public List<ChestCategory> getChestCategories() {return chestCategories;}

    /** Категория сундука арены по id (без учёта регистра) или null. */
    public ChestCategory getChestCategory(String id)
    {
        if (id == null) {return null;}
        for (ChestCategory cat : chestCategories)
        {
            if (cat.getId().equalsIgnoreCase(id)) {return cat;}
        }
        return null;
    }

    public void addChestCategory(ChestCategory cat) {chestCategories.add(cat);}

    public boolean removeChestCategory(String id)
    {
        return chestCategories.removeIf(cat -> cat.getId().equalsIgnoreCase(id));
    }

    public GameSession getSession() {return session;}
    public void setSession(GameSession session) {this.session = session;}
}
