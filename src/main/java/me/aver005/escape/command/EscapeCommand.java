package me.aver005.escape.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.ArenaCheck;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.game.GameEvent;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.kit.Kit;
import me.aver005.escape.menu.ArenaSelectMenu;
import me.aver005.escape.menu.KitEditorMenu;
import me.aver005.escape.menu.LootEditorMenu;
import me.aver005.escape.menu.ScavengerEditorMenu;
import me.aver005.escape.menu.TradeEditorMenu;
import me.aver005.escape.menu.TradeListEditorMenu;
import me.aver005.escape.menu.TraderListMenu;
import me.aver005.escape.menu.VillagerPointsMenu;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** /escape — все команды игрока и админа. */
public class EscapeCommand implements TabExecutor
{
    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "stats", "info", "help");
    private static final List<String> ADMIN_SUBS = List.of(
        "save", "reload", "list", "stop", "start", "create", "remove", "enable", "disable", "check", "debug",
        "debuglog",
        "setlobby", "setname", "setdesc", "setminplayers", "setmaxplayers", "set", "worldsetup", "markers",
        "addspawn", "addfinalspawn", "addchest", "addtable", "addore", "addlever", "addvillager", "breakable",
        "addcontract", "kit", "loot", "chestsetup",
        "createcontract", "contracttype", "contractidle", "contractdesc", "contractamount", "contractprice",
        "createtheme", "themetype", "themeidle", "themedesc", "themeamount", "themegold", "themereturn",
        "addtheme", "removetheme",
        "createvillager", "villagername", "addtrade", "trades", "villagers", "chesttag", "traderquota",
        "addscrap", "removescrap", "scrapwear", "scraplist", "scrapedit");
    private static final List<String> ARENA_SETTINGS = List.of(
        "duration", "eventinterval", "salaryinterval", "salarygold", "glowtime", "glowgold",
        "contractminarena", "contractmaxarena", "contractminchest", "contractmaxchest",
        "traders", "tables", "forkuses", "startgold", "startdelay", "startdelayfull",
        "wearmin", "wearmax", "dynamicchests");

    private final EscapePlugin plugin;

    public EscapeCommand(EscapePlugin plugin) {this.plugin = plugin;}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p)) {sender.sendMessage("Только для игроков"); return true;}

        if (args.length == 0) {new ArenaSelectMenu(plugin).open(p); return true;}
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (DebugLog.on())
        {
            DebugLog.log(Cat.ADMIN, "command %s: /%s %s", p.getName(), label, String.join(" ", args));
        }

        switch (sub)
        {
            case "help" -> {sendHelp(p); return true;}
            case "info" ->
            {
                for (Component line : Msg.getList("info")) {p.sendMessage(line);}
                return true;
            }
            case "stats" ->
            {
                String target = args.length >= 2 ? args[1] : p.getName();
                plugin.stats().findByName(target, row ->
                {
                    if (row == null) {Msg.send(p, "stats.not-found"); return;}
                    Msg.send(p, "stats.header", Msg.ph("player", row.name()));
                    Msg.send(p, "stats.line-wins", Msg.ph("n", row.wins()));
                    Msg.send(p, "stats.line-loses", Msg.ph("n", row.loses()));
                    Msg.send(p, "stats.line-kills", Msg.ph("n", row.kills()));
                    Msg.send(p, "stats.line-deaths", Msg.ph("n", row.deaths()));
                    Msg.send(p, "stats.line-games", Msg.ph("n", row.gamesPlayed()));
                    Msg.send(p, "stats.line-mvp", Msg.ph("n", row.mvpGames()));
                    Msg.send(p, "stats.line-quests", Msg.ph("n", row.questsCompleted()));
                    Msg.send(p, "stats.line-trades", Msg.ph("n", row.tradesCompleted()));
                    Msg.send(p, "stats.line-ores", Msg.ph("n", row.oresMined()));
                    Msg.send(p, "stats.line-best-kills", Msg.ph("n", row.bestGameKills()));
                    Msg.send(p, "stats.line-last-kills", Msg.ph("n", row.lastGameKills()));
                });
                return true;
            }
            case "leave" ->
            {
                GameSession session = plugin.arenas().sessionOf(p);
                if (session == null) {Msg.send(p, "errors.not-in-game"); return true;}
                if (session.leave(p)) {Msg.send(p, "lobby.left-match");}
                return true;
            }
            case "join" ->
            {
                if (args.length < 2) {new ArenaSelectMenu(plugin).open(p); return true;}
                Arena arena = plugin.arenas().get(args[1]);
                if (arena == null) {Msg.send(p, "errors.arena-not-exists"); return true;}
                plugin.arenas().join(p, arena);
                return true;
            }
            default -> {}
        }

        if (!p.hasPermission("escape.admin")) {sendHelp(p); return true;}

        switch (sub)
        {
            case "save" ->
            {
                plugin.saveEverything();
                Msg.send(p, "admin.saved");
                return true;
            }
            case "reload" ->
            {
                plugin.arenas().stopAll();
                plugin.reloadEverything();
                Msg.send(p, "admin.reloaded");
                return true;
            }
            case "list" ->
            {
                Msg.send(p, "admin.list-header");
                for (Arena arena : plugin.arenas().all().values())
                {
                    GameSession session = arena.getSession();
                    String statusKey;
                    int current = 0;
                    if (!arena.isEnabled()) {statusKey = "admin.list-status-disabled";}
                    else if (session == null) {statusKey = "admin.list-status-idle";}
                    else
                    {
                        switch (session.getPhase())
                        {
                            case RUNNING, ENDING -> {statusKey = "admin.list-status-running"; current = session.aliveCount();}
                            case COUNTDOWN -> {statusKey = "admin.list-status-countdown"; current = session.lobbySize();}
                            default -> {statusKey = "admin.list-status-idle"; current = session.lobbySize();}
                        }
                    }
                    Msg.send(p, "admin.list-entry",
                        Msg.ph("arena", arena.getId()),
                        Msg.phC("status", Msg.get(statusKey)),
                        Msg.ph("current", current),
                        Msg.ph("max", arena.getMaxPlayers()));
                }
                return true;
            }
            case "debug" ->
            {
                handleDebug(p, args);
                return true;
            }
            case "debuglog" ->
            {
                handleDebugLog(p, args);
                return true;
            }
            case "kit" ->
            {
                handleKit(p, args);
                return true;
            }
            case "loot" ->
            {
                handleLoot(p, args);
                return true;
            }
            case "chestsetup" ->
            {
                handleChestSetup(p, args);
                return true;
            }
            case "trades" ->
            {
                handleTrades(p, args);
                return true;
            }
            default -> {}
        }

        if (args.length < 2) {Msg.send(p, "errors.not-enough-args"); return true;}
        String id = args[1].toUpperCase(Locale.ROOT);

        switch (sub)
        {
            case "create" ->
            {
                if (plugin.arenas().exists(id)) {Msg.send(p, "errors.arena-exists"); return true;}
                Arena arena = plugin.arenas().create(id, p.getWorld().getName());
                if (args.length >= 3)
                {
                    Integer max = parseInt(p, args[2], 2, 128);
                    if (max == null) {return true;}
                    arena.setMaxPlayers(max);
                }
                arena.setLobby(p.getLocation());
                plugin.arenas().save(arena);
                Msg.send(p, "admin.arena-created", Msg.ph("arena", id));
                Msg.send(p, "admin.arena-created-hint", Msg.ph("arena", id));
                return true;
            }
            case "remove" ->
            {
                if (!requireArena(p, id)) {return true;}
                plugin.arenas().delete(id);
                Msg.send(p, "admin.arena-removed", Msg.ph("arena", id));
                return true;
            }
            case "enable", "disable" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (sub.equals("enable"))
                {
                    List<ArenaCheck.Finding> findings = ArenaCheck.run(plugin, arena);
                    ArenaCheck.report(p, arena, findings);
                    if (ArenaCheck.hasCritical(findings))
                    {
                        Msg.send(p, "check.enable-blocked", Msg.ph("arena", id));
                        return true;
                    }
                }
                arena.setEnabled(sub.equals("enable"));
                plugin.arenas().save(arena);
                Msg.send(p, sub.equals("enable") ? "admin.arena-enabled" : "admin.arena-disabled", Msg.ph("arena", id));
                return true;
            }
            case "check" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                ArenaCheck.report(p, arena, ArenaCheck.run(plugin, arena));
                return true;
            }
            case "worldsetup" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                World world = arena.getWorld();
                if (world == null) {Msg.send(p, "errors.world-not-loaded"); return true;}
                Msg.send(p, "admin.worldsetup-header", Msg.ph("arena", id), Msg.ph("world", world.getName()));
                applyRule(p, world, GameRules.SPAWN_MOBS, false);             // естественный спавн мобов
                applyRule(p, world, GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0); // огонь не тикает
                applyRule(p, world, GameRules.MOB_GRIEFING, false);           // криперы/эндермены не портят арену
                applyRule(p, world, GameRules.ADVANCE_WEATHER, false);
                applyRule(p, world, GameRules.SPAWN_PHANTOMS, false);
                applyRule(p, world, GameRules.SPAWN_WANDERING_TRADERS, false); // странствующий торговец ≠ наш торговец
                applyRule(p, world, GameRules.SPAWN_PATROLS, false);
                applyRule(p, world, GameRules.RAIDS, false);
                applyRule(p, world, GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                applyRule(p, world, GameRules.SPECTATORS_GENERATE_CHUNKS, false);
                applyRule(p, world, GameRules.IMMEDIATE_RESPAWN, true);       // страховка: смерти у нас фейковые
                world.setStorm(false);
                world.setThundering(false);
                Msg.send(p, "admin.worldsetup-done", Msg.ph("world", world.getName()));
                return true;
            }
            case "markers" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (arena.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return true;}
                if (arena.getSession() != null) {Msg.send(p, "admin.markers-busy", Msg.ph("arena", id)); return true;}
                int n = SetupMarkers.placeAll(arena);
                Msg.send(p, "admin.markers-placed", Msg.ph("arena", id), Msg.ph("n", n));
                return true;
            }
            case "stop" ->
            {
                if (id.equals("ALL"))
                {
                    for (Arena arena : plugin.arenas().all().values())
                    {
                        if (arena.getSession() != null) {arena.getSession().adminStop();}
                    }
                    Msg.send(p, "admin.arena-stopped", Msg.ph("arena", "ALL"));
                    return true;
                }
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (arena.getSession() != null) {arena.getSession().adminStop();}
                Msg.send(p, "admin.arena-stopped", Msg.ph("arena", id));
                return true;
            }
            case "start" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                GameSession session = arena.getSession();
                if (session == null || !session.forceStart()) {Msg.send(p, "admin.cannot-force-start"); return true;}
                Msg.send(p, "admin.arena-started", Msg.ph("arena", id));
                return true;
            }
            case "setlobby" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                arena.setLobby(p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
                arena.setWorldName(p.getWorld().getName());
                plugin.arenas().save(arena);
                Msg.send(p, "admin.lobby-set", Msg.ph("arena", id));
                Msg.send(p, "admin.lobby-set-hint", Msg.ph("arena", id));
                return true;
            }
            case "setname" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String name = joinArgs(args, 2);
                arena.setDisplayNameRaw(name);
                plugin.arenas().save(arena);
                Msg.send(p, "admin.name-set", Msg.ph("arena", id), Msg.phMm("name", name));
                Msg.send(p, "admin.name-set-hint", Msg.ph("arena", id));
                return true;
            }
            case "setdesc" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String desc = joinArgs(args, 2);
                arena.setDescriptionRaw(desc);
                plugin.arenas().save(arena);
                Msg.send(p, "admin.desc-set", Msg.ph("arena", id), Msg.phMm("description", desc));
                return true;
            }
            case "setmaxplayers", "setminplayers" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer n = parseInt(p, args[2], 1, 128);
                if (n == null) {return true;}
                if (sub.equals("setmaxplayers")) {arena.setMaxPlayers(n);} else {arena.setMinPlayers(n);}
                plugin.arenas().save(arena);
                Msg.send(p, sub.equals("setmaxplayers") ? "admin.max-players-set" : "admin.min-players-set",
                    Msg.ph("arena", id), Msg.ph("n", n));
                return true;
            }
            case "set" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 4) {Msg.send(p, "errors.not-enough-args"); return true;}
                String key = args[2].toLowerCase(Locale.ROOT);
                boolean zeroAllowed = key.startsWith("wear") || key.equals("dynamicchests");
                Integer n = parseInt(p, args[3], zeroAllowed ? 0 : 1, Integer.MAX_VALUE);
                if (n == null) {return true;}
                switch (key)
                {
                    case "wearmin" -> arena.setWearMinPercent(Math.min(99, n));
                    case "wearmax" -> arena.setWearMaxPercent(Math.min(99, n));
                    case "dynamicchests" -> arena.setDynamicChests(n > 0);
                    case "duration" -> arena.setDurationSeconds(n);
                    case "eventinterval" -> arena.setEventIntervalSeconds(n);
                    case "salaryinterval" -> arena.setSalaryIntervalSeconds(n);
                    case "salarygold" -> arena.setSalaryGold(n);
                    case "glowtime" -> arena.setGlowSecondsBeforeEnd(n);
                    case "glowgold" -> arena.setGlowBonusGold(n);
                    case "contractminarena" -> arena.setContractsMinPerArena(n);
                    case "contractmaxarena" -> arena.setContractsMaxPerArena(n);
                    case "contractminchest" -> arena.setContractsMinPerChest(n);
                    case "contractmaxchest" -> arena.setContractsMaxPerChest(n);
                    case "traders" -> arena.setTraderCount(n);
                    case "tables" -> arena.setTableCount(n);
                    case "forkuses" -> arena.setForkUses(n);
                    case "startgold" -> arena.setStartGold(n);
                    case "startdelay" -> arena.setStartDelaySeconds(n);
                    case "startdelayfull" -> arena.setStartDelayFullSeconds(n);
                    default -> {Msg.send(p, "errors.unknown-subcommand"); return true;}
                }
                plugin.arenas().save(arena);
                Msg.send(p, "admin.setting-set", Msg.ph("key", key), Msg.ph("arena", id), Msg.ph("n", n));
                return true;
            }
            case "addspawn" -> {return giveMarker(p, id, "spawn", Material.BEACON, null);}
            case "addfinalspawn" -> {return giveMarker(p, id, "finalspawn", Material.LODESTONE, null);}
            case "addchest" -> {return giveMarker(p, id, "chest", Material.CHEST, null);}
            case "addtable" -> {return giveMarker(p, id, "table", Material.ENCHANTING_TABLE, null);}
            case "addore" -> {return giveMarker(p, id, "ore", Material.STONE, null);}
            case "addlever" ->
            {
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                return giveMarker(p, id, "lever", Material.LEVER, joinArgs(args, 2));
            }
            case "addvillager" ->
            {
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String traderId = args[2].toUpperCase(Locale.ROOT);
                if (!plugin.traders().exists(traderId)) {Msg.send(p, "errors.trader-not-exists"); return true;}
                return giveMarker(p, id, "villager", Material.CRAFTING_TABLE, traderId);
            }
            case "breakable" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                ItemStack wand = Items.special(Material.IRON_AXE, Msg.get("breakable.wand-name"),
                    Msg.getList("breakable.wand-lore", Msg.ph("arena", id)), "breakwand");
                ItemMeta meta = wand.getItemMeta();
                meta.getPersistentDataContainer().set(Keys.MARKER_ARENA, PersistentDataType.STRING, arena.getId());
                wand.setItemMeta(meta);
                p.getInventory().addItem(wand);
                Msg.send(p, "breakable.wand-given", Msg.ph("arena", id), Msg.ph("n", arena.getBreakables().size()));
                return true;
            }
            case "addcontract" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String cid = args[2].toUpperCase(Locale.ROOT);
                if (!plugin.contracts().exists(cid)) {Msg.send(p, "errors.contract-not-exists"); return true;}
                if (!arena.getContractIds().contains(cid)) {arena.getContractIds().add(cid);}
                plugin.arenas().save(arena);
                Msg.send(p, "admin.contract-added", Msg.ph("arena", id));
                return true;
            }
            case "createcontract" ->
            {
                if (plugin.contracts().exists(id)) {Msg.send(p, "errors.contract-exists"); return true;}
                plugin.contracts().add(new Contract(id));
                plugin.contracts().save();
                Msg.send(p, "admin.contract-created", Msg.ph("contract", id));
                Msg.send(p, "admin.contract-created-hint", Msg.ph("contract", id));
                return true;
            }
            case "contracttype" ->
            {
                Contract contract = requireContract(p, id);
                if (contract == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                ContractType type = ContractType.parse(args[2]);
                if (type == null) {Msg.send(p, "errors.wrong-contract-type"); return true;}
                contract.setType(type);
                plugin.contracts().save();
                Msg.send(p, "admin.contract-type-set", Msg.ph("contract", id), Msg.ph("type", type.name()));
                Msg.send(p, "admin.contract-type-hint", Msg.ph("contract", id));
                return true;
            }
            case "contractidle" ->
            {
                Contract contract = requireContract(p, id);
                if (contract == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                contract.setIdle(joinArgs(args, 2));
                plugin.contracts().save();
                Msg.send(p, "admin.contract-idle-set", Msg.ph("contract", id));
                Msg.send(p, "admin.contract-idle-hint", Msg.ph("contract", id));
                return true;
            }
            case "contractdesc" ->
            {
                Contract contract = requireContract(p, id);
                if (contract == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String desc = joinArgs(args, 2);
                contract.setDescription(desc);
                plugin.contracts().save();
                Msg.send(p, "admin.contract-desc-set", Msg.ph("contract", id), Msg.phMm("description", desc));
                Msg.send(p, "admin.contract-desc-hint", Msg.ph("contract", id));
                return true;
            }
            case "contractamount", "contractprice" ->
            {
                Contract contract = requireContract(p, id);
                if (contract == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer n = parseInt(p, args[2], 1, 100000);
                if (n == null) {return true;}
                if (sub.equals("contractamount")) {contract.setAmount(n);} else {contract.setPrice(n);}
                plugin.contracts().save();
                Msg.send(p, sub.equals("contractamount") ? "admin.contract-amount-set" : "admin.contract-price-set",
                    Msg.ph("contract", id));
                if (sub.equals("contractamount")) {Msg.send(p, "admin.contract-amount-hint", Msg.ph("contract", id));}
                return true;
            }
            case "createtheme" ->
            {
                if (plugin.themes().exists(id)) {Msg.send(p, "errors.theme-exists"); return true;}
                plugin.themes().add(new Theme(id));
                plugin.themes().save();
                Msg.send(p, "admin.theme-created", Msg.ph("theme", id));
                Msg.send(p, "admin.theme-created-hint", Msg.ph("theme", id));
                return true;
            }
            case "themetype" ->
            {
                Theme theme = requireTheme(p, id);
                if (theme == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                ThemeType type = ThemeType.parse(args[2]);
                if (type == null) {Msg.send(p, "errors.wrong-theme-type"); return true;}
                theme.setType(type);
                plugin.themes().save();
                Msg.send(p, "admin.theme-type-set", Msg.ph("theme", id), Msg.ph("type", type.name()));
                return true;
            }
            case "themeidle" ->
            {
                Theme theme = requireTheme(p, id);
                if (theme == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                theme.setIdle(joinArgs(args, 2));
                plugin.themes().save();
                Msg.send(p, "admin.theme-idle-set", Msg.ph("theme", id));
                return true;
            }
            case "themedesc" ->
            {
                Theme theme = requireTheme(p, id);
                if (theme == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                theme.setDescription(joinArgs(args, 2));
                plugin.themes().save();
                Msg.send(p, "admin.theme-desc-set", Msg.ph("theme", id));
                return true;
            }
            case "themeamount", "themegold" ->
            {
                Theme theme = requireTheme(p, id);
                if (theme == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer n = parseInt(p, args[2], sub.equals("themeamount") ? 1 : 0, 100000);
                if (n == null) {return true;}
                if (sub.equals("themeamount")) {theme.setAmount(n);} else {theme.setGold(n);}
                plugin.themes().save();
                Msg.send(p, "admin.theme-number-set",
                    Msg.ph("theme", id), Msg.ph("key", sub.substring(5)), Msg.ph("n", n));
                return true;
            }
            case "themereturn" ->
            {
                Theme theme = requireTheme(p, id);
                if (theme == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String raw = args[2].toUpperCase(Locale.ROOT);
                if (!raw.equals(Theme.TURN_IN_SELF) && !raw.equals(Theme.TURN_IN_ANY)
                    && !plugin.traders().exists(raw))
                {
                    Msg.send(p, "errors.trader-not-exists");
                    return true;
                }
                theme.setTurnIn(raw);
                plugin.themes().save();
                Msg.send(p, "admin.theme-return-set", Msg.ph("theme", id), Msg.ph("target", raw));
                return true;
            }
            case "addtheme" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String themeId = args[2].toUpperCase(Locale.ROOT);
                if (!plugin.themes().exists(themeId)) {Msg.send(p, "errors.theme-not-exists"); return true;}
                if (!trader.getThemes().contains(themeId)) {trader.getThemes().add(themeId);}
                plugin.traders().save();
                Msg.send(p, "admin.theme-attached", Msg.ph("theme", themeId), Msg.ph("trader", id));
                return true;
            }
            case "removetheme" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String themeId = args[2].toUpperCase(Locale.ROOT);
                trader.getThemes().remove(themeId);
                plugin.traders().save();
                Msg.send(p, "admin.theme-detached", Msg.ph("theme", themeId), Msg.ph("trader", id));
                return true;
            }
            case "createvillager" ->
            {
                if (plugin.traders().exists(id)) {Msg.send(p, "errors.trader-exists"); return true;}
                plugin.traders().add(new TraderType(id));
                plugin.traders().save();
                Msg.send(p, "admin.trader-created", Msg.ph("trader", id));
                Msg.send(p, "admin.trader-created-hint", Msg.ph("trader", id));
                return true;
            }
            case "villagername" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                String name = joinArgs(args, 2);
                trader.setNameRaw(name);
                plugin.traders().save();
                Msg.send(p, "admin.trader-renamed", Msg.ph("trader", id), Msg.phMm("name", name));
                Msg.send(p, "admin.trader-renamed-hint", Msg.ph("trader", id));
                return true;
            }
            case "addtrade" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                new TradeEditorMenu(plugin, trader).open(p);
                Msg.send(p, "admin.trade-menu-opened");
                Msg.send(p, "admin.trade-menu-hint");
                return true;
            }
            case "scrapedit" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                new ScavengerEditorMenu(plugin, trader).open(p);
                Msg.send(p, "admin.scrap-editor-hint", Msg.ph("trader", trader.getId()));
                return true;
            }
            case "villagers" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (arena.getWorld() == null) {Msg.send(p, "errors.world-not-loaded"); return true;}
                if (arena.getTraderSpots().isEmpty()) {Msg.send(p, "villagers.no-points", Msg.ph("arena", id)); return true;}
                new VillagerPointsMenu(plugin, arena).open(p);
                Msg.send(p, "villagers.opened-hint", Msg.ph("arena", id), Msg.ph("n", arena.getTraderSpots().size()));
                return true;
            }
            case "chesttag" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (plugin.loot().isEmpty()) {Msg.send(p, "chesttag.no-categories", Msg.ph("arena", id)); return true;}
                for (LootCategory cat : plugin.loot().all())
                {
                    String catName = cat.getNameRaw() == null ? cat.getId() : cat.getNameRaw();
                    ItemStack tag = Items.special(cat.getIcon(), Msg.mm(catName),
                        Msg.getList("chesttag.item-lore", Msg.ph("arena", id)), "chesttag");
                    ItemMeta meta = tag.getItemMeta();
                    var pdc = meta.getPersistentDataContainer();
                    pdc.set(Keys.MARKER_ARENA, PersistentDataType.STRING, arena.getId());
                    pdc.set(Keys.CATEGORY_ID, PersistentDataType.STRING, cat.getId());
                    tag.setItemMeta(meta);
                    p.getInventory().addItem(tag);
                }
                Msg.send(p, "chesttag.given", Msg.ph("arena", id), Msg.ph("n", plugin.loot().all().size()));
                return true;
            }
            case "traderquota" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 4) {Msg.send(p, "admin.traderquota-usage"); return true;}
                String type = args[2].toUpperCase(Locale.ROOT);
                if (!plugin.traders().exists(type)) {Msg.send(p, "errors.trader-not-exists"); return true;}
                Integer n = parseInt(p, args[3], -1, 100000);
                if (n == null) {return true;}
                if (n < 0)
                {
                    arena.getTraderQuotas().remove(type);
                    Msg.send(p, "admin.traderquota-cleared", Msg.ph("arena", id), Msg.ph("type", type));
                }
                else
                {
                    arena.getTraderQuotas().put(type, n);
                    Msg.send(p, "admin.traderquota-set", Msg.ph("arena", id), Msg.ph("type", type), Msg.ph("n", n));
                }
                plugin.arenas().save(arena);
                return true;
            }
            case "addscrap" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer price = parseInt(p, args[2], 1, 100000);
                if (price == null) {return true;}
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {Msg.send(p, "errors.no-item-in-hand"); return true;}
                if (hand.getType().getMaxDurability() <= 0)
                {
                    Msg.send(p, "admin.scrap-not-durable", Msg.ph("item", hand.getType().name()));
                    return true;
                }
                trader.getScrapPrices().put(hand.getType(), price);
                plugin.traders().save();
                Msg.send(p, "admin.scrap-added",
                    Msg.ph("trader", id), Msg.ph("item", hand.getType().name()), Msg.ph("n", price));
                return true;
            }
            case "removescrap" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {Msg.send(p, "errors.no-item-in-hand"); return true;}
                boolean removed = trader.getScrapPrices().remove(hand.getType()) != null;
                plugin.traders().save();
                Msg.send(p, removed ? "admin.scrap-removed" : "admin.scrap-absent",
                    Msg.ph("trader", id), Msg.ph("item", hand.getType().name()));
                return true;
            }
            case "scrapwear" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer n = parseInt(p, args[2], 0, 99);
                if (n == null) {return true;}
                trader.setScrapMinWearPercent(n);
                plugin.traders().save();
                Msg.send(p, "admin.scrap-wear-set", Msg.ph("trader", id), Msg.ph("n", n));
                return true;
            }
            case "scraplist" ->
            {
                TraderType trader = requireTrader(p, id);
                if (trader == null) {return true;}
                Msg.send(p, "admin.scrap-list-header",
                    Msg.ph("trader", id), Msg.ph("wear", trader.getScrapMinWearPercent()));
                if (trader.getScrapPrices().isEmpty()) {Msg.send(p, "admin.scrap-list-empty"); return true;}
                for (Map.Entry<Material, Integer> e : trader.getScrapPrices().entrySet())
                {
                    Msg.send(p, "admin.scrap-list-entry", Msg.ph("item", e.getKey().name()), Msg.ph("n", e.getValue()));
                }
                return true;
            }
            default ->
            {
                Msg.send(p, "errors.unknown-subcommand");
                return true;
            }
        }
    }

    // ===== helpers =====

    private boolean giveMarker(Player p, String arenaId, String type, Material material, String extra)
    {
        Arena arena = requireArenaGet(p, arenaId);
        if (arena == null) {return true;}
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("admin.marker-lore-arena", Msg.ph("arena", arena.getId())));
        if (extra != null) {lore.add(Msg.get("admin.marker-lore-extra", Msg.ph("extra", extra)));}
        ItemStack item = Items.named(material,
            Msg.get("admin.marker-name", Msg.ph("type", Msg.raw("marker-types." + type))), lore);
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.MARKER_TYPE, PersistentDataType.STRING, type);
        pdc.set(Keys.MARKER_ARENA, PersistentDataType.STRING, arena.getId());
        if (extra != null) {pdc.set(Keys.MARKER_EXTRA, PersistentDataType.STRING, extra);}
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
        Msg.send(p, "admin.marker-given", Msg.ph("type", Msg.raw("marker-types." + type)));
        return true;
    }

    /** /escape kit ... — стартовые наборы («касты»): библиотека, копии, редактирование. */
    private void handleKit(Player p, String[] args)
    {
        if (args.length < 2) {Msg.send(p, "kit.usage"); return;}
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action)
        {
            case "list" ->
            {
                if (args.length >= 3)
                {
                    Arena arena = requireArenaGet(p, args[2].toUpperCase(Locale.ROOT));
                    if (arena == null) {return;}
                    Msg.send(p, "kit.list-arena-header",
                        Msg.ph("arena", arena.getId()), Msg.ph("default", arena.getDefaultKit()));
                    if (arena.getKits().isEmpty()) {Msg.send(p, "kit.list-empty"); return;}
                    for (Kit kit : arena.getKits()) {sendKitLine(p, kit);}
                }
                else
                {
                    Msg.send(p, "kit.list-global-header");
                    if (plugin.kits().ids().isEmpty()) {Msg.send(p, "kit.list-empty"); return;}
                    for (String kid : plugin.kits().ids()) {sendKitLine(p, plugin.kits().get(kid));}
                }
            }
            case "copy" ->
            {
                if (args.length < 4) {Msg.send(p, "kit.copy-usage"); return;}
                Kit source = plugin.kits().get(args[2]);
                if (source == null) {Msg.send(p, "kit.not-found-global", Msg.ph("id", args[2])); return;}
                Arena arena = requireArenaGet(p, args[3].toUpperCase(Locale.ROOT));
                if (arena == null) {return;}
                String newId = args.length >= 5 ? args[4] : source.getId();
                if (arena.getKit(newId) != null)
                {
                    Msg.send(p, "kit.exists", Msg.ph("id", newId), Msg.ph("arena", arena.getId()));
                    return;
                }
                arena.addKit(source.copy(newId));
                plugin.arenas().save(arena);
                Msg.send(p, "kit.copied",
                    Msg.ph("id", source.getId()), Msg.ph("arena", arena.getId()), Msg.ph("newid", newId));
            }
            case "create" ->
            {
                if (args.length < 4) {Msg.send(p, "kit.create-usage"); return;}
                Arena arena = requireArenaGet(p, args[2].toUpperCase(Locale.ROOT));
                if (arena == null) {return;}
                String kid = args[3];
                if (arena.getKit(kid) != null)
                {
                    Msg.send(p, "kit.exists", Msg.ph("id", kid), Msg.ph("arena", arena.getId()));
                    return;
                }
                arena.addKit(new Kit(kid));
                plugin.arenas().save(arena);
                Msg.send(p, "kit.created", Msg.ph("id", kid), Msg.ph("arena", arena.getId()));
                Msg.send(p, "kit.created-hint", Msg.ph("id", kid), Msg.ph("arena", arena.getId()));
            }
            case "delete" ->
            {
                Kit kit = kitArg(p, args);
                if (kit == null) {return;}
                Arena arena = plugin.arenas().get(args[2]);
                arena.removeKit(kit.getId());
                plugin.arenas().save(arena);
                Msg.send(p, "kit.deleted", Msg.ph("id", kit.getId()), Msg.ph("arena", arena.getId()));
            }
            case "edit" ->
            {
                Kit kit = kitArg(p, args);
                if (kit == null) {return;}
                Arena arena = plugin.arenas().get(args[2]);
                new KitEditorMenu(plugin, arena, kit).open(p);
                Msg.send(p, "kit.editor-hint", Msg.ph("id", kit.getId()), Msg.ph("arena", arena.getId()));
            }
            case "name" ->
            {
                Kit kit = kitArg(p, args);
                if (kit == null) {return;}
                if (args.length < 5) {Msg.send(p, "errors.not-enough-args"); return;}
                Arena arena = plugin.arenas().get(args[2]);
                String name = joinArgs(args, 4);
                kit.setNameRaw(name);
                plugin.arenas().save(arena);
                Msg.send(p, "kit.name-set", Msg.ph("id", kit.getId()), Msg.phMm("name", name));
            }
            case "icon" ->
            {
                Kit kit = kitArg(p, args);
                if (kit == null) {return;}
                Arena arena = plugin.arenas().get(args[2]);
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {Msg.send(p, "errors.no-item-in-hand"); return;}
                kit.setIcon(hand.getType());
                plugin.arenas().save(arena);
                Msg.send(p, "kit.icon-set", Msg.ph("id", kit.getId()), Msg.ph("icon", hand.getType().name()));
            }
            case "gold" ->
            {
                Kit kit = kitArg(p, args);
                if (kit == null) {return;}
                if (args.length < 5) {Msg.send(p, "errors.not-enough-args"); return;}
                Integer n = parseInt(p, args[4], -1, 100000);
                if (n == null) {return;}
                Arena arena = plugin.arenas().get(args[2]);
                kit.setGold(n);
                plugin.arenas().save(arena);
                Msg.send(p, "kit.gold-set", Msg.ph("id", kit.getId()), Msg.ph("n", n));
            }
            case "default" ->
            {
                if (args.length < 4) {Msg.send(p, "kit.default-usage"); return;}
                Arena arena = requireArenaGet(p, args[2].toUpperCase(Locale.ROOT));
                if (arena == null) {return;}
                String value = args[3];
                boolean special = value.equalsIgnoreCase("random") || value.equalsIgnoreCase("none");
                Kit named = arena.getKit(value);
                if (!special && named == null) {Msg.send(p, "kit.default-invalid", Msg.ph("value", value)); return;}
                arena.setDefaultKit(special ? value.toLowerCase(Locale.ROOT) : named.getId());
                plugin.arenas().save(arena);
                Msg.send(p, "kit.default-set", Msg.ph("arena", arena.getId()), Msg.ph("value", arena.getDefaultKit()));
            }
            default -> Msg.send(p, "kit.usage");
        }
    }

    private void sendKitLine(Player p, Kit kit)
    {
        Msg.send(p, "kit.list-entry",
            Msg.ph("id", kit.getId()),
            Msg.phMm("name", kit.getNameRaw()),
            Msg.ph("goldval", kit.getGold()),
            Msg.ph("items", kit.getItems().size()));
    }

    /** Разбор общего хвоста `<arena> <kitId>` (args[2], args[3]). Шлёт ошибку и возвращает null. */
    private Kit kitArg(Player p, String[] args)
    {
        if (args.length < 4) {Msg.send(p, "errors.not-enough-args"); return null;}
        Arena arena = requireArenaGet(p, args[2].toUpperCase(Locale.ROOT));
        if (arena == null) {return null;}
        Kit kit = arena.getKit(args[3]);
        if (kit == null) {Msg.send(p, "kit.not-found", Msg.ph("id", args[3]), Msg.ph("arena", arena.getId()));}
        return kit;
    }

    /**
     * /escape loot [gui] | list | create &lt;id&gt; | delete &lt;id&gt; | name|icon|weight|
     * minchest|maxchest|minarena|maxarena|minchests|maxchests|refill &lt;id&gt; [value] —
     * ГЛОБАЛЬНЫЕ категории лута (loot/&lt;id&gt;.yml). Богатое редактирование — в GUI.
     */
    private void handleLoot(Player p, String[] args)
    {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "gui";
        switch (action)
        {
            case "gui" -> new LootEditorMenu(plugin).open(p);
            case "list" ->
            {
                Msg.send(p, "loot.list-header");
                if (plugin.loot().isEmpty()) {Msg.send(p, "loot.list-empty"); return;}
                for (LootCategory cat : plugin.loot().all()) {sendLootLine(p, cat);}
            }
            case "create" ->
            {
                if (args.length < 3) {Msg.send(p, "loot.create-usage"); return;}
                String cid = args[2].toLowerCase(Locale.ROOT);
                if (plugin.loot().exists(cid)) {Msg.send(p, "loot.exists", Msg.ph("id", cid)); return;}
                LootCategory cat = new LootCategory(cid);
                cat.setNameRaw("<gray>" + cid);
                plugin.loot().save(cat);
                Msg.send(p, "loot.created", Msg.ph("id", cid));
            }
            case "delete" ->
            {
                LootCategory cat = lootCatArg(p, args);
                if (cat == null) {return;}
                plugin.loot().delete(cat.getId());
                Msg.send(p, "loot.deleted", Msg.ph("id", cat.getId()));
            }
            case "name" ->
            {
                LootCategory cat = lootCatArg(p, args);
                if (cat == null) {return;}
                if (args.length < 4) {Msg.send(p, "errors.not-enough-args"); return;}
                String name = joinArgs(args, 3);
                cat.setNameRaw(name);
                plugin.loot().save(cat);
                Msg.send(p, "loot.name-set", Msg.ph("id", cat.getId()), Msg.phMm("name", name));
            }
            case "icon" ->
            {
                LootCategory cat = lootCatArg(p, args);
                if (cat == null) {return;}
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {Msg.send(p, "errors.no-item-in-hand"); return;}
                cat.setIcon(hand.getType());
                plugin.loot().save(cat);
                Msg.send(p, "loot.icon-set", Msg.ph("id", cat.getId()), Msg.ph("icon", hand.getType().name()));
            }
            case "weight" ->
            {
                LootCategory cat = lootCatArg(p, args);
                if (cat == null) {return;}
                if (args.length < 4) {Msg.send(p, "errors.not-enough-args"); return;}
                Integer n = parseInt(p, args[3], 1, 1000);
                if (n == null) {return;}
                cat.setWeight(n);
                plugin.loot().save(cat);
                Msg.send(p, "loot.field-set", Msg.ph("id", cat.getId()), Msg.ph("field", "weight"), Msg.ph("n", n));
            }
            case "minchest", "maxchest", "minarena", "maxarena", "minchests", "maxchests", "refill" ->
            {
                LootCategory cat = lootCatArg(p, args);
                if (cat == null) {return;}
                if (args.length < 4) {Msg.send(p, "errors.not-enough-args"); return;}
                // -1 = без ограничения (для refill 0 = без рефилла)
                Integer n = parseInt(p, args[3], action.equals("refill") ? 0 : -1, 100000);
                if (n == null) {return;}
                switch (action)
                {
                    case "minchest" -> cat.setMinPerChest(n);
                    case "maxchest" -> cat.setMaxPerChest(n);
                    case "minarena" -> cat.setMinPerArena(n);
                    case "maxarena" -> cat.setMaxPerArena(n);
                    case "minchests" -> cat.setMinChests(n);
                    case "maxchests" -> cat.setMaxChests(n);
                    case "refill" -> cat.setRefillSeconds(n);
                    default -> {}
                }
                plugin.loot().save(cat);
                Msg.send(p, "loot.field-set", Msg.ph("id", cat.getId()), Msg.ph("field", action), Msg.ph("n", n));
            }
            default -> Msg.send(p, "loot.usage");
        }
    }

    private void sendLootLine(Player p, LootCategory cat)
    {
        Msg.send(p, "loot.list-entry",
            Msg.ph("id", cat.getId()),
            Msg.phMm("name", cat.getNameRaw() == null ? cat.getId() : cat.getNameRaw()),
            Msg.ph("weight", cat.getWeight()),
            Msg.ph("items", cat.getLoot().size()),
            Msg.ph("chests", cat.getMaxChests()),
            Msg.ph("refill", cat.getRefillSeconds()));
    }

    /** Разбор `<id>` (args[2]) глобальной категории. Шлёт ошибку и возвращает null. */
    private LootCategory lootCatArg(Player p, String[] args)
    {
        if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return null;}
        LootCategory cat = plugin.loot().get(args[2].toLowerCase(Locale.ROOT));
        if (cat == null) {Msg.send(p, "loot.not-found", Msg.ph("id", args[2]));}
        return cat;
    }

    /** /escape chestsetup <ID> [gui] | stop — мастер разметки точек по категориям. */
    private void handleChestSetup(Player p, String[] args)
    {
        if (args.length < 2) {Msg.send(p, "chestsetup.usage"); return;}
        if (args[1].equalsIgnoreCase("stop")) {plugin.chestSetup().stop(p, false); return;}
        Arena arena = requireArenaGet(p, args[1].toUpperCase(Locale.ROOT));
        if (arena == null) {return;}
        if (args.length >= 3 && args[2].equalsIgnoreCase("gui")) {plugin.chestSetup().openGui(p, arena);}
        else {plugin.chestSetup().start(p, arena);}
    }

    /**
     * /escape trades [VID] — без VID открывает список жителей (создание из
     * инвентаря/сундука/копии + редактор); с VID сразу открывает товары жителя.
     */
    private void handleTrades(Player p, String[] args)
    {
        if (args.length < 2) {new TraderListMenu(plugin).open(p); return;}
        TraderType trader = requireTrader(p, args[1].toUpperCase(Locale.ROOT));
        if (trader == null) {return;}
        new TradeListEditorMenu(plugin, trader).open(p);
        Msg.send(p, "admin.trades-editor-hint", Msg.ph("trader", trader.getId()));
    }

    /**
     * /escape debuglog [on|off|save|clear] — продвинутое логирование.
     * Состояние переживает рестарт: пишется в config.yml (debug-log.enabled).
     */
    private void handleDebugLog(Player p, String[] args)
    {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action)
        {
            case "status" -> sendDebugLogStatus(p);
            case "on", "off" ->
            {
                boolean on = action.equals("on");
                if (on == DebugLog.on()) {Msg.send(p, on ? "debuglog.already-on" : "debuglog.already-off"); return;}
                DebugLog.setEnabled(on);
                Msg.send(p, on ? "debuglog.enabled" : "debuglog.disabled");
                sendDebugLogStatus(p);
            }
            case "save" ->
            {
                int lines = DebugLog.buffered();
                if (lines == 0) {Msg.send(p, "debuglog.save-empty"); return;}
                File file = DebugLog.save();
                if (file == null) {Msg.send(p, "debuglog.save-failed"); return;}
                Msg.send(p, "debuglog.saved", Msg.ph("file", file.getName()), Msg.ph("n", lines));
                Msg.send(p, "debuglog.saved-path", Msg.ph("path", file.getParent()));
            }
            case "clear" -> Msg.send(p, "debuglog.cleared", Msg.ph("n", DebugLog.clear()));
            default -> Msg.send(p, "debuglog.usage");
        }
    }

    private void sendDebugLogStatus(Player p)
    {
        Msg.send(p, "debuglog.status",
            Msg.phC("state", Msg.get(DebugLog.on() ? "debuglog.state-on" : "debuglog.state-off")),
            Msg.phC("console", Msg.get(DebugLog.toConsole() ? "debuglog.state-on" : "debuglog.state-off")),
            Msg.ph("lines", DebugLog.buffered()),
            Msg.ph("limit", DebugLog.limit()),
            Msg.ph("lost", DebugLog.droppedLines()));
    }

    /** /escape debug <действие> — симуляция игровых событий для соло-отладки. */
    private void handleDebug(Player p, String[] args)
    {
        if (!p.hasPermission("escape.admin.debug")) {Msg.send(p, "debug.no-permission"); return;}
        GameSession session = plugin.arenas().sessionOf(p);
        if (session == null) {Msg.send(p, "debug.not-in-match"); return;}
        if (args.length < 2) {Msg.send(p, "debug.usage"); return;}
        String action = args[1].toLowerCase(Locale.ROOT);

        if (session.getPhase() != GameSession.Phase.RUNNING)
        {
            Msg.send(p, "debug.need-running");
            return;
        }

        switch (action)
        {
            case "death" ->
            {
                if (!session.isPlaying(p.getUniqueId())) {Msg.send(p, "debug.not-playing"); return;}
                session.dropInventory(p, p.getLocation());
                p.setHealth(20.0);
                session.handleDeath(p);
                Msg.send(p, "debug.death-done");
            }
            case "kill" ->
            {
                if (args.length < 3) {Msg.send(p, "debug.kill-usage"); return;}
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null || !session.isPlaying(target.getUniqueId()))
                {
                    Msg.send(p, "debug.player-not-found", Msg.ph("player", args[2]));
                    return;
                }
                session.dropInventory(target, target.getLocation());
                target.setHealth(20.0);
                session.handleDeath(target);
                Msg.send(p, "debug.kill-done", Msg.ph("player", target.getName()));
            }
            case "refill" ->
            {
                int n = session.debugRefillAll();
                Msg.send(p, "debug.refill-done", Msg.ph("n", n));
            }
            case "contract" ->
            {
                if (session.debugCompleteContract(p)) {Msg.send(p, "debug.contract-done");}
                else {Msg.send(p, "debug.contract-none");}
            }
            case "theme" ->
            {
                if (session.themes().debugComplete(p)) {Msg.send(p, "debug.theme-done");}
                else {Msg.send(p, "debug.theme-none");}
            }
            case "event" ->
            {
                if (args.length < 3) {Msg.send(p, "debug.event-usage"); return;}
                GameEvent event;
                try {event = GameEvent.valueOf(args[2].toUpperCase(Locale.ROOT));}
                catch (IllegalArgumentException e)
                {
                    Msg.send(p, "debug.event-unknown", Msg.ph("event", args[2]));
                    return;
                }
                session.debugStartEvent(event);
                Msg.send(p, "debug.event-done", Msg.ph("event", event.name()));
            }
            case "glow" ->
            {
                if (session.debugStartGlow()) {Msg.send(p, "debug.glow-done");}
                else {Msg.send(p, "debug.glow-already");}
            }
            case "final" ->
            {
                if (session.debugFinalBattle()) {Msg.send(p, "debug.final-done");}
                else {Msg.send(p, "debug.final-already");}
            }
            case "finish" ->
            {
                session.debugFinish();
                Msg.send(p, "debug.finish-done");
            }
            case "gold" ->
            {
                if (args.length < 3) {Msg.send(p, "debug.gold-usage"); return;}
                Integer n = parseInt(p, args[2], 1, 1024);
                if (n == null) {return;}
                session.giveGold(p, n);
                Msg.send(p, "debug.gold-done", Msg.ph("n", n));
            }
            case "key" ->
            {
                p.getInventory().addItem(session.themes().createMagicKey());
                Msg.send(p, "debug.key-given");
            }
            case "insight" ->
            {
                p.getInventory().addItem(session.respawnBlocks().createInsightItem());
                Msg.send(p, "debug.insight-given");
            }
            default -> Msg.send(p, "debug.usage");
        }
    }

    private <T> void applyRule(Player p, World world, GameRule<T> rule, T value)
    {
        world.setGameRule(rule, value);
        Msg.send(p, "admin.worldsetup-rule", Msg.ph("rule", rule.getKey().getKey()), Msg.ph("value", String.valueOf(value)));
    }

    private boolean requireArena(Player p, String id)
    {
        if (!plugin.arenas().exists(id)) {Msg.send(p, "errors.arena-not-exists"); return false;}
        return true;
    }

    private Arena requireArenaGet(Player p, String id)
    {
        Arena arena = plugin.arenas().get(id);
        if (arena == null) {Msg.send(p, "errors.arena-not-exists");}
        return arena;
    }

    private Theme requireTheme(Player p, String id)
    {
        Theme theme = plugin.themes().get(id);
        if (theme == null) {Msg.send(p, "errors.theme-not-exists");}
        return theme;
    }

    private Contract requireContract(Player p, String id)
    {
        Contract contract = plugin.contracts().get(id);
        if (contract == null) {Msg.send(p, "errors.contract-not-exists");}
        return contract;
    }

    private TraderType requireTrader(Player p, String id)
    {
        TraderType trader = plugin.traders().get(id);
        if (trader == null) {Msg.send(p, "errors.trader-not-exists");}
        return trader;
    }

    private Integer parseInt(Player p, String raw, int min, int max)
    {
        try
        {
            int n = Integer.parseInt(raw);
            if (n < min || n > max) {Msg.send(p, "errors.number-out-of-range"); return null;}
            return n;
        }
        catch (NumberFormatException e)
        {
            Msg.send(p, "errors.must-be-number");
            return null;
        }
    }

    private String joinArgs(String[] args, int from)
    {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    private void sendHelp(Player p)
    {
        for (Component line : Msg.getList("help.player")) {p.sendMessage(line);}
        if (p.hasPermission("escape.admin"))
        {
            for (Component line : Msg.getList("help.admin")) {p.sendMessage(line);}
        }
    }

    // ===== tab =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p)) {return List.of();}
        List<String> out = new ArrayList<>();

        if (args.length == 1)
        {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (p.hasPermission("escape.admin")) {subs.addAll(ADMIN_SUBS);}
            filter(subs, args[0], out);
            return out;
        }

        if (!p.hasPermission("escape.admin")) {return out;}
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("kit")) {return kitTab(args, out);}
        if (sub.equals("loot")) {return lootTab(args, out);}

        if (args.length == 2)
        {
            switch (sub)
            {
                case "join", "remove", "enable", "disable", "check", "setlobby", "setname", "setdesc",
                     "setminplayers", "setmaxplayers", "set", "start", "worldsetup", "markers",
                     "addspawn", "addfinalspawn", "addchest", "addtable", "addore", "addlever", "addvillager",
                     "addcontract", "villagers", "chesttag", "traderquota", "breakable" ->
                    filter(new ArrayList<>(plugin.arenas().ids()), args[1], out);
                case "stop" ->
                {
                    List<String> ids = new ArrayList<>(plugin.arenas().ids());
                    ids.add("all");
                    filter(ids, args[1], out);
                }
                case "chestsetup" ->
                {
                    List<String> ids = new ArrayList<>(plugin.arenas().ids());
                    ids.add("stop");
                    filter(ids, args[1], out);
                }
                case "debug" -> filter(new ArrayList<>(List.of(
                    "death", "kill", "refill", "contract", "theme", "event",
                    "glow", "final", "finish", "gold", "key", "insight")), args[1], out);
                case "debuglog" -> filter(new ArrayList<>(List.of(
                    "on", "off", "save", "clear", "status")), args[1], out);
                case "contracttype", "contractidle", "contractdesc", "contractamount", "contractprice" ->
                    filter(new ArrayList<>(plugin.contracts().ids()), args[1], out);
                case "themetype", "themeidle", "themedesc", "themeamount", "themegold", "themereturn" ->
                    filter(new ArrayList<>(plugin.themes().ids()), args[1], out);
                case "villagername", "addtrade", "trades", "addtheme", "removetheme",
                     "addscrap", "removescrap", "scrapwear", "scraplist", "scrapedit" ->
                    filter(new ArrayList<>(plugin.traders().ids()), args[1], out);
                default -> {}
            }
            return out;
        }

        if (args.length == 3)
        {
            switch (sub)
            {
                case "contracttype" ->
                {
                    List<String> types = new ArrayList<>();
                    for (ContractType type : ContractType.values()) {types.add(type.name());}
                    filter(types, args[2], out);
                }
                case "themetype" ->
                {
                    List<String> types = new ArrayList<>();
                    for (ThemeType type : ThemeType.values()) {types.add(type.name());}
                    filter(types, args[2], out);
                }
                case "themereturn" ->
                {
                    List<String> targets = new ArrayList<>(List.of(Theme.TURN_IN_SELF, Theme.TURN_IN_ANY));
                    targets.addAll(plugin.traders().ids());
                    filter(targets, args[2], out);
                }
                case "addtheme", "removetheme" -> filter(new ArrayList<>(plugin.themes().ids()), args[2], out);
                case "debug" ->
                {
                    if (args[1].equalsIgnoreCase("event"))
                    {
                        List<String> events = new ArrayList<>();
                        for (GameEvent event : GameEvent.values()) {events.add(event.name());}
                        filter(events, args[2], out);
                    }
                    else if (args[1].equalsIgnoreCase("kill"))
                    {
                        List<String> names = new ArrayList<>();
                        for (Player online : plugin.getServer().getOnlinePlayers()) {names.add(online.getName());}
                        filter(names, args[2], out);
                    }
                }
                case "set" -> filter(new ArrayList<>(ARENA_SETTINGS), args[2], out);
                case "chestsetup" -> filter(new ArrayList<>(List.of("gui")), args[2], out);
                case "addcontract" -> filter(new ArrayList<>(plugin.contracts().ids()), args[2], out);
                case "addvillager", "traderquota" -> filter(new ArrayList<>(plugin.traders().ids()), args[2], out);
                default -> {}
            }
        }
        return out;
    }

    private List<String> kitTab(String[] args, List<String> out)
    {
        List<String> actions = List.of("list", "copy", "create", "delete", "edit", "name", "icon", "gold", "default");
        if (args.length == 2) {filter(new ArrayList<>(actions), args[1], out); return out;}
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3)
        {
            if (action.equals("copy")) {filter(new ArrayList<>(plugin.kits().ids()), args[2], out);}
            else {filter(new ArrayList<>(plugin.arenas().ids()), args[2], out);}
            return out;
        }
        if (args.length == 4)
        {
            switch (action)
            {
                case "copy" -> filter(new ArrayList<>(plugin.arenas().ids()), args[3], out);
                case "delete", "edit", "name", "icon", "gold" -> filter(arenaKitIds(args[2]), args[3], out);
                case "default" ->
                {
                    List<String> vals = new ArrayList<>(List.of("random", "none"));
                    vals.addAll(arenaKitIds(args[2]));
                    filter(vals, args[3], out);
                }
                default -> {}
            }
        }
        return out;
    }

    private List<String> arenaKitIds(String arenaId)
    {
        Arena arena = plugin.arenas().get(arenaId);
        List<String> ids = new ArrayList<>();
        if (arena != null) {for (Kit kit : arena.getKits()) {ids.add(kit.getId());}}
        return ids;
    }

    private List<String> lootTab(String[] args, List<String> out)
    {
        List<String> actions = List.of("gui", "list", "create", "delete", "name", "icon", "weight",
            "minchest", "maxchest", "minarena", "maxarena", "minchests", "maxchests", "refill");
        if (args.length == 2) {filter(new ArrayList<>(actions), args[1], out); return out;}
        String action = args[1].toLowerCase(Locale.ROOT);
        // все действия, кроме gui/list/create, адресуют существующую глобальную категорию по id
        if (args.length == 3 && !action.equals("gui") && !action.equals("list") && !action.equals("create"))
        {
            filter(new ArrayList<>(plugin.loot().ids()), args[2], out);
        }
        return out;
    }

    private void filter(List<String> options, String prefix, List<String> out)
    {
        String low = prefix.toLowerCase(Locale.ROOT);
        for (String option : options)
        {
            if (option.toLowerCase(Locale.ROOT).startsWith(low)) {out.add(option);}
        }
    }
}
