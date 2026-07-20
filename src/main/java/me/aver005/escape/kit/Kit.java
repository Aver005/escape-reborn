package me.aver005.escape.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aver005.escape.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Стартовый набор («каст»): доп. предметы поверх системных (вилка/помощник/блок
 * возрождения) плюс своё стартовое золото. Хранится двумя способами и оба
 * читаются {@link #load}: глобальный kits.yml — рукописный формат предметов
 * ({@link Items#fromSpec}), копия арены (arenas/&lt;id&gt;/kits.yml) —
 * сериализованные ItemStack (как loot после GUI-редактора).
 */
public class Kit
{
    private final String id;
    private String nameRaw;             // MiniMessage — имя в меню
    private Material icon = Material.CHEST;
    private int gold = -1;              // -1 = использовать start-gold арены
    private List<String> loreRaw = new ArrayList<>();  // MiniMessage — лор в меню
    private final List<ItemStack> items = new ArrayList<>();

    public Kit(String id) {this.id = id;}

    // ===== persistence =====

    public static Kit load(String id, ConfigurationSection sec)
    {
        Kit kit = new Kit(id);
        kit.nameRaw = sec.getString("name", id);
        Material icon = Material.matchMaterial(sec.getString("icon", "CHEST"));
        kit.icon = icon != null ? icon : Material.CHEST;
        kit.gold = sec.getInt("gold", -1);
        kit.loreRaw = new ArrayList<>(sec.getStringList("lore"));
        for (Map<?, ?> entry : sec.getMapList("items"))
        {
            ItemStack item;
            if (entry.get("type") instanceof String)
            {
                item = Items.fromSpec(entry);
            }
            else
            {
                item = readItem(entry.get("item"));
            }
            if (item != null) {kit.items.add(item);}
        }
        return kit;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("name", nameRaw);
        sec.set("icon", icon.name());
        sec.set("gold", gold);
        sec.set("lore", loreRaw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ItemStack item : items) {list.add(Map.of("item", item.serialize()));}
        sec.set("items", list);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack readItem(Object raw)
    {
        if (raw instanceof ItemStack is) {return is;}
        if (raw instanceof Map<?, ?> map) {return ItemStack.deserialize((Map<String, Object>) map);}
        return null;
    }

    /** Глубокая копия под новым id — для «копии глобального каста на арену». */
    public Kit copy(String newId)
    {
        Kit kit = new Kit(newId);
        kit.nameRaw = nameRaw;
        kit.icon = icon;
        kit.gold = gold;
        kit.loreRaw = new ArrayList<>(loreRaw);
        for (ItemStack item : items) {kit.items.add(item.clone());}
        return kit;
    }

    // ===== выдача =====

    /** Выдать предметы каста: броня — в слоты (если пусто), остальное — в инвентарь. */
    public void apply(Player p)
    {
        PlayerInventory inv = p.getInventory();
        for (ItemStack item : items)
        {
            ItemStack copy = item.clone();
            switch (armorSlot(copy.getType()))
            {
                case HEAD -> {if (empty(inv.getHelmet())) {inv.setHelmet(copy);} else {inv.addItem(copy);}}
                case CHEST -> {if (empty(inv.getChestplate())) {inv.setChestplate(copy);} else {inv.addItem(copy);}}
                case LEGS -> {if (empty(inv.getLeggings())) {inv.setLeggings(copy);} else {inv.addItem(copy);}}
                case FEET -> {if (empty(inv.getBoots())) {inv.setBoots(copy);} else {inv.addItem(copy);}}
                default -> inv.addItem(copy);
            }
        }
    }

    private static boolean empty(ItemStack slot)
    {
        return slot == null || slot.getType().isAir();
    }

    private enum Slot {HEAD, CHEST, LEGS, FEET, NONE}

    /** Определить слот брони по материалу (кожа/железо/кольчуга/золото/алмаз/незерит + тыква/череп/элитры). */
    private static Slot armorSlot(Material mat)
    {
        String name = mat.name().toUpperCase(Locale.ROOT);
        if (name.endsWith("_HELMET") || mat == Material.CARVED_PUMPKIN
            || name.endsWith("_HEAD") || name.endsWith("_SKULL")) {return Slot.HEAD;}
        if (name.endsWith("_CHESTPLATE") || mat == Material.ELYTRA) {return Slot.CHEST;}
        if (name.endsWith("_LEGGINGS")) {return Slot.LEGS;}
        if (name.endsWith("_BOOTS")) {return Slot.FEET;}
        return Slot.NONE;
    }

    // ===== accessors =====

    public String getId() {return id;}
    public String getNameRaw() {return nameRaw;}
    public void setNameRaw(String v) {this.nameRaw = v;}
    public Material getIcon() {return icon;}
    public void setIcon(Material v) {this.icon = v;}
    public int getGold() {return gold;}
    public void setGold(int v) {this.gold = v;}
    public List<String> getLoreRaw() {return loreRaw;}
    public List<ItemStack> getItems() {return items;}
}
