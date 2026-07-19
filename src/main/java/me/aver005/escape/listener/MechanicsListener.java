package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.util.Msg;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Улучшенные ванильные механики — только внутри матча:
 * снежки и яйца наносят урон, удочка подтягивает игрока к рыбаку («крюк»).
 * Вне сессии всё работает по-ванильному.
 */
public class MechanicsListener implements Listener
{
    private final EscapePlugin plugin;

    public MechanicsListener(EscapePlugin plugin) {this.plugin = plugin;}

    // ===== снежки и яйца =====

    /**
     * Ванильный снежок/яйцо бьёт игрока на 0 урона, поэтому урон выдаём сами —
     * в ProjectileHitEvent, то есть ДО ванильного попадания. Оно после этого
     * гасится окном неуязвимости, так что урон не задваивается.
     * Источник THROWN: работает броня и наши обработчики урона (кровавая луна,
     * кредит убийце, фейковая смерть).
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e)
    {
        Projectile projectile = e.getEntity();
        double damage;
        if (projectile instanceof Snowball) {damage = configDouble("projectiles.snowball-damage", 2.0);}
        else if (projectile instanceof Egg) {damage = configDouble("projectiles.egg-damage", 3.0);}
        else {return;}
        if (damage <= 0) {return;}

        if (!(e.getHitEntity() instanceof Player victim)) {return;}
        if (!(projectile.getShooter() instanceof Player shooter)) {return;}
        if (victim.equals(shooter)) {return;}

        GameSession session = plugin.arenas().sessionOf(shooter);
        if (session == null || session != plugin.arenas().sessionOf(victim)) {return;}

        victim.damage(damage, DamageSource.builder(DamageType.THROWN)
            .withCausingEntity(shooter)
            .withDirectEntity(projectile)
            .build());
    }

    // ===== удочка-крюк =====

    /**
     * Пойманного удочкой игрока тянет к рыбаку. Сила рывка зависит от уровня
     * зачарования «Сила» (POWER) на удочке; без зачарования — слабый рывок.
     * Ванильный рывок применяется уже ПОСЛЕ события, поэтому свою скорость
     * выставляем следующим тиком — иначе ванилла добавит свою поверх.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e)
    {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {return;}
        if (!(e.getCaught() instanceof Player target)) {return;}

        Player fisher = e.getPlayer();
        GameSession session = plugin.arenas().sessionOf(fisher);
        if (session == null || session != plugin.arenas().sessionOf(target)) {return;}
        if (!session.isPlaying(fisher.getUniqueId()) || !session.isPlaying(target.getUniqueId())) {return;}

        double strength = hookStrength(rodInHand(fisher, e.getHand()));
        if (strength <= 0) {return;}

        Vector pull = fisher.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() < 1.0E-4) {return;}
        pull.normalize().multiply(strength);
        pull.setY(pull.getY() * 0.5 + 0.35); // дуга, иначе тащит по земле

        plugin.getServer().getScheduler().runTask(plugin, () ->
        {
            if (target.isOnline() && session.isPlaying(target.getUniqueId())) {target.setVelocity(pull);}
        });

        // сорвался с высоты после крюка — убийство засчитывается рыбаку
        session.recordDamager(target, fisher);
        Msg.send(target, "hook.pulled", Msg.ph("player", fisher.getName()));
        Msg.send(fisher, "hook.hooked", Msg.ph("player", target.getName()));
    }

    /** Удочка в той руке, которой закинули (getHand может быть null). */
    private ItemStack rodInHand(Player p, EquipmentSlot hand)
    {
        if (hand == EquipmentSlot.OFF_HAND) {return p.getInventory().getItemInOffHand();}
        return p.getInventory().getItemInMainHand();
    }

    private double hookStrength(ItemStack rod)
    {
        double base = configDouble("fishing-hook.base-strength", 0.55);
        double perLevel = configDouble("fishing-hook.power-strength-per-level", 0.30);
        double max = configDouble("fishing-hook.max-strength", 2.5);
        int level = rod == null || rod.getType().isAir() ? 0 : rod.getEnchantmentLevel(Enchantment.POWER);
        return Math.min(max, base + perLevel * level);
    }

    private double configDouble(String path, double fallback)
    {
        return plugin.getConfig().getDouble(path, fallback);
    }
}
