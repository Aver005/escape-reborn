package me.aver005.escape.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/** Хелперы создания предметов. */
public final class Items
{
    private Items() {}

    /** Убирает автокурсив кастомных имён/лора. */
    public static Component flat(Component c)
    {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack named(Material mat, Component name, List<Component> lore)
    {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (name != null) {meta.displayName(flat(name));}
        if (lore != null && !lore.isEmpty())
        {
            List<Component> flatLore = new ArrayList<>();
            for (Component line : lore) {flatLore.add(flat(line));}
            meta.lore(flatLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack named(Material mat, Component name)
    {
        return named(mat, name, null);
    }

    public static ItemStack special(Material mat, Component name, List<Component> lore, String specialTag)
    {
        ItemStack item = named(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.SPECIAL_ITEM, PersistentDataType.STRING, specialTag);
        item.setItemMeta(meta);
        return item;
    }

    public static String specialTag(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return null;}
        return item.getItemMeta().getPersistentDataContainer().get(Keys.SPECIAL_ITEM, PersistentDataType.STRING);
    }

    public static boolean isSpecial(ItemStack item, String tag)
    {
        return tag.equals(specialTag(item));
    }

    /** Панель-заполнитель без имени. */
    public static ItemStack filler(Material mat)
    {
        return named(mat, Component.space());
    }

    /**
     * Навести компас-проводник игрока на цель (lodestone-трекинг без маяка).
     * false — компаса в инвентаре нет (выпал при смерти и т.п.).
     */
    public static boolean pointAssistantCompass(Player p, Location target)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (!isSpecial(item, "assistant")) {continue;}
            if (!(item.getItemMeta() instanceof CompassMeta meta)) {continue;}
            meta.setLodestoneTracked(false);
            meta.setLodestone(target);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    /** Сколько предметов материала в инвентаре игрока. */
    public static int countMaterial(Player p, Material mat)
    {
        int amount = 0;
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item != null && item.getType() == mat) {amount += item.getAmount();}
        }
        return amount;
    }

    /**
     * Предмет из рукописной YML-спеки (loot.yml / traders.yml). Поля (все,
     * кроме type, опциональны; лишние ключи вроде price/weight игнорируются):
     *   type: SPLASH_POTION              — материал
     *   amount: 1                        — количество
     *   name: "<dark_gray>Зелье слепоты" — имя (MiniMessage)
     *   lore: ["<gray>строка", ...]      — лор (MiniMessage)
     *   potion: POISON                   — базовый тип зелья/стрелы (PotionType)
     *   effects: [{type: blindness, seconds: 8, amplifier: 0}] — кастом-эффекты
     *   enchants: {power: 2}             — зачарования (ванильные id)
     *   color: "#8B4513" | RED           — кожаная броня / цвет зелья (DyeColor)
     *   trim: {pattern: sentry, material: iron} — отделка брони
     *   damage: 50                       — фикс. урон прочности (wear не трогает)
     *   unbreakable: true                — неломаемый
     *   skull-owner: Aver005             — голова игрока
     *   firework-power: 2                — дальность фейерверка
     * null — спека битая (нет/неизвестен type).
     */
    public static ItemStack fromSpec(Map<?, ?> spec)
    {
        if (!(spec.get("type") instanceof String typeName)) {return null;}
        Material mat = Material.matchMaterial(typeName);
        if (mat == null || mat.isAir()) {return null;}
        int amount = spec.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {return item;}

        if (meta instanceof PotionMeta potionMeta)
        {
            if (spec.get("potion") instanceof String potionName)
            {
                try {potionMeta.setBasePotionType(PotionType.valueOf(potionName.toUpperCase(Locale.ROOT)));}
                catch (IllegalArgumentException ignored) {}
            }
            if (spec.get("effects") instanceof List<?> effects)
            {
                for (Object raw : effects)
                {
                    if (!(raw instanceof Map<?, ?> eff)) {continue;}
                    if (!(eff.get("type") instanceof String effName)) {continue;}
                    PotionEffectType type = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.MOB_EFFECT)
                        .get(NamespacedKey.minecraft(effName.toLowerCase(Locale.ROOT)));
                    if (type == null) {continue;}
                    int seconds = eff.get("seconds") instanceof Number n ? Math.max(1, n.intValue()) : 10;
                    int amplifier = eff.get("amplifier") instanceof Number n ? Math.max(0, n.intValue()) : 0;
                    potionMeta.addCustomEffect(new PotionEffect(type, seconds * 20, amplifier), true);
                }
            }
        }

        Color color = parseColor(spec.get("color"));
        if (color != null)
        {
            if (meta instanceof LeatherArmorMeta leather) {leather.setColor(color);}
            else if (meta instanceof PotionMeta potionMeta) {potionMeta.setColor(color);}
        }

        if (meta instanceof ArmorMeta armor && spec.get("trim") instanceof Map<?, ?> trim
            && trim.get("pattern") instanceof String patternName
            && trim.get("material") instanceof String materialName)
        {
            TrimPattern pattern = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_PATTERN)
                .get(NamespacedKey.minecraft(patternName.toLowerCase(Locale.ROOT)));
            TrimMaterial material = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.TRIM_MATERIAL)
                .get(NamespacedKey.minecraft(materialName.toLowerCase(Locale.ROOT)));
            if (pattern != null && material != null) {armor.setTrim(new ArmorTrim(material, pattern));}
        }

        if (meta instanceof SkullMeta skull && spec.get("skull-owner") instanceof String owner)
        {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
        }

        if (meta instanceof FireworkMeta firework && spec.get("firework-power") instanceof Number n)
        {
            firework.setPower(Math.max(0, Math.min(255, n.intValue())));
        }

        if (meta instanceof Damageable damageable && spec.get("damage") instanceof Number n
            && mat.getMaxDurability() > 0)
        {
            damageable.setDamage(Math.max(0, Math.min(mat.getMaxDurability() - 1, n.intValue())));
        }

        if (Boolean.TRUE.equals(spec.get("unbreakable"))) {meta.setUnbreakable(true);}

        if (spec.get("name") instanceof String nameRaw)
        {
            meta.displayName(flat(Msg.mm(nameRaw)));
        }
        if (spec.get("lore") instanceof List<?> loreRaw)
        {
            List<Component> lore = new ArrayList<>();
            for (Object line : loreRaw) {lore.add(flat(Msg.mm(String.valueOf(line))));}
            meta.lore(lore);
        }
        item.setItemMeta(meta);

        if (spec.get("enchants") instanceof Map<?, ?> enchants)
        {
            for (Map.Entry<?, ?> entry : enchants.entrySet())
            {
                Enchantment ench = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT)));
                if (ench == null || !(entry.getValue() instanceof Number level)) {continue;}
                item.addUnsafeEnchantment(ench, Math.max(1, level.intValue()));
            }
        }
        return item;
    }

    /** "#RRGGBB" или имя DyeColor (RED, LIME...). null — не распознан. */
    private static Color parseColor(Object raw)
    {
        if (!(raw instanceof String s) || s.isEmpty()) {return null;}
        if (s.startsWith("#"))
        {
            try {return Color.fromRGB(Integer.parseInt(s.substring(1), 16));}
            catch (NumberFormatException e) {return null;}
        }
        try {return DyeColor.valueOf(s.toUpperCase(Locale.ROOT)).getColor();}
        catch (IllegalArgumentException e) {return null;}
    }

    /** Изъять amount предметов материала из инвентаря. */
    public static void takeMaterial(Player p, Material mat, int amount)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (amount <= 0) {return;}
            if (item == null || item.getType() != mat) {continue;}
            int take = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - take);
            amount -= take;
        }
    }
}
