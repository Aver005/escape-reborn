package me.aver005.escape;
import me.aver005.escape.util.EscapeKeys;

import java.sql.SQLException;

import me.aver005.escape.arena.ArenaManager;
import me.aver005.escape.arena.EscapeArenaConfig;
import me.aver005.escape.arena.EscapeArenaConfigs;
import me.aver005.escape.kit.Kit;
import ru.kiviuly.mg.api.arena.Arena;
import java.util.ArrayList;
import java.util.List;
import me.aver005.escape.arena.ChestSetupManager;
import me.aver005.escape.command.EscapeCommand;
import me.aver005.escape.contract.ContractRegistry;
import me.aver005.escape.kit.KitRegistry;
import me.aver005.escape.loot.LootCategoryRegistry;
import me.aver005.escape.loot.LootMigration;
import me.aver005.escape.modifier.ModifierRegistry;
import me.aver005.escape.theme.ThemeRegistry;
import me.aver005.escape.listener.ChatListener;
import me.aver005.escape.listener.GameListener;
import me.aver005.escape.listener.MechanicsListener;
import me.aver005.escape.listener.ProtectionListener;
import me.aver005.escape.listener.SetupListener;
import me.aver005.escape.stats.StatsRepository;
import me.aver005.escape.trader.TraderRegistry;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
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
    private LootCategoryRegistry lootRegistry;
    private ChestSetupManager chestSetup;
    private StatsRepository statsRepository;
    private EscapeArenaConfigs arenaConfigs;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        // Общий тулкит (Keys/Msg/DebugLog) инициализирует ядро MgCore — здесь только
        // свои игровые ключи и подмешивание собственного messages.yml в общий каталог.
        EscapeKeys.init(this);
        Msg.merge(this);
        DebugLog.init(this); // свой диагностический лог (игровые категории Cat)

        arenaConfigs = new EscapeArenaConfigs(this);
        arenaManager = new ArenaManager(this);
        contractRegistry = new ContractRegistry(this);
        themeRegistry = new ThemeRegistry(this);
        traderRegistry = new TraderRegistry(this);
        kitRegistry = new KitRegistry(this);
        modifierRegistry = new ModifierRegistry(this);
        lootRegistry = new LootCategoryRegistry(this);
        chestSetup = new ChestSetupManager(this);
        statsRepository = new StatsRepository(this);

        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        arenaConfigs.clear();
        kitRegistry.load();
        modifierRegistry.load();
        lootRegistry.load();
        arenaManager.loadAll();
        contractRegistry.load();
        themeRegistry.load();
        traderRegistry.load();
        // одноразовая миграция старых пулов/пер-ареновых категорий в глобальные loot/*.yml
        LootMigration.run(this);

        var pm = getServer().getPluginManager();
        // MenuListener не регистрируем: меню наследуют общий mg-api Menu, клики
        // маршрутизирует MenuListener ядра (иначе обработка была бы двойной).
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
        arenaConfigs.clear();
        kitRegistry.load();
        modifierRegistry.load();
        lootRegistry.load();
        arenaManager.loadAll();
        contractRegistry.load();
        themeRegistry.load();
        traderRegistry.load();
    }

    public EscapeArenaConfigs arenaConfigs() {return arenaConfigs;}

    /** Киты, разрешённые на арене: глобальный реестр, отфильтрованный списком арены. */
    public List<Kit> kitsFor(Arena arena)
    {
        EscapeArenaConfig cfg = arenaConfigs.of(arena);
        List<Kit> out = new ArrayList<>();
        for (String id : kitRegistry.ids())
        {
            if (cfg.allowsKit(id)) {out.add(kitRegistry.get(id));}
        }
        return out;
    }

    /** Кит по id, если он разрешён на этой арене (иначе null). */
    public Kit kitFor(Arena arena, String id)
    {
        if (id == null) {return null;}
        return arenaConfigs.of(arena).allowsKit(id) ? kitRegistry.get(id) : null;
    }

    public ArenaManager arenas() {return arenaManager;}
    public ContractRegistry contracts() {return contractRegistry;}
    public ThemeRegistry themes() {return themeRegistry;}
    public TraderRegistry traders() {return traderRegistry;}
    public KitRegistry kits() {return kitRegistry;}
    public ModifierRegistry modifiers() {return modifierRegistry;}
    public LootCategoryRegistry loot() {return lootRegistry;}
    public ChestSetupManager chestSetup() {return chestSetup;}
    public StatsRepository stats() {return statsRepository;}
}
