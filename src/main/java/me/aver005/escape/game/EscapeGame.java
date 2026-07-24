package me.aver005.escape.game;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.command.EscapeCommand;
import me.aver005.escape.menu.ArenaSelectMenu;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.data.DataKey;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.game.MinigameDescriptor;
import ru.kiviuly.mg.api.stats.StatsService;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Escape как игра платформы: тонкий адаптер между ядром и правилами.
 *
 * <p>Каркас (лобби, отсчёт, фазы, ростер, снапшоты игроков, откат мира, HUD,
 * статистика) ведёт ядро и зовёт хуки. Сами правила Escape живут в
 * {@link EscapeRules} — по объекту на матч (хранится в матче через {@code DataKey}).</p>
 *
 * <p>Класс без состояния: одна инстанция на плагин, матчей может идти несколько.</p>
 */
public final class EscapeGame extends Minigame
{
    /** Стабильный id игры (владелец арен, узел прав, папка данных). */
    public static final String ID = "escape";

    private final EscapePlugin plugin;
    /** Обработчик игро-специфичных подкоманд /escape (движок зовёт через onCommand/tabComplete). */
    private final EscapeCommand commands;

    public EscapeGame(EscapePlugin plugin, MgCore core)
    {
        super(core, plugin);
        this.plugin = plugin;
        this.commands = new EscapeCommand(plugin);
    }

    @Override
    public String id() {return ID;}

    /** Игро-дефолты новой арены (числа из config.yml → «карман» настроек арены). */
    @Override
    public void onArenaCreated(Arena arena)
    {
        var cfg = plugin.getConfig().getConfigurationSection("defaults");
        if (cfg != null)
        {
            arena.setMinPlayers(cfg.getInt("min-players", 2));
            arena.setMaxPlayers(cfg.getInt("max-players", 12));
            arena.setMatchDurationSeconds(cfg.getInt("duration-seconds", 1200));
            arena.setLobbyCountdownSeconds(cfg.getInt("start-delay-seconds", 60));
            arena.setCountdownFullSeconds(cfg.getInt("start-delay-full-seconds", 10));
            EscapeArena.setTraderCount(arena, cfg.getInt("trader-count", 32));
            EscapeArena.setTableCount(arena, cfg.getInt("table-count", 5));
            EscapeArena.setEventIntervalSeconds(arena, cfg.getInt("event-interval-seconds", 210));
            EscapeArena.setSalaryIntervalSeconds(arena, cfg.getInt("salary-interval-seconds", 600));
            EscapeArena.setSalaryGold(arena, cfg.getInt("salary-gold", 16));
            EscapeArena.setGlowSecondsBeforeEnd(arena, cfg.getInt("glow-seconds-before-end", 600));
            EscapeArena.setGlowBonusGold(arena, cfg.getInt("glow-bonus-gold", 18));
            EscapeArena.setForkUses(arena, cfg.getInt("fork-uses", 1));
            EscapeArena.setStartGold(arena, cfg.getInt("start-gold", 24));
        }
        plugin.arenas().save(arena);
    }

    @Override
    public String displayName() {return Msg.raw("game.display-name");}

    /** Узел прав игры: escape.admin (плюс общий mg.admin из ядра). Совпадает с plugin.yml. */
    @Override
    public String adminPermission() {return "escape.admin";}

    @Override
    public MinigameDescriptor descriptor()
    {
        return new MinigameDescriptor(id(), Component.text("Escape"),
            new ItemStack(Material.IRON_BARS), 2, 12, false);
    }

    /** Escape ведёт свою статистику сам (свои счётчики) — движок не авто-пишет recordMatch. */
    @Override
    public boolean recordsOwnStats() {return true;}

    /** Правила матча живут в самом матче: по объекту на матч. */
    private static final DataKey<EscapeRules> RULES = DataKey.of("escape-rules", EscapeRules.class);

    /** Правила текущего матча (создаются при первом обращении). */
    public EscapeRules rules(Match m)
    {
        EscapeRules r = m.get(RULES);
        if (r == null) {r = new EscapeRules(plugin, m); m.set(RULES, r);}
        return r;
    }

    /** Правила уже идущего матча (или null, если матч ещё не заведён). Без создания. */
    public static EscapeRules rulesOf(Match m) {return m == null ? null : m.get(RULES);}

    // ===== хуки жизненного цикла (ядро -> правила) =====

    @Override
    public void onLobbyJoin(Match m, Player p) {rules(m).onLobbyJoin(p);}

    @Override
    public void onStart(Match m) {rules(m).onStart();}

    @Override
    public void giveLoadout(Match m, Player p) {rules(m).giveLoadout(p);}

    @Override
    public void onTick(Match m) {rules(m).onTick();}

    @Override
    public boolean onLethalDamage(Match m, Player p) {return rules(m).onLethalDamage(p);}

    @Override
    public void onPlayerEliminated(Match m, MatchPlayer mp) {rules(m).onEliminated(mp);}

    @Override
    public void onPlayerRemoved(Match m, UUID id) {rules(m).onPlayerRemoved(id);}

    @Override
    public MatchResult checkResult(Match m) {return rules(m).checkResult();}

    @Override
    public void onEnd(Match m, MatchResult result) {rules(m).onEnd(result);}

    @Override
    public void onCleanup(Match m) {rules(m).onCleanup();}

    @Override
    public List<Component> scoreboardLines(Match m, Player viewer) {return rules(m).scoreboardLines(viewer);}

    /** /escape reload: перечитать escape-контент (киты/лут/контракты/темы/торговцы + arenaConfigs). */
    @Override
    public void onReload() {plugin.reloadEverything();}

    /** /mg remove <арена escape>: подчистить игро-специфичный конфиг арены. */
    @Override
    public void onArenaRemoved(String arenaId) {plugin.arenaConfigs().remove(arenaId);}

    /** Вход запрещён, пока идёт мастер разметки сундуков (свой или на этой арене). */
    @Override
    public boolean canJoin(Arena arena, Player p)
    {
        if (plugin.chestSetup().isActive(p) || plugin.chestSetup().isArenaBusy(arena))
        {
            Msg.send(p, "chestsetup.busy-join");
            return false;
        }
        return true;
    }

    /** /escape без аргументов — своё меню выбора арен Escape (вместо ядрового). */
    @Override
    public boolean onEmptyCommand(Player p) {new ArenaSelectMenu(plugin).open(p); return true;}

    // ===== оффлайн-стражи: игрок остаётся в матче при дисконнекте, возвращается при возврате =====

    /** Дисконнект в идущем матче (вне финальной битвы) — оставить оффлайн под стражем. */
    @Override
    public boolean keepOnDisconnect(Match m, Player p) {return !rules(m).isFinalBattle();}

    @Override
    public void onPlayerDisconnect(Match m, Player p) {rules(m).offlineGuards().beginGuard(p);}

    @Override
    public void onPlayerReconnect(Match m, Player p) {rules(m).offlineGuards().handleRejoin(p);}

    // ===== команды (движок -> игро-специфичные подкоманды /escape) =====

    @Override
    public boolean onCommand(Player p, String sub, String[] args) {return commands.handle(p, sub, args);}

    @Override
    public List<String> tabComplete(Player p, String[] args) {return commands.tab(p, args);}

    /** Игро-специфичные строки /escape stats под базовыми (свои именованные счётчики). */
    @Override
    public List<Component> statsLines(Player viewer, StatsService.Row row)
    {
        return List.of(
            Msg.get("stats.line-deaths", Msg.ph("n", row.counter("deaths"))),
            Msg.get("stats.line-games", Msg.ph("n", row.counter("games_played"))),
            Msg.get("stats.line-mvp", Msg.ph("n", row.counter("mvp_games"))),
            Msg.get("stats.line-quests", Msg.ph("n", row.counter("quests_completed"))),
            Msg.get("stats.line-trades", Msg.ph("n", row.counter("trades_completed"))),
            Msg.get("stats.line-ores", Msg.ph("n", row.counter("ores_mined"))),
            Msg.get("stats.line-best-kills", Msg.ph("n", row.counter("best_game_kills"))),
            Msg.get("stats.line-last-kills", Msg.ph("n", row.counter("last_game_kills"))));
    }
}
