package me.aver005.escape.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.game.GameSession;
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
    private Location lobby;
    private List<String> contractIds = new ArrayList<>();
    private List<String> deadMessages = new ArrayList<>();

    // locations.yml
    private List<Location> spawns = new ArrayList<>();
    private List<Location> finalSpawns = new ArrayList<>();
    private List<Location> chestSpots = new ArrayList<>();
    private List<Location> tableSpots = new ArrayList<>();
    private List<Location> oreSpots = new ArrayList<>();
    private final Map<Location, String> levers = new LinkedHashMap<>();       // точка -> имя локации
    private final Map<Location, String> traderSpots = new LinkedHashMap<>();  // точка -> тип торговца

    // loot.yml
    private final List<WeightedItem> loot = new ArrayList<>();

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
        arena.lobby = cfg.getLocation("lobby");
        arena.contractIds = new ArrayList<>(cfg.getStringList("contracts"));
        arena.deadMessages = new ArrayList<>(cfg.getStringList("dead-messages"));

        YamlConfiguration locs = YamlConfiguration.loadConfiguration(new File(folder, "locations.yml"));
        arena.spawns = readLocations(locs, "spawns");
        arena.finalSpawns = readLocations(locs, "final-spawns");
        arena.chestSpots = readLocations(locs, "chests");
        arena.tableSpots = readLocations(locs, "tables");
        arena.oreSpots = readLocations(locs, "ores");
        readNamedLocations(locs, "levers", "name", arena.levers);
        readNamedLocations(locs, "traders", "type", arena.traderSpots);

        YamlConfiguration lootCfg = YamlConfiguration.loadConfiguration(new File(folder, "loot.yml"));
        for (Map<?, ?> entry : lootCfg.getMapList("items"))
        {
            // простой рукописный формат: {type: STONE_SWORD, weight: 20, amount: 1}
            ItemStack item;
            if (entry.get("type") instanceof String typeName)
            {
                Material mat = Material.matchMaterial(typeName);
                if (mat == null || mat.isAir()) {continue;}
                int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
                item = new ItemStack(mat, amount);
            }
            else
            {
                item = readItem(entry.get("item"));
            }
            Object weight = entry.get("weight");
            if (item == null) {continue;}
            arena.loot.add(new WeightedItem(item, weight instanceof Number n ? Math.max(1, n.intValue()) : 1));
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
        cfg.set("lobby", lobby);
        cfg.set("contracts", contractIds);
        cfg.set("dead-messages", deadMessages);

        YamlConfiguration locs = new YamlConfiguration();
        locs.set("spawns", spawns);
        locs.set("final-spawns", finalSpawns);
        locs.set("chests", chestSpots);
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

        try
        {
            cfg.save(new File(folder, "arena.yml"));
            locs.save(new File(folder, "locations.yml"));
            lootCfg.save(new File(folder, "loot.yml"));
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
    public Location getLobby() {return lobby;}
    public void setLobby(Location v) {this.lobby = v;}
    public List<String> getContractIds() {return contractIds;}
    public List<String> getDeadMessages() {return deadMessages;}
    public List<Location> getSpawns() {return spawns;}
    public List<Location> getFinalSpawns() {return finalSpawns;}
    public List<Location> getChestSpots() {return chestSpots;}
    public List<Location> getTableSpots() {return tableSpots;}
    public List<Location> getOreSpots() {return oreSpots;}
    public Map<Location, String> getLevers() {return levers;}
    public Map<Location, String> getTraderSpots() {return traderSpots;}
    public List<WeightedItem> getLoot() {return loot;}

    public GameSession getSession() {return session;}
    public void setSession(GameSession session) {this.session = session;}
}
