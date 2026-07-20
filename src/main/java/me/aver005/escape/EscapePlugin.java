package me.aver005.escape;

import java.sql.SQLException;

import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ArenaManager;
import me.aver005.escape.arena.ChestSetupManager;
import me.aver005.escape.category.CategoryRegistry;
import me.aver005.escape.command.EscapeCommand;
import me.aver005.escape.contract.ContractRegistry;
import me.aver005.escape.kit.KitRegistry;
import me.aver005.escape.modifier.ModifierRegistry;
import me.aver005.escape.theme.ThemeRegistry;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.listener.ChatListener;
import me.aver005.escape.listener.GameListener;
import me.aver005.escape.listener.MechanicsListener;
import me.aver005.escape.listener.MenuListener;
import me.aver005.escape.listener.ProtectionListener;
import me.aver005.escape.listener.SetupListener;
import me.aver005.escape.stats.StatsRepository;
import me.aver005.escape.trader.TraderRegistry;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Escape — тюремный last man standing. Возрождение оригинала 2020/2021. */
public final class EscapePlugin extends JavaPlugin
{
    private ArenaManager arenaManager;
    private ContractRegistry contractRegistry;
    private ThemeRegistry themeRegistry;
    private TraderRegistry traderRegistry;
    private KitRegistry kitRegistry;
    private ModifierRegistry modifierRegistry;
    private CategoryRegistry categoryRegistry;
    private ChestSetupManager chestSetup;
    private StatsRepository statsRepository;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);
        DebugLog.init(this);

        arenaManager = new ArenaManager(this);
        contractRegistry = new ContractRegistry(this);
        themeRegistry = new ThemeRegistry(this);
        traderRegistry = new TraderRegistry(this);
        kitRegistry = new KitRegistry(this);
        modifierRegistry = new ModifierRegistry(this);
        categoryRegistry = new CategoryRegistry(this);
        chestSetup = new ChestSetupManager(this);
        statsRepository = new StatsRepository(this);

        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        kitRegistry.load();
        modifierRegistry.load();
        categoryRegistry.load();
        arenaManager.loadAll();
        contractRegistry.load();
        themeRegistry.load();
        traderRegistry.load();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new SetupListener(this), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(new MechanicsListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);

        EscapeCommand command = new EscapeCommand(this);
        var escape = getCommand("escape");
        escape.setExecutor(command);
        escape.setTabCompleter(command);

        getLogger().info("Escape enabled, arenas loaded: " + arenaManager.all().size());
        if (DebugLog.on()) {getLogger().info("Escape debug log is ON (/escape debuglog off to stop)");}
        DebugLog.log(Cat.ADMIN, "plugin enable arenas=%d contracts=%d themes=%d traders=%d",
            arenaManager.all().size(), contractRegistry.ids().size(),
            themeRegistry.ids().size(), traderRegistry.ids().size());
    }

    @Override
    public void onDisable()
    {
        DebugLog.log(Cat.ADMIN, "plugin disable");
        if (chestSetup != null) {chestSetup.stopAll();}
        if (arenaManager != null) {arenaManager.stopAll();}
        saveEverything();
        if (statsRepository != null) {statsRepository.close();}
    }

    public void saveEverything()
    {
        arenaManager.saveAll();
        contractRegistry.save();
        themeRegistry.save();
        traderRegistry.save();
    }

    public void reloadEverything()
    {
        if (chestSetup != null) {chestSetup.stopAll();}
        reloadConfig();
        Msg.reload();
        DebugLog.reload();
        kitRegistry.load();
        modifierRegistry.load();
        categoryRegistry.load();
        arenaManager.loadAll();
        contractRegistry.load();
        themeRegistry.load();
        traderRegistry.load();
    }

    /** Вход игрока на арену (создаёт сессию при необходимости). */
    public boolean joinArena(Player p, Arena arena)
    {
        // арена под настройкой мастера (или сам админ в мастере) в матч не идёт
        if (chestSetup.isActive(p) || chestSetup.isArenaBusy(arena))
        {
            Msg.send(p, "chestsetup.busy-join");
            return false;
        }
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
    public ThemeRegistry themes() {return themeRegistry;}
    public TraderRegistry traders() {return traderRegistry;}
    public KitRegistry kits() {return kitRegistry;}
    public ModifierRegistry modifiers() {return modifierRegistry;}
    public CategoryRegistry categories() {return categoryRegistry;}
    public ChestSetupManager chestSetup() {return chestSetup;}
    public StatsRepository stats() {return statsRepository;}
}
