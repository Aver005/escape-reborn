package me.aver005.escape.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.menu.ArenaSelectMenu;
import me.aver005.escape.menu.LootEditorMenu;
import me.aver005.escape.menu.TradeEditorMenu;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.trader.TraderType;
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
        "save", "reload", "list", "stop", "start", "create", "remove", "enable", "disable",
        "setlobby", "setname", "setdesc", "setminplayers", "setmaxplayers", "set", "worldsetup",
        "addspawn", "addfinalspawn", "addchest", "addtable", "addore", "addlever", "addvillager",
        "additem", "edititems", "addcontract",
        "createcontract", "contracttype", "contractidle", "contractdesc", "contractamount", "contractprice",
        "createtheme", "themetype", "themeidle", "themedesc", "themeamount", "themegold", "themereturn",
        "addtheme", "removetheme",
        "createvillager", "villagername", "addtrade");
    private static final List<String> ARENA_SETTINGS = List.of(
        "duration", "eventinterval", "salaryinterval", "salarygold", "glowtime", "glowgold",
        "chests", "traders", "tables", "forkuses", "startgold", "startdelay", "startdelayfull");

    private final EscapePlugin plugin;

    public EscapeCommand(EscapePlugin plugin) {this.plugin = plugin;}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player p)) {sender.sendMessage("Только для игроков"); return true;}

        if (args.length == 0) {new ArenaSelectMenu(plugin).open(p); return true;}
        String sub = args[0].toLowerCase(Locale.ROOT);

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
                plugin.joinArena(p, arena);
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
                arena.setEnabled(sub.equals("enable"));
                plugin.arenas().save(arena);
                Msg.send(p, sub.equals("enable") ? "admin.arena-enabled" : "admin.arena-disabled", Msg.ph("arena", id));
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
                Integer n = parseInt(p, args[3], 1, Integer.MAX_VALUE);
                if (n == null) {return true;}
                switch (key)
                {
                    case "duration" -> arena.setDurationSeconds(n);
                    case "eventinterval" -> arena.setEventIntervalSeconds(n);
                    case "salaryinterval" -> arena.setSalaryIntervalSeconds(n);
                    case "salarygold" -> arena.setSalaryGold(n);
                    case "glowtime" -> arena.setGlowSecondsBeforeEnd(n);
                    case "glowgold" -> arena.setGlowBonusGold(n);
                    case "chests" -> arena.setChestCount(n);
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
            case "additem" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                if (args.length < 3) {Msg.send(p, "errors.not-enough-args"); return true;}
                Integer weight = parseInt(p, args[2], 1, 250);
                if (weight == null) {return true;}
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {Msg.send(p, "errors.no-item-in-hand"); return true;}
                ItemStack base = hand.clone();
                arena.getLoot().removeIf(entry -> entry.item().isSimilar(base));
                arena.getLoot().add(new WeightedItem(base, weight));
                plugin.arenas().save(arena);
                Msg.send(p, "admin.item-added", Msg.ph("arena", id), Msg.ph("n", weight));
                return true;
            }
            case "edititems" ->
            {
                Arena arena = requireArenaGet(p, id);
                if (arena == null) {return true;}
                new LootEditorMenu(plugin, arena).open(p);
                Msg.send(p, "admin.loot-editor-hint", Msg.ph("arena", id));
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

        if (args.length == 2)
        {
            switch (sub)
            {
                case "join", "remove", "enable", "disable", "setlobby", "setname", "setdesc",
                     "setminplayers", "setmaxplayers", "set", "start", "worldsetup",
                     "addspawn", "addfinalspawn", "addchest", "addtable", "addore", "addlever", "addvillager",
                     "additem", "edititems", "addcontract" ->
                    filter(new ArrayList<>(plugin.arenas().ids()), args[1], out);
                case "stop" ->
                {
                    List<String> ids = new ArrayList<>(plugin.arenas().ids());
                    ids.add("all");
                    filter(ids, args[1], out);
                }
                case "contracttype", "contractidle", "contractdesc", "contractamount", "contractprice" ->
                    filter(new ArrayList<>(plugin.contracts().ids()), args[1], out);
                case "themetype", "themeidle", "themedesc", "themeamount", "themegold", "themereturn" ->
                    filter(new ArrayList<>(plugin.themes().ids()), args[1], out);
                case "villagername", "addtrade", "addtheme", "removetheme" ->
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
                case "set" -> filter(new ArrayList<>(ARENA_SETTINGS), args[2], out);
                case "addcontract" -> filter(new ArrayList<>(plugin.contracts().ids()), args[2], out);
                case "addvillager" -> filter(new ArrayList<>(plugin.traders().ids()), args[2], out);
                default -> {}
            }
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
