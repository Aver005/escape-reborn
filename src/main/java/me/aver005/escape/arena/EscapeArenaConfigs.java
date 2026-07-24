package me.aver005.escape.arena;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;

/**
 * Реестр игро-специфичных конфигов арен Escape ({@code plugins/Escape/game/<ID>.yml}).
 * Ленивая загрузка с кэшем; сбрасывается на reload.
 */
public final class EscapeArenaConfigs
{
    private final EscapePlugin plugin;
    private final Map<String, EscapeArenaConfig> cache = new HashMap<>();

    public EscapeArenaConfigs(EscapePlugin plugin) {this.plugin = plugin;}

    private File fileOf(String arenaId)
    {
        return new File(new File(plugin.getDataFolder(), "game"), arenaId.toUpperCase(Locale.ROOT) + ".yml");
    }

    public EscapeArenaConfig of(String arenaId)
    {
        String id = arenaId.toUpperCase(Locale.ROOT);
        return cache.computeIfAbsent(id, k -> EscapeArenaConfig.load(k, fileOf(k)));
    }

    public EscapeArenaConfig of(Arena arena) {return of(arena.getId());}

    public void save(String arenaId) {of(arenaId).save(fileOf(arenaId));}

    public void save(Arena arena) {save(arena.getId());}

    /** Удалить конфиг арены (при удалении самой арены). */
    public void remove(String arenaId)
    {
        String id = arenaId.toUpperCase(Locale.ROOT);
        cache.remove(id);
        File f = fileOf(id);
        if (f.exists()) {f.delete();}
    }

    public void clear() {cache.clear();}
}
