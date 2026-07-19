package me.aver005.escape.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Продвинутое логирование (/escape debuglog on|off|save|clear).
 * Каждое игровое действие пишется в консоль сервера и в собственный кольцевой
 * буфер; буфер админ в любой момент сохраняет в plugins/Escape/logs/.
 *
 * Состояние живёт в config.yml (debug-log.enabled) — переживает рестарт.
 * В лог уходит только ASCII (инвариант проекта): всё вне 0x20..0x7E
 * заменяется на '?', чтобы русские ники и названия миров не превращали
 * Windows-консоль в кашу.
 */
public final class DebugLog
{
    /** Подсистема-источник записи: по ней удобно грепать сохранённый лог. */
    public enum Cat
    {
        SESSION, PLAYER, COMBAT, CHEST, CONTRACT, THEME, RESPAWN, GUARD, EVENT, WORLD, SHOP, MECH, ADMIN
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MIN_BUFFER = 100;

    private static final Deque<String> BUFFER = new ArrayDeque<>();

    private static JavaPlugin plugin;
    private static volatile boolean enabled = false;
    private static volatile boolean console = true;
    private static volatile int bufferLimit = 20000;
    private static long dropped = 0; // строк вытеснено лимитом буфера

    private DebugLog() {}

    public static void init(JavaPlugin pl)
    {
        plugin = pl;
        reload();
    }

    /** Перечитать настройки из config.yml (в том числе из /escape reload). */
    public static void reload()
    {
        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("debug-log.enabled", false);
        console = cfg.getBoolean("debug-log.console", true);
        bufferLimit = Math.max(MIN_BUFFER, cfg.getInt("debug-log.buffer-lines", 20000));
        synchronized (BUFFER) {trimLocked();}
    }

    // ===== состояние =====

    public static boolean on() {return enabled;}
    public static boolean toConsole() {return console;}
    public static int limit() {return bufferLimit;}
    public static long droppedLines() {return dropped;}

    public static int buffered()
    {
        synchronized (BUFFER) {return BUFFER.size();}
    }

    /** Переключение с сохранением состояния в config.yml. */
    public static void setEnabled(boolean value)
    {
        if (!value) {log(Cat.ADMIN, "debuglog off");}
        enabled = value;
        if (value) {log(Cat.ADMIN, "debuglog on (buffer limit %d, console %b)", bufferLimit, console);}

        plugin.getConfig().set("debug-log.enabled", value);
        plugin.saveConfig();
    }

    /** Очистка буфера. Возвращает, сколько строк выброшено. */
    public static int clear()
    {
        synchronized (BUFFER)
        {
            int size = BUFFER.size();
            BUFFER.clear();
            dropped = 0;
            return size;
        }
    }

    // ===== запись =====

    /**
     * Запись действия. format/args — как в String.format; форматирование
     * происходит только при включённом логировании.
     */
    public static void log(Cat cat, String format, Object... args)
    {
        if (!enabled) {return;}
        String line;
        try {line = ascii("[" + cat.name() + "] " + (args.length == 0 ? format : String.format(format, args)));}
        catch (RuntimeException e) {line = "[" + cat.name() + "] format-error: " + ascii(format);}

        synchronized (BUFFER)
        {
            BUFFER.addLast(LocalDateTime.now().format(TIME) + " " + line);
            trimLocked();
        }
        if (console && plugin != null) {plugin.getLogger().info("[DBG]" + line);}
    }

    private static void trimLocked()
    {
        while (BUFFER.size() > bufferLimit)
        {
            BUFFER.removeFirst();
            dropped++;
        }
    }

    // ===== сохранение =====

    /**
     * Сохранить буфер в plugins/Escape/logs/debug-<дата>.log.
     * null — не удалось записать файл (причина уходит в консоль).
     */
    public static File save()
    {
        List<String> lines;
        long lost;
        synchronized (BUFFER)
        {
            lines = new ArrayList<>(BUFFER);
            lost = dropped;
        }

        File dir = new File(plugin.getDataFolder(), "logs");
        if (!dir.isDirectory() && !dir.mkdirs())
        {
            plugin.getLogger().warning("Cannot create debug log directory: " + dir.getPath());
            return null;
        }

        File file = new File(dir, "debug-" + LocalDateTime.now().format(STAMP) + ".log");
        List<String> out = new ArrayList<>(lines.size() + 6);
        out.add("# Escape debug log");
        out.add("# saved:  " + LocalDateTime.now());
        out.add("# server: " + Bukkit.getVersion());
        out.add("# lines:  " + lines.size() + " (dropped by buffer limit: " + lost + ")");
        out.add("");
        out.addAll(lines);

        try {Files.write(file.toPath(), out, StandardCharsets.UTF_8);}
        catch (IOException e)
        {
            plugin.getLogger().warning("Failed to save debug log: " + e.getMessage());
            return null;
        }
        return file;
    }

    // ===== форматирование значений =====

    /** Координаты для лога: world:x,y,z. */
    public static String at(Location loc)
    {
        if (loc == null) {return "-";}
        String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        return world + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /** Предмет для лога: MATERIALxN (имена не берём — они русские и в PDC их нет). */
    public static String item(ItemStack item)
    {
        if (item == null || item.getType().isAir()) {return "-";}
        return item.getType().name() + "x" + item.getAmount();
    }

    /** Инвариант: в лог сервера идёт только ASCII. */
    private static String ascii(String s)
    {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7E)
            {
                if (sb != null) {sb.append(c);}
                continue;
            }
            if (sb == null) {sb = new StringBuilder(s.length()).append(s, 0, i);}
            sb.append('?');
        }
        return sb == null ? s : sb.toString();
    }
}
