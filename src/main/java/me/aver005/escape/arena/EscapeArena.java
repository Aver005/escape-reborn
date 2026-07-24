package me.aver005.escape.arena;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import ru.kiviuly.mg.api.arena.Arena;

/**
 * Игро-специфичный слой поверх платформенной {@link Arena}.
 *
 * <p>Платформа знает об арене только общее (мир, лобби, спавны, лимиты, тайминги) и
 * держит остальное в двух универсальных «карманах»: числа ({@code getSetting}) и
 * именованные наборы точек с ярлыками ({@code spots}). Escape-специфика (сколько
 * торговцев, где руда, куда повёрнут сундук) живёт именно там, а этот класс даёт к
 * ней привычные типизированные обращения.</p>
 *
 * <p>Наборы точек возвращаются ЖИВЫМИ представлениями: {@code add/put/remove} пишут
 * прямо в арену, поэтому старый код продолжает работать без изменения логики.
 * После правок арену надо сохранить через {@code core.arenas().save(arena)}.</p>
 */
public final class EscapeArena
{
    // ===== ключи «кармана чисел» =====
    private static final String TRADER_COUNT = "trader-count";
    private static final String TABLE_COUNT = "table-count";
    private static final String EVENT_INTERVAL = "event-interval";
    private static final String SALARY_INTERVAL = "salary-interval";
    private static final String SALARY_GOLD = "salary-gold";
    private static final String GLOW_BEFORE_END = "glow-before-end";
    private static final String GLOW_BONUS_GOLD = "glow-bonus-gold";
    private static final String FORK_USES = "fork-uses";
    private static final String START_GOLD = "start-gold";
    private static final String WEAR_MIN = "wear-min";
    private static final String WEAR_MAX = "wear-max";
    private static final String DYNAMIC_CHESTS = "dynamic-chests";
    private static final String CONTRACTS_MIN_ARENA = "contracts-min-per-arena";
    private static final String CONTRACTS_MAX_ARENA = "contracts-max-per-arena";
    private static final String CONTRACTS_MIN_CHEST = "contracts-min-per-chest";
    private static final String CONTRACTS_MAX_CHEST = "contracts-max-per-chest";

    // ===== группы точек =====
    /** Точка сундука -> id категорий лута (форма совпадает с платформенной). */
    public static final String G_CHEST = "chest";
    /** Точка сундука -> сторона, куда он повёрнут. */
    public static final String G_CHEST_FACING = "chest-facing";
    public static final String G_FINAL = "final";
    public static final String G_TABLE = "table";
    public static final String G_ORE = "ore";
    public static final String G_LEVER = "lever";      // ярлык — имя локации
    public static final String G_TRADER = "trader";    // ярлык — тип торговца
    public static final String G_BREAKABLE = "breakable";

    private EscapeArena() {}

    // ===== числа =====

    public static int traderCount(Arena a) {return a.getSetting(TRADER_COUNT, 32);}
    public static void setTraderCount(Arena a, int v) {a.setSetting(TRADER_COUNT, v);}

    public static int tableCount(Arena a) {return a.getSetting(TABLE_COUNT, 5);}
    public static void setTableCount(Arena a, int v) {a.setSetting(TABLE_COUNT, v);}

    public static int eventIntervalSeconds(Arena a) {return a.getSetting(EVENT_INTERVAL, 210);}
    public static void setEventIntervalSeconds(Arena a, int v) {a.setSetting(EVENT_INTERVAL, v);}

    public static int salaryIntervalSeconds(Arena a) {return a.getSetting(SALARY_INTERVAL, 600);}
    public static void setSalaryIntervalSeconds(Arena a, int v) {a.setSetting(SALARY_INTERVAL, v);}

    public static int salaryGold(Arena a) {return a.getSetting(SALARY_GOLD, 16);}
    public static void setSalaryGold(Arena a, int v) {a.setSetting(SALARY_GOLD, v);}

    public static int glowSecondsBeforeEnd(Arena a) {return a.getSetting(GLOW_BEFORE_END, 600);}
    public static void setGlowSecondsBeforeEnd(Arena a, int v) {a.setSetting(GLOW_BEFORE_END, v);}

    public static int glowBonusGold(Arena a) {return a.getSetting(GLOW_BONUS_GOLD, 18);}
    public static void setGlowBonusGold(Arena a, int v) {a.setSetting(GLOW_BONUS_GOLD, v);}

    public static int forkUses(Arena a) {return a.getSetting(FORK_USES, 1);}
    public static void setForkUses(Arena a, int v) {a.setSetting(FORK_USES, v);}

    public static int startGold(Arena a) {return a.getSetting(START_GOLD, 24);}
    public static void setStartGold(Arena a, int v) {a.setSetting(START_GOLD, v);}

    public static int wearMinPercent(Arena a) {return a.getSetting(WEAR_MIN, 40);}
    public static void setWearMinPercent(Arena a, int v) {a.setSetting(WEAR_MIN, v);}

    public static int wearMaxPercent(Arena a) {return a.getSetting(WEAR_MAX, 90);}
    public static void setWearMaxPercent(Arena a, int v) {a.setSetting(WEAR_MAX, v);}

    /** Платформенный «карман» хранит только числа, поэтому флаг — 0/1. */
    public static boolean dynamicChests(Arena a) {return a.getSetting(DYNAMIC_CHESTS, 0) != 0;}
    public static void setDynamicChests(Arena a, boolean v) {a.setSetting(DYNAMIC_CHESTS, v ? 1 : 0);}

    public static int contractsMinPerArena(Arena a) {return a.getSetting(CONTRACTS_MIN_ARENA, 4);}
    public static void setContractsMinPerArena(Arena a, int v) {a.setSetting(CONTRACTS_MIN_ARENA, v);}

    public static int contractsMaxPerArena(Arena a) {return a.getSetting(CONTRACTS_MAX_ARENA, 16);}
    public static void setContractsMaxPerArena(Arena a, int v) {a.setSetting(CONTRACTS_MAX_ARENA, v);}

    public static int contractsMinPerChest(Arena a) {return a.getSetting(CONTRACTS_MIN_CHEST, 0);}
    public static void setContractsMinPerChest(Arena a, int v) {a.setSetting(CONTRACTS_MIN_CHEST, v);}

    public static int contractsMaxPerChest(Arena a) {return a.getSetting(CONTRACTS_MAX_CHEST, 3);}
    public static void setContractsMaxPerChest(Arena a, int v) {a.setSetting(CONTRACTS_MAX_CHEST, v);}

    // ===== наборы точек (живые представления) =====

    /** Точка сундука -> id категорий лута. Форма совпадает с платформенной — отдаём как есть. */
    public static Map<Location, List<String>> chestSpots(Arena a) {return a.spots(G_CHEST);}

    /** Точка сундука -> сторона поворота. */
    public static Map<Location, BlockFace> chestFacings(Arena a) {return new FacingView(a);}

    /** Сторона поворота конкретного сундука (по умолчанию NORTH). */
    public static BlockFace chestFacing(Arena a, Location loc)
    {
        BlockFace f = chestFacings(a).get(loc);
        return f == null ? BlockFace.NORTH : f;
    }

    /**
     * Квоты торговцев: тип -> сколько их на арене. Это числа, поэтому живут в «кармане
     * чисел» под префиксом {@code trader-quota.<ТИП>}; представление — живое.
     */
    public static Map<String, Integer> traderQuotas(Arena a) {return new QuotaView(a);}

    /** Точка рычага -> имя локации. */
    public static Map<Location, String> levers(Arena a) {return new TagView(a, G_LEVER);}

    /** Точка торговца -> его тип. */
    public static Map<Location, String> traderSpots(Arena a) {return new TagView(a, G_TRADER);}

    public static List<Location> finalSpawns(Arena a) {return new PointList(a, G_FINAL);}
    public static List<Location> tableSpots(Arena a) {return new PointList(a, G_TABLE);}
    public static List<Location> oreSpots(Arena a) {return new PointList(a, G_ORE);}
    public static List<Location> breakables(Arena a) {return new PointList(a, G_BREAKABLE);}

    // ===== представления =====

    /** Набор точек как список: чтение — из арены, изменения пишутся в неё же. */
    private static final class PointList extends AbstractList<Location>
    {
        private final Arena arena;
        private final String group;

        PointList(Arena arena, String group) {this.arena = arena; this.group = group;}

        private List<Location> snapshot() {return new ArrayList<>(arena.spots(group).keySet());}

        @Override public int size() {return arena.spots(group).size();}
        @Override public Location get(int i) {return snapshot().get(i);}
        @Override public boolean add(Location loc) {arena.addSpot(group, loc, null); return true;}
        @Override public Location remove(int i) {Location l = get(i); arena.removeSpot(group, l); return l;}
        @Override public boolean remove(Object o) {return o instanceof Location l && arena.removeSpot(group, l);}
        @Override public boolean contains(Object o) {return o instanceof Location l && arena.spotAt(group, l) != null;}
        @Override public void clear() {arena.spots(group).clear();}
    }

    /** Квоты торговцев поверх «кармана чисел»: ключи с префиксом {@code trader-quota.}. */
    private static final class QuotaView extends AbstractMap<String, Integer>
    {
        private static final String PREFIX = "trader-quota.";
        private final Arena arena;

        QuotaView(Arena arena) {this.arena = arena;}

        @Override
        public Set<Entry<String, Integer>> entrySet()
        {
            Set<Entry<String, Integer>> out = new LinkedHashSet<>();
            for (Map.Entry<String, Integer> e : arena.getSettings().entrySet())
            {
                if (e.getKey().startsWith(PREFIX))
                {
                    out.add(new SimpleEntry<>(e.getKey().substring(PREFIX.length()), e.getValue()));
                }
            }
            return out;
        }

        @Override public Integer put(String key, Integer value) {return arena.getSettings().put(PREFIX + key, value);}
        @Override public Integer remove(Object key) {return arena.getSettings().remove(PREFIX + key);}
        @Override public Integer get(Object key) {return arena.getSettings().get(PREFIX + key);}
    }

    /** Набор точек как мапа «точка -> один ярлык». */
    private static class TagView extends AbstractMap<Location, String>
    {
        final Arena arena;
        final String group;

        TagView(Arena arena, String group) {this.arena = arena; this.group = group;}

        @Override
        public Set<Entry<Location, String>> entrySet()
        {
            Set<Entry<Location, String>> out = new LinkedHashSet<>();
            for (Map.Entry<Location, List<String>> e : arena.spots(group).entrySet())
            {
                List<String> tags = e.getValue();
                out.add(new SimpleEntry<>(e.getKey(), tags.isEmpty() ? null : tags.get(0)));
            }
            return out;
        }

        @Override
        public String put(Location key, String value)
        {
            List<String> prev = arena.spots(group).put(key, new ArrayList<>(List.of(value)));
            return prev == null || prev.isEmpty() ? null : prev.get(0);
        }

        @Override
        public String remove(Object key)
        {
            List<String> prev = arena.spots(group).remove(key);
            return prev == null || prev.isEmpty() ? null : prev.get(0);
        }

        @Override
        public String get(Object key)
        {
            List<String> tags = arena.spots(group).get(key);
            return tags == null || tags.isEmpty() ? null : tags.get(0);
        }
    }

    /** Тот же приём, но ярлык — сторона света. */
    private static final class FacingView extends AbstractMap<Location, BlockFace>
    {
        private final TagView inner;

        FacingView(Arena arena) {this.inner = new TagView(arena, G_CHEST_FACING);}

        private static BlockFace parse(String raw)
        {
            if (raw == null) {return BlockFace.NORTH;}
            try {return BlockFace.valueOf(raw);}
            catch (IllegalArgumentException e) {return BlockFace.NORTH;}
        }

        @Override
        public Set<Entry<Location, BlockFace>> entrySet()
        {
            Set<Entry<Location, BlockFace>> out = new LinkedHashSet<>();
            for (Entry<Location, String> e : inner.entrySet())
            {
                out.add(new SimpleEntry<>(e.getKey(), parse(e.getValue())));
            }
            return out;
        }

        @Override
        public BlockFace put(Location key, BlockFace value)
        {
            String prev = inner.put(key, value.name());
            return prev == null ? null : parse(prev);
        }

        @Override
        public BlockFace remove(Object key)
        {
            String prev = inner.remove(key);
            return prev == null ? null : parse(prev);
        }

        @Override
        public BlockFace get(Object key)
        {
            String raw = inner.get(key);
            return raw == null ? null : parse(raw);
        }
    }
}
