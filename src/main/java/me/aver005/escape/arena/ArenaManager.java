package me.aver005.escape.arena;

import ru.kiviuly.mg.api.arena.Arena;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.EscapeRules;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.entity.Player;

/** Реестр арен (arenas/<id>/) и привязка игрок -> активная сессия. */
public class ArenaManager
{
    private final EscapePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, EscapeRules> sessionByPlayer = new ConcurrentHashMap<>();
    /** Живой матч по id арены. Держим здесь, а не в самой арене: конфиг арены — платформенный. */
    private final Map<String, EscapeRules> sessionByArena = new ConcurrentHashMap<>();

    /** Текущий матч на арене (или null). */
    public EscapeRules sessionOf(Arena arena) {return arena == null ? null : sessionByArena.get(arena.getId());}

    /** Привязать/снять матч арены (null снимает). */
    public void setSession(Arena arena, EscapeRules session)
    {
        if (arena == null) {return;}
        if (session == null) {sessionByArena.remove(arena.getId());}
        else {sessionByArena.put(arena.getId(), session);}
    }

    public ArenaManager(EscapePlugin plugin) {this.plugin = plugin;}

    private File arenasFolder()
    {
        return new File(plugin.getDataFolder(), "arenas");
    }

    private File fileOf(String arenaId) {return new File(arenasFolder(), arenaId + ".yml");}

    /**
     * Арены в платформенном формате: один файл {@code arenas/<ID>.yml} (общее + «карманы»
     * чисел и точек). Игро-специфичный контент лежит рядом в {@code game/<ID>.yml} и
     * подтягивается лениво через {@link EscapeArenaConfigs}.
     */
    public void loadAll()
    {
        arenas.clear();
        File root = arenasFolder();
        if (!root.exists()) {root.mkdirs(); return;}
        File[] files = root.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {return;}
        for (File f : files)
        {
            String id = f.getName().substring(0, f.getName().length() - 4).toUpperCase();
            try
            {
                Arena arena = Arena.load(id, f);
                arenas.put(arena.getId(), arena);
                plugin.getLogger().info("Arena loaded: " + arena.getId());
            }
            catch (Exception e)
            {
                plugin.getLogger().severe("Failed to load arena " + id + ": " + e.getMessage());
            }
        }
    }

    public void saveAll()
    {
        for (Arena arena : arenas.values()) {save(arena);}
    }

    /** Сохраняет и платформенную часть арены, и её игро-специфичный конфиг. */
    public void save(Arena arena)
    {
        arena.save(fileOf(arena.getId()));
        plugin.arenaConfigs().save(arena);
    }

    public Arena create(String id, String worldName)
    {
        Arena arena = new Arena(id);
        arena.setWorldName(worldName);
        applyDefaults(arena);
        arenas.put(id, arena);
        save(arena);
        return arena;
    }

    private void applyDefaults(Arena arena)
    {
        var cfg = plugin.getConfig().getConfigurationSection("defaults");
        if (cfg == null) {return;}
        arena.setMinPlayers(cfg.getInt("min-players", 2));
        arena.setMaxPlayers(cfg.getInt("max-players", 12));
        EscapeArena.setTraderCount(arena, cfg.getInt("trader-count", 32));
        EscapeArena.setTableCount(arena, cfg.getInt("table-count", 5));
        arena.setMatchDurationSeconds(cfg.getInt("duration-seconds", 1200));
        EscapeArena.setEventIntervalSeconds(arena, cfg.getInt("event-interval-seconds", 210));
        EscapeArena.setSalaryIntervalSeconds(arena, cfg.getInt("salary-interval-seconds", 600));
        EscapeArena.setSalaryGold(arena, cfg.getInt("salary-gold", 16));
        EscapeArena.setGlowSecondsBeforeEnd(arena, cfg.getInt("glow-seconds-before-end", 600));
        EscapeArena.setGlowBonusGold(arena, cfg.getInt("glow-bonus-gold", 18));
        arena.setLobbyCountdownSeconds(cfg.getInt("start-delay-seconds", 60));
        arena.setCountdownFullSeconds(cfg.getInt("start-delay-full-seconds", 10));
        EscapeArena.setForkUses(arena, cfg.getInt("fork-uses", 1));
        EscapeArena.setStartGold(arena, cfg.getInt("start-gold", 24));
    }

    public void delete(String id)
    {
        Arena arena = arenas.remove(id);
        if (arena == null) {return;}
        if (sessionOf(arena) != null) {sessionOf(arena).forceStop();}
        File folder = new File(arenasFolder(), id);
        if (folder.exists())
        {
            try (var stream = Files.walk(folder.toPath()))
            {
                stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Failed to delete arena folder " + id + ": " + e.getMessage());
            }
        }
    }

    public Arena get(String id) {return id == null ? null : arenas.get(id.toUpperCase());}
    public boolean exists(String id) {return id != null && arenas.containsKey(id.toUpperCase());}
    public Map<String, Arena> all() {return arenas;}
    public Set<String> ids() {return arenas.keySet();}

    // ===== сессии =====

    public EscapeRules sessionOf(Player p) {return sessionByPlayer.get(p.getUniqueId());}
    public boolean inSession(Player p) {return sessionByPlayer.containsKey(p.getUniqueId());}
    public void bind(UUID player, EscapeRules session) {sessionByPlayer.put(player, session);}
    public void unbind(UUID player) {sessionByPlayer.remove(player);}

    /** Вход игрока на арену (создаёт сессию при необходимости). */
    public boolean join(Player p, Arena arena)
    {
        // арена под настройкой мастера (или сам админ в мастере) в матч не идёт
        if (plugin.chestSetup().isActive(p) || plugin.chestSetup().isArenaBusy(arena))
        {
            Msg.send(p, "chestsetup.busy-join");
            return false;
        }
        EscapeRules session = sessionOf(arena);
        boolean fresh = false;
        if (session == null)
        {
            session = new EscapeRules(plugin, arena);
            setSession(arena, session);
            fresh = true;
        }
        boolean joined = session.join(p);
        if (!joined && fresh && session.lobbySize() == 0) {setSession(arena, null);}
        return joined;
    }

    public void stopAll()
    {
        for (Arena arena : arenas.values())
        {
            if (sessionOf(arena) != null) {sessionOf(arena).forceStop();}
        }
    }
}
