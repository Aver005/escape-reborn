package me.aver005.escape.arena;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.EscapeGame;
import me.aver005.escape.game.EscapeRules;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.arena.ArenaService;
import ru.kiviuly.mg.api.game.Match;

/**
 * Тонкий фасад Escape над реестром арен ядра ({@code core.arenas()}).
 *
 * <p>Арены и жизненный цикл матча ведёт платформа; здесь — только escape-удобства:
 * {@link #sessionOf(Player)}/{@link #sessionOf(Arena)} отдают {@link EscapeRules}
 * текущего матча (мост от {@link Match} к правилам), и сохранение арены пишет заодно
 * её игро-специфичный конфиг. Старый код Escape продолжает звать {@code plugin.arenas()}
 * почти без изменений.</p>
 */
public class ArenaManager
{
    private final EscapePlugin plugin;

    public ArenaManager(EscapePlugin plugin) {this.plugin = plugin;}

    private ArenaService core() {return plugin.core().arenas();}

    // ===== арены (делегаты в ядро; область видимости — только Escape) =====

    public Arena get(String id) {return core().get(EscapeGame.ID, id);}
    public boolean exists(String id) {return core().exists(EscapeGame.ID, id);}
    public Collection<Arena> all() {return core().all(EscapeGame.ID);}
    public Set<String> ids() {return core().ids(EscapeGame.ID);}

    /** Дефолты игры (torgovcy, зарплата, …) проставит EscapeGame.onArenaCreated. */
    public Arena create(String id, String worldName) {return core().create(id, worldName, EscapeGame.ID);}

    public void delete(String id)
    {
        Arena arena = get(id);
        if (arena == null) {return;}
        stop(arena);
        plugin.arenaConfigs().remove(id);
        core().delete(EscapeGame.ID, id);
    }

    /** Сохранить арену: платформенную часть — ядром, игро-конфиг — рядом. */
    public void save(Arena arena)
    {
        core().save(arena);
        plugin.arenaConfigs().save(arena);
    }

    /** Загрузка/оптовое сохранение — забота ядра; здесь no-op, чтобы не дублировать. */
    public void loadAll() {}
    public void saveAll() {}

    // ===== матч / правила =====

    /** Правила текущего матча игрока (или null). */
    public EscapeRules sessionOf(Player p)
    {
        Match m = core().sessionOf(p);
        return EscapeGame.rulesOf(m);
    }

    /** Правила текущего матча арены (или null). */
    public EscapeRules sessionOf(Arena arena)
    {
        return arena == null ? null : EscapeGame.rulesOf(arena.getSession());
    }

    public boolean inSession(Player p) {return core().inGame(p);}

    /** Привязку игрок→матч ведёт ядро — здесь no-op для совместимости старого кода. */
    public void bind(UUID player, EscapeRules session) {}
    public void unbind(UUID player) {}

    /** Вход игрока на арену (проверка мастера настройки + делегат в ядро). */
    public boolean join(Player p, Arena arena)
    {
        if (plugin.chestSetup().isActive(p) || plugin.chestSetup().isArenaBusy(arena))
        {
            ru.kiviuly.mg.api.util.Msg.send(p, "chestsetup.busy-join");
            return false;
        }
        core().join(p, arena);
        return core().inGame(p);
    }

    public void leave(Player p) {core().leave(p);}

    /** Досрочный старт матча админом. */
    public boolean forceStart(Arena arena) {return core().forceStart(arena);}

    /** Немедленно остановить и откатить матч. */
    public void stop(Arena arena) {core().stop(arena);}

    /** Остановить все матчи Escape (reload/disable). */
    public void stopAll()
    {
        for (Arena arena : all()) {stop(arena);}
    }
}
