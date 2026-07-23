package me.aver005.escape.arena;

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
import me.aver005.escape.game.GameSession;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.entity.Player;

/** Реестр арен (arenas/<id>/) и привязка игрок -> активная сессия. */
public class ArenaManager
{
    private final EscapePlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, GameSession> sessionByPlayer = new ConcurrentHashMap<>();

    public ArenaManager(EscapePlugin plugin) {this.plugin = plugin;}

    private File arenasFolder()
    {
        return new File(plugin.getDataFolder(), "arenas");
    }

    public void loadAll()
    {
        arenas.clear();
        File root = arenasFolder();
        if (!root.exists()) {root.mkdirs(); return;}
        File[] folders = root.listFiles(File::isDirectory);
        if (folders == null) {return;}
        for (File folder : folders)
        {
            try
            {
                Arena arena = Arena.load(folder);
                arenas.put(arena.getId(), arena);
                plugin.getLogger().info("Arena loaded: " + arena.getId());
            }
            catch (Exception e)
            {
                plugin.getLogger().severe("Failed to load arena " + folder.getName() + ": " + e.getMessage());
            }
        }
    }

    public void saveAll()
    {
        for (Arena arena : arenas.values()) {save(arena);}
    }

    public void save(Arena arena)
    {
        arena.save(new File(arenasFolder(), arena.getId()));
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
        arena.setTraderCount(cfg.getInt("trader-count", 32));
        arena.setTableCount(cfg.getInt("table-count", 5));
        arena.setDurationSeconds(cfg.getInt("duration-seconds", 1200));
        arena.setEventIntervalSeconds(cfg.getInt("event-interval-seconds", 210));
        arena.setSalaryIntervalSeconds(cfg.getInt("salary-interval-seconds", 600));
        arena.setSalaryGold(cfg.getInt("salary-gold", 16));
        arena.setGlowSecondsBeforeEnd(cfg.getInt("glow-seconds-before-end", 600));
        arena.setGlowBonusGold(cfg.getInt("glow-bonus-gold", 18));
        arena.setStartDelaySeconds(cfg.getInt("start-delay-seconds", 60));
        arena.setStartDelayFullSeconds(cfg.getInt("start-delay-full-seconds", 10));
        arena.setForkUses(cfg.getInt("fork-uses", 1));
        arena.setStartGold(cfg.getInt("start-gold", 24));
    }

    public void delete(String id)
    {
        Arena arena = arenas.remove(id);
        if (arena == null) {return;}
        if (arena.getSession() != null) {arena.getSession().forceStop();}
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

    public GameSession sessionOf(Player p) {return sessionByPlayer.get(p.getUniqueId());}
    public boolean inSession(Player p) {return sessionByPlayer.containsKey(p.getUniqueId());}
    public void bind(UUID player, GameSession session) {sessionByPlayer.put(player, session);}
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
        GameSession session = arena.getSession();
        boolean fresh = false;
        if (session == null)
        {
            session = new GameSession(plugin, arena);
            arena.setSession(session);
            fresh = true;
        }
        boolean joined = session.join(p);
        if (!joined && fresh && session.lobbySize() == 0) {arena.setSession(null);}
        return joined;
    }

    public void stopAll()
    {
        for (Arena arena : arenas.values())
        {
            if (arena.getSession() != null) {arena.getSession().forceStop();}
        }
    }
}
