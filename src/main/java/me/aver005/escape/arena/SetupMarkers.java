package me.aver005.escape.arena;

import java.util.ArrayList;
import java.util.List;

import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Блоки-подсказки настроенных точек арены: стоят вне матча, чтобы админ видел,
 * где уже что размещено. Перед стартом матча снимаются (игроки не должны
 * появиться внутри стекла), после матча возвращаются.
 *
 * Спавн и точка жителя — колонна из двух стёкол на месте фигуры; точка сундука —
 * настоящий сундук (его содержимое игрой не используется и чистится).
 */
public final class SetupMarkers
{
    public static final Material SPAWN = Material.GRAY_STAINED_GLASS;
    public static final Material FINAL_SPAWN = Material.BLACK_STAINED_GLASS;
    public static final Material TRADER = Material.PINK_STAINED_GLASS;

    private SetupMarkers() {}

    // ===== предметы-маркеры (выдаются админу для расстановки точек) =====

    /**
     * Предмет-маркер точки: ПКМ по блоку размечает точку типа {@code type} на арене.
     * PDC — MARKER_TYPE/MARKER_ARENA (+ MARKER_EXTRA, если {@code extra} задан:
     * имя рычага, id жителя). Используется и командой, и хабом настройки арены.
     */
    public static ItemStack markerItem(Arena arena, String type, Material material, String extra)
    {
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
        return item;
    }

    /** Жезл ломаемых блоков арены (ПКМ помечает блок, что вернётся после матча). */
    public static ItemStack breakWand(Arena arena)
    {
        ItemStack wand = Items.special(Material.IRON_AXE, Msg.get("breakable.wand-name"),
            Msg.getList("breakable.wand-lore", Msg.ph("arena", arena.getId())), "breakwand");
        ItemMeta meta = wand.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.MARKER_ARENA, PersistentDataType.STRING, arena.getId());
        wand.setItemMeta(meta);
        return wand;
    }

    // ===== установка =====

    /** Расставить блоки-подсказки всех точек арены. Возвращает число обработанных точек. */
    public static int placeAll(Arena arena)
    {
        int count = 0;
        for (Location loc : arena.getSpawns()) {count += placePillar(loc, SPAWN) ? 1 : 0;}
        for (Location loc : arena.getFinalSpawns()) {count += placePillar(loc, FINAL_SPAWN) ? 1 : 0;}
        for (Location loc : arena.getTraderSpots().keySet()) {count += placePillar(loc, TRADER) ? 1 : 0;}
        for (Location loc : arena.getChestSpots().keySet()) {count += placeChest(loc, arena.getChestFacing(loc)) ? 1 : 0;}
        return count;
    }

    /** Блоки-подсказки одной точки (тип маркера как в MARKER_TYPE). */
    public static void placePoint(Arena arena, Location loc, String markerType)
    {
        switch (markerType)
        {
            case "spawn" -> placePillar(loc, SPAWN);
            case "finalspawn" -> placePillar(loc, FINAL_SPAWN);
            case "villager" -> placePillar(loc, TRADER);
            case "chest" -> placeChest(loc, arena.getChestFacing(loc));
            default -> {}
        }
    }

    private static boolean placePillar(Location loc, Material material)
    {
        if (loc == null || loc.getWorld() == null) {return false;}
        setIfFree(loc.getBlock(), material);
        setIfFree(loc.getBlock().getRelative(0, 1, 0), material);
        return true;
    }

    private static boolean placeChest(Location loc, BlockFace facing)
    {
        if (loc == null || loc.getWorld() == null) {return false;}
        Block block = loc.getBlock();
        if (block.getType() != Material.CHEST)
        {
            if (!isFree(block)) {return false;}
            block.setType(Material.CHEST);
        }
        applyFacing(block, facing);
        clearChest(block);
        return true;
    }

    /** Развернуть сундук в сохранённую сторону (иначе setType всегда даёт NORTH). */
    private static void applyFacing(Block block, BlockFace facing)
    {
        if (facing == null) {return;}
        if (block.getBlockData() instanceof Directional dir && dir.getFaces().contains(facing))
        {
            dir.setFacing(facing);
            block.setBlockData(dir, false);
        }
    }

    /** Не затираем карту: маркер встаёт только в воздух/заменяемый блок. */
    private static void setIfFree(Block block, Material material)
    {
        if (block.getType() == material) {return;}
        if (!isFree(block)) {return;}
        block.setType(material);
    }

    private static boolean isFree(Block block)
    {
        return block.getType().isAir() || block.isReplaceable();
    }

    // ===== снятие перед матчем =====

    /**
     * Убрать подсказки перед стартом матча: стёкла спавнов/жителей снимаются,
     * сундуки-маркеры остаются блоками карты, но их содержимое чистится
     * (лут точки настройки в игре не участвует).
     */
    public static void clearForMatch(Arena arena)
    {
        for (Location loc : arena.getSpawns()) {clearPillar(loc, SPAWN);}
        for (Location loc : arena.getFinalSpawns()) {clearPillar(loc, FINAL_SPAWN);}
        for (Location loc : arena.getTraderSpots().keySet()) {clearPillar(loc, TRADER);}
        for (Location loc : arena.getChestSpots().keySet())
        {
            if (loc != null && loc.getWorld() != null) {clearChest(loc.getBlock());}
        }
    }

    private static void clearPillar(Location loc, Material material)
    {
        if (loc == null || loc.getWorld() == null) {return;}
        Block block = loc.getBlock();
        if (block.getType() == material) {block.setType(Material.AIR);}
        Block upper = block.getRelative(0, 1, 0);
        if (upper.getType() == material) {upper.setType(Material.AIR);}
    }

    private static void clearChest(Block block)
    {
        if (block.getState() instanceof Chest chest) {chest.getInventory().clear();}
    }

    /**
     * Вторая половина многоблочной структуры (дверь/высокое растение — по половине;
     * кровать — по части и направлению) или null для обычного блока. Нужно, чтобы
     * при пометке/сломе ломаемого блока запомнить и вернуть ОБЕ половины.
     */
    public static Block structurePartner(Block block)
    {
        BlockData data = block.getBlockData();
        if (data instanceof Bed bed)
        {
            BlockFace face = bed.getPart() == Bed.Part.HEAD
                ? bed.getFacing().getOppositeFace() : bed.getFacing();
            return block.getRelative(face);
        }
        if (data instanceof Bisected bisected)
        {
            return block.getRelative(bisected.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
        }
        return null;
    }

    // ===== слом маркера =====

    /**
     * Тип точки, которой принадлежит блок (сам блок или колонна над точкой),
     * либо null. Значения совпадают с MARKER_TYPE и ключами marker-types.
     */
    public static String pointTypeAt(Arena arena, Location broken)
    {
        Location base = broken.getBlock().getLocation();
        Location below = base.clone().subtract(0, 1, 0);
        if (arena.getSpawns().contains(base) || arena.getSpawns().contains(below)) {return "spawn";}
        if (arena.getFinalSpawns().contains(base) || arena.getFinalSpawns().contains(below)) {return "finalspawn";}
        if (arena.getTraderSpots().containsKey(base) || arena.getTraderSpots().containsKey(below)) {return "villager";}
        if (arena.getChestSpots().containsKey(base)) {return "chest";}
        return null;
    }

    /**
     * Удалить точку, которой принадлежит сломанный блок, и убрать её остатки.
     * Возвращает тип удалённой точки или null. Арену сохраняет вызывающий.
     */
    public static String removeAt(Arena arena, Location broken)
    {
        String type = pointTypeAt(arena, broken);
        if (type == null) {return null;}

        Location base = broken.getBlock().getLocation();
        Location below = base.clone().subtract(0, 1, 0);
        switch (type)
        {
            case "spawn" ->
            {
                Location point = arena.getSpawns().contains(base) ? base : below;
                arena.getSpawns().remove(point);
                clearPillar(point, SPAWN);
            }
            case "finalspawn" ->
            {
                Location point = arena.getFinalSpawns().contains(base) ? base : below;
                arena.getFinalSpawns().remove(point);
                clearPillar(point, FINAL_SPAWN);
            }
            case "villager" ->
            {
                Location point = arena.getTraderSpots().containsKey(base) ? base : below;
                arena.getTraderSpots().remove(point);
                clearPillar(point, TRADER);
            }
            case "chest" ->
            {
                arena.getChestSpots().remove(base);
                arena.getChestFacings().remove(base);
                clearChest(base.getBlock()); // содержимое точки настройки не выпадает
            }
            default -> {return null;}
        }
        return type;
    }
}
