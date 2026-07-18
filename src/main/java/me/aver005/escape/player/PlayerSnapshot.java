package me.aver005.escape.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/**
 * Снапшот состояния игрока перед входом в мини-игру.
 * Пишется на диск (snapshots/<uuid>.yml) — переживает рестарт сервера.
 */
public final class PlayerSnapshot
{
    private PlayerSnapshot() {}

    private static File file(JavaPlugin plugin, java.util.UUID uuid)
    {
        return new File(new File(plugin.getDataFolder(), "snapshots"), uuid + ".yml");
    }

    public static boolean exists(JavaPlugin plugin, java.util.UUID uuid)
    {
        return file(plugin, uuid).exists();
    }

    public static void save(JavaPlugin plugin, Player p)
    {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("location", p.getLocation());
        cfg.set("inventory", Arrays.asList(p.getInventory().getContents()));
        cfg.set("health", p.getHealth());
        cfg.set("food", p.getFoodLevel());
        cfg.set("saturation", (double) p.getSaturation());
        cfg.set("level", p.getLevel());
        cfg.set("exp", (double) p.getExp());
        cfg.set("gamemode", p.getGameMode().name());
        cfg.set("allow-flight", p.getAllowFlight());
        cfg.set("flying", p.isFlying());
        cfg.set("walk-speed", (double) p.getWalkSpeed());
        cfg.set("fly-speed", (double) p.getFlySpeed());
        cfg.set("effects", new ArrayList<>(p.getActivePotionEffects()));
        File f = file(plugin, p.getUniqueId());
        f.getParentFile().mkdirs();
        try {cfg.save(f);}
        catch (IOException e) {plugin.getLogger().severe("Failed to save snapshot for " + p.getName() + ": " + e.getMessage());}
    }

    /** Полностью очищает игрока (вход в лобби). */
    public static void clear(Player p)
    {
        p.getInventory().clear();
        p.setFireTicks(0);
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setGameMode(GameMode.ADVENTURE);
        p.setLevel(60); // пасхалка оригинала: в лобби все 60-го уровня
        p.setExp(0f);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setWalkSpeed(0.2f);
        p.setFlySpeed(0.1f);
        for (PotionEffect effect : p.getActivePotionEffects()) {p.removePotionEffect(effect.getType());}
    }

    /** Восстанавливает игрока и удаляет снапшот. Возвращает false, если снапшота нет. */
    public static boolean restore(JavaPlugin plugin, Player p)
    {
        File f = file(plugin, p.getUniqueId());
        if (!f.exists()) {return false;}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);

        clear(p);

        Location loc = cfg.getLocation("location");
        if (loc != null && loc.getWorld() != null) {p.teleport(loc);}

        List<?> inv = cfg.getList("inventory");
        if (inv != null)
        {
            ItemStack[] contents = new ItemStack[p.getInventory().getSize()];
            for (int i = 0; i < contents.length && i < inv.size(); i++)
            {
                Object o = inv.get(i);
                contents[i] = o instanceof ItemStack is ? is : null;
            }
            p.getInventory().setContents(contents);
        }

        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHealth != null ? maxHealth.getValue() : 20.0;
        p.setHealth(Math.min(cap, cfg.getDouble("health", 20.0)));
        p.setFoodLevel(cfg.getInt("food", 20));
        p.setSaturation((float) cfg.getDouble("saturation", 5.0));
        p.setLevel(cfg.getInt("level", 0));
        p.setExp((float) cfg.getDouble("exp", 0.0));
        try {p.setGameMode(GameMode.valueOf(cfg.getString("gamemode", "SURVIVAL")));}
        catch (IllegalArgumentException e) {p.setGameMode(GameMode.SURVIVAL);}
        p.setAllowFlight(cfg.getBoolean("allow-flight", false));
        if (p.getAllowFlight()) {p.setFlying(cfg.getBoolean("flying", false));}
        p.setWalkSpeed((float) cfg.getDouble("walk-speed", 0.2));
        p.setFlySpeed((float) cfg.getDouble("fly-speed", 0.1));
        List<?> effects = cfg.getList("effects");
        if (effects != null)
        {
            for (Object o : effects)
            {
                if (o instanceof PotionEffect effect) {p.addPotionEffect(effect);}
            }
        }

        f.delete();
        return true;
    }
}
