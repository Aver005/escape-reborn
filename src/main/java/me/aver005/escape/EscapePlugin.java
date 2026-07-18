package me.aver005.escape;

import java.sql.SQLException;

import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ArenaManager;
import me.aver005.escape.command.EscapeCommand;
import me.aver005.escape.contract.ContractRegistry;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.listener.ChatListener;
import me.aver005.escape.listener.GameListener;
import me.aver005.escape.listener.MenuListener;
import me.aver005.escape.listener.ProtectionListener;
import me.aver005.escape.listener.SetupListener;
import me.aver005.escape.stats.StatsRepository;
import me.aver005.escape.trader.TraderRegistry;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Escape — тюремный last man standing. Возрождение оригинала 2020/2021. */
public final class EscapePlugin extends JavaPlugin
{
    private ArenaManager arenaManager;
    private ContractRegistry contractRegistry;
    private TraderRegistry traderRegistry;
    private StatsRepository statsRepository;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);

        arenaManager = new ArenaManager(this);
        contractRegistry = new ContractRegistry(this);
        traderRegistry = new TraderRegistry(this);
        statsRepository = new StatsRepository(this);

        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        arenaManager.loadAll();
        contractRegistry.load();
        traderRegistry.load();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new SetupListener(this), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);

        EscapeCommand command = new EscapeCommand(this);
        var escape = getCommand("escape");
        escape.setExecutor(command);
        escape.setTabCompleter(command);

        getLogger().info("Escape enabled, arenas loaded: " + arenaManager.all().size());
    }

    @Override
    public void onDisable()
    {
        if (arenaManager != null) {arenaManager.stopAll();}
        saveEverything();
        if (statsRepository != null) {statsRepository.close();}
    }

    public void saveEverything()
    {
        arenaManager.saveAll();
        contractRegistry.save();
        traderRegistry.save();
    }

    public void reloadEverything()
    {
        reloadConfig();
        Msg.reload();
        arenaManager.loadAll();
        contractRegistry.load();
        traderRegistry.load();
    }

    /** Вход игрока на арену (создаёт сессию при необходимости). */
    public boolean joinArena(Player p, Arena arena)
    {
        GameSession session = arena.getSession();
        boolean fresh = false;
        if (session == null)
        {
            session = new GameSession(this, arena);
            arena.setSession(session);
            fresh = true;
        }
        boolean joined = session.join(p);
        if (!joined && fresh && session.lobbySize() == 0) {arena.setSession(null);}
        return joined;
    }

    public ArenaManager arenas() {return arenaManager;}
    public ContractRegistry contracts() {return contractRegistry;}
    public TraderRegistry traders() {return traderRegistry;}
    public StatsRepository stats() {return statsRepository;}
}
