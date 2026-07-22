package me.aver005.escape.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aver005.escape.game.GameSession;
import me.aver005.escape.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Арена: настройки + сконфигурированные точки.
 * Хранится в папке arenas/<id>/: arena.yml, locations.yml, kits.yml.
 * Лут больше НЕ хранится на арене — он в глобальных категориях (loot/*.yml);
 * точка сундука несёт список id категорий (0..N).
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
    // контракты кладутся в сундуки независимо от лута (в бюджет категорий не идут)
    private int contractsMinPerArena = 4;
    private int contractsMaxPerArena = 16;   // -1 = без потолка (заполнить до chests*max-per-chest)
    private int contractsMinPerChest = 0;    // не используется в раздаче (оставлен для совместимости)
    private int contractsMaxPerChest = 3;    // потолок контрактов в одном сундуке
    private Location lobby;
    private List<String> contractIds = new ArrayList<>();
    private List<String> deadMessages = new ArrayList<>();

    // locations.yml
    private List<Location> spawns = new ArrayList<>();
    private List<Location> finalSpawns = new ArrayList<>();
    private final Map<Location, List<String>> chestSpots = new LinkedHashMap<>();   // точка -> id категорий (0..N)
    private List<Location> tableSpots = new ArrayList<>();
    private List<Location> oreSpots = new ArrayList<>();
    private final Map<Location, String> levers = new LinkedHashMap<>();       // точка -> имя локации
    private final Map<Location, String> traderSpots = new LinkedHashMap<>();  // точка -> тип торговца
    private List<Location> breakables = new ArrayList<>();                    // отмеченные ломаемые блоки (вернутся после матча)

    // kits.yml — стартовые наборы («касты»), копии глобальных шаблонов
    private final List<Kit> kits = new ArrayList<>();
    private String defaultKit = "random";  // выбор по умолчанию: random | none | <id каста>

    // arena.yml — лимит числа жителей ПО ТИПУ (typeId -> макс. за матч); нет записи = все точки типа
    private final Map<String, Integer> traderQuotas = new LinkedHashMap<>();

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
        arena.contractsMinPerArena = Math.max(0, cfg.getInt("contract-min-per-arena", 4));
        int cMaxArena = cfg.getInt("contract-max-per-arena", 16);   // -1 = без потолка (заполнить до предела)
        arena.contractsMaxPerArena = cMaxArena < 0 ? -1 : Math.max(arena.contractsMinPerArena, cMaxArena);
        arena.contractsMinPerChest = Math.max(0, cfg.getInt("contract-min-per-chest", 0));
        arena.contractsMaxPerChest = Math.max(1, cfg.getInt("contract-max-per-chest", 3));
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
        arena.breakables = readLocations(locs, "breakables");

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

        ConfigurationSection tqRoot = cfg.getConfigurationSection("trader-quotas");
        if (tqRoot != null)
        {
            for (String key : tqRoot.getKeys(false))
            {
                arena.traderQuotas.put(key.toUpperCase(Locale.ROOT), Math.max(0, tqRoot.getInt(key)));
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
        cfg.set("contract-min-per-arena", contractsMinPerArena);
        cfg.set("contract-max-per-arena", contractsMaxPerArena);
        cfg.set("contract-min-per-chest", contractsMinPerChest);
        cfg.set("contract-max-per-chest", contractsMaxPerChest);
        cfg.set("lobby", lobby);
        cfg.set("contracts", contractIds);
        cfg.set("dead-messages", deadMessages);
        if (!traderQuotas.isEmpty())
        {
            ConfigurationSection tq = cfg.createSection("trader-quotas");
            for (Map.Entry<String, Integer> e : traderQuotas.entrySet()) {tq.set(e.getKey(), e.getValue());}
        }

        YamlConfiguration locs = new YamlConfiguration();
        locs.set("spawns", spawns);
        locs.set("final-spawns", finalSpawns);
        locs.set("chests", writeChestSpots(chestSpots));
        locs.set("tables", tableSpots);
        locs.set("ores", oreSpots);
        locs.set("levers", writeNamedLocations(levers, "name"));
        locs.set("traders", writeNamedLocations(traderSpots, "type"));
        locs.set("breakables", breakables);

        YamlConfiguration kitsCfg = new YamlConfiguration();
        kitsCfg.set("default-kit", defaultKit);
        for (Kit kit : kits)
        {
            kit.save(kitsCfg.createSection("kits." + kit.getId()));
        }

        try
        {
            cfg.save(new File(folder, "arena.yml"));
            locs.save(new File(folder, "locations.yml"));
            kitsCfg.save(new File(folder, "kits.yml"));
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
     * Точки сундуков → список id категорий (0..N). Форматы (в порядке новизны):
     *   НОВЫЙ    {location, categories: [id, ...]} — мультикатегорийная точка;
     *   СТАРЫЙ-1 {location, category: id}         — одиночная категория (список из 1);
     *   СТАРЫЙ-0 плоский Location                 — без категорий (пустой список).
     * Пере-сейв апгрейдит файл до нового формата.
     */
    @SuppressWarnings("unchecked")
    private static void readChestSpots(ConfigurationSection sec, Map<Location, List<String>> target)
    {
        for (Object o : sec.getList("chests", List.of()))
        {
            if (o instanceof Location l)
            {
                target.put(l, new ArrayList<>());
            }
            else if (o instanceof Map<?, ?> m && m.get("location") instanceof Location l)
            {
                List<String> cats = new ArrayList<>();
                if (m.get("categories") instanceof List<?> list)
                {
                    for (Object c : list) {if (c != null) {cats.add(String.valueOf(c));}}
                }
                else if (m.get("category") != null)
                {
                    cats.add(String.valueOf(m.get("category")));
                }
                target.put(l, cats);
            }
        }
    }

    private List<Map<String, Object>> writeChestSpots(Map<Location, List<String>> source)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Location, List<String>> entry : source.entrySet())
        {
            out.add(Map.of("location", entry.getKey(), "categories", new ArrayList<>(entry.getValue())));
        }
        return out;
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
    public int getContractsMinPerArena() {return contractsMinPerArena;}
    public void setContractsMinPerArena(int v) {this.contractsMinPerArena = Math.max(0, v);}
    public int getContractsMaxPerArena() {return contractsMaxPerArena;}
    public void setContractsMaxPerArena(int v) {this.contractsMaxPerArena = v < 0 ? -1 : v;}
    public int getContractsMinPerChest() {return contractsMinPerChest;}
    public void setContractsMinPerChest(int v) {this.contractsMinPerChest = Math.max(0, v);}
    public int getContractsMaxPerChest() {return contractsMaxPerChest;}
    public void setContractsMaxPerChest(int v) {this.contractsMaxPerChest = Math.max(0, v);}
    public Location getLobby() {return lobby;}
    public void setLobby(Location v) {this.lobby = v;}
    public List<String> getContractIds() {return contractIds;}
    public List<String> getDeadMessages() {return deadMessages;}
    public List<Location> getSpawns() {return spawns;}
    public List<Location> getFinalSpawns() {return finalSpawns;}
    public Map<Location, List<String>> getChestSpots() {return chestSpots;}
    public List<Location> getTableSpots() {return tableSpots;}
    public List<Location> getOreSpots() {return oreSpots;}
    public Map<Location, String> getLevers() {return levers;}
    public Map<Location, String> getTraderSpots() {return traderSpots;}
    public Map<String, Integer> getTraderQuotas() {return traderQuotas;}
    public List<Location> getBreakables() {return breakables;}
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

    public GameSession getSession() {return session;}
    public void setSession(GameSession session) {this.session = session;}
}
