package me.aver005.escape.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * Оффлайн-стражи (docs/07-decisions.md §8): живой игрок вышел из игры —
 * на его месте 90 секунд стоит AFK-зомби в его экипировке.
 * Вернулся при живом зомби — продолжает как ни в чём не бывало.
 * Зомби убили — это смерть игрока (кредит убийце, дроп вещей).
 * Зомби истёк — вещи выпадают; дальше спасает только установленный блок
 * возрождения при возврате в общее окно (4 минуты от выхода).
 */
public class OfflineGuards
{
    private static class Guard
    {
        final UUID owner;
        final String ownerName;
        final List<ItemStack> items = new ArrayList<>();
        UUID zombieId;
        Location zombieAt;
        BukkitTask zombieTimer;
        BukkitTask deadlineTimer;
        boolean itemsDropped = false;

        Guard(UUID owner, String ownerName)
        {
            this.owner = owner;
            this.ownerName = ownerName;
        }
    }

    private final EscapePlugin plugin;
    private final GameSession session;
    private final Map<UUID, Guard> guards = new HashMap<>();

    public OfflineGuards(EscapePlugin plugin, GameSession session)
    {
        this.plugin = plugin;
        this.session = session;
    }

    public boolean isGuarded(UUID owner) {return guards.containsKey(owner);}

    public boolean ownsZombie(UUID zombieId) {return findByZombie(zombieId) != null;}

    private Guard findByZombie(UUID zombieId)
    {
        for (Guard guard : guards.values())
        {
            if (zombieId.equals(guard.zombieId)) {return guard;}
        }
        return null;
    }

    // ===== выход игрока =====

    /** Живой игрок вышел из идущего матча: спавним стража, игрок остаётся участником. */
    public void beginGuard(Player p)
    {
        Guard guard = new Guard(p.getUniqueId(), p.getName());
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item != null && !item.getType().isAir()) {guard.items.add(item.clone());}
        }
        spawnZombie(guard, p);

        int zombieSeconds = Math.max(10, plugin.getConfig().getInt("offline-guard.zombie-seconds", 90));
        int returnSeconds = Math.max(zombieSeconds + 10, plugin.getConfig().getInt("offline-guard.return-seconds", 240));

        guard.zombieTimer = Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            guard.zombieTimer = null;
            removeZombie(guard, true);
            endZombiePhase(guard, false);
        }, zombieSeconds * 20L);

        guard.deadlineTimer = Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            guard.deadlineTimer = null;
            finishGuard(guard, true);
        }, returnSeconds * 20L);

        guards.put(guard.owner, guard);
        session.gameChat().systemKey("offline-guard.left-broadcast",
            Msg.ph("player", guard.ownerName), Msg.ph("seconds", zombieSeconds));
    }

    // ===== возврат игрока =====

    /** Игрок перезашёл, оставаясь участником матча. */
    public boolean handleRejoin(Player p)
    {
        Guard guard = guards.remove(p.getUniqueId());
        if (guard == null) {return true;} // страховка: участник без стража — просто продолжает

        cancelTimers(guard);

        if (!guard.itemsDropped)
        {
            // зомби ещё стоит: убрать тихо, игрок продолжает со своим инвентарём
            removeZombie(guard, false);
            Msg.send(p, "offline-guard.rejoined-zombie");
            return true;
        }

        // вещи уже выпали: спасает только установленный блок возрождения
        p.getInventory().clear();
        RespawnBlock rb = session.respawnBlocks().get(p.getUniqueId());
        if (rb != null && rb.isPlaced() && rb.charges > 0 && !session.isFinalBattle())
        {
            session.respawnBlocks().materializeOnBlock(p);
            Msg.send(p, "offline-guard.rejoined-block");
            return true;
        }

        Msg.send(p, "offline-guard.returned-no-block");
        session.eliminateAfterFailedRespawn(p);
        return true;
    }

    // ===== зомби =====

    private void spawnZombie(Guard guard, Player p)
    {
        Location loc = p.getLocation();
        guard.zombieAt = loc.clone();
        RespawnBlock rb = session.respawnBlocks().get(p.getUniqueId());
        Class<? extends Zombie> guardType = rb != null ? rb.tier.guardType() : RespawnTier.COPPER.guardType();
        Zombie zombie = loc.getWorld().spawn(loc, guardType, z ->
        {
            z.setAI(false);
            z.setShouldBurnInDay(false);
            z.setAdult();
            if (z instanceof PigZombie pigZombie) {pigZombie.setAngry(false);}
            z.setPersistent(true);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            z.customName(Component.text(p.getName()));
            z.setCustomNameVisible(true);

            PlayerInventory inv = p.getInventory();
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(cloned(inv.getHelmet()));
            eq.setChestplate(cloned(inv.getChestplate()));
            eq.setLeggings(cloned(inv.getLeggings()));
            eq.setBoots(cloned(inv.getBoots()));
            eq.setItemInMainHand(cloned(inv.getItemInMainHand()));
            eq.setItemInOffHand(cloned(inv.getItemInOffHand()));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
        });
        guard.zombieId = zombie.getUniqueId();
    }

    private ItemStack cloned(ItemStack item)
    {
        return item == null ? null : item.clone();
    }

    /** Зомби умер. killer != null — убит игроком (это смерть владельца). */
    public void onZombieDeath(UUID zombieId, Player killer)
    {
        Guard guard = findByZombie(zombieId);
        if (guard == null) {return;}
        guard.zombieId = null; // сущность уже мертва

        boolean deathAnnounced = false;
        if (killer != null && session.isPlaying(killer.getUniqueId()))
        {
            MatchPlayer killerData = session.matchData(killer.getUniqueId());
            if (killerData != null) {killerData.kills++;}
            plugin.stats().add(killer.getUniqueId(), killer.getName(), "kills", 1);
            killer.giveExpLevels(plugin.getConfig().getInt("match.kill-xp-levels", 10));
            session.progressContracts(killer, ContractType.KILLS, c -> true, 1);
            session.themes().progress(killer, ThemeType.KILLS, t -> true, 1);
            session.respawnBlocks().onOwnerKill(killer);
            plugin.stats().add(guard.owner, guard.ownerName, "deaths", 1);
            session.announceDeath(guard.ownerName);
            deathAnnounced = true;
        }

        endZombiePhase(guard, deathAnnounced);
    }

    private void removeZombie(Guard guard, boolean animate)
    {
        if (guard.zombieId == null) {return;}
        Entity entity = Bukkit.getEntity(guard.zombieId);
        guard.zombieId = null;
        if (entity == null) {return;}
        if (animate)
        {
            Location center = entity.getLocation().add(0, 1, 0);
            center.getWorld().spawnParticle(Particle.CLOUD, center, 40, 0.3, 0.6, 0.3, 0.02);
            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_DEATH, 1.0f, 0.8f);
        }
        entity.remove();
    }

    /** Зомби кончился (истёк или убит): вещи выпадают; блок есть — ждём возврата, нет — выбыл. */
    private void endZombiePhase(Guard guard, boolean deathAnnounced)
    {
        cancelTask(guard.zombieTimer);
        guard.zombieTimer = null;
        dropItems(guard);

        RespawnBlock rb = session.respawnBlocks().get(guard.owner);
        boolean canReturn = rb != null && rb.isPlaced() && rb.charges > 0 && !session.isFinalBattle();
        if (!canReturn)
        {
            finishGuard(guard, !deathAnnounced);
        }
        // иначе ждём возврата: deadlineTimer (окно от выхода) уже тикает
    }

    private void dropItems(Guard guard)
    {
        if (guard.itemsDropped) {return;}
        guard.itemsDropped = true;
        Location at = guard.zombieAt;
        if (at == null || at.getWorld() == null) {return;}
        for (ItemStack item : guard.items)
        {
            if (item == null || item.getType().isAir()) {continue;}
            if (Items.isSpecial(item, "respawn_block")) {continue;} // блок-предмет гибнет с владельцем
            Item drop = at.getWorld().dropItemNaturally(at.clone().add(0, 0.5, 0), item);
            session.trackDrop(drop);
        }
        guard.items.clear();
    }

    /** Возврат невозможен или дедлайн истёк: заочное выбывание. */
    private void finishGuard(Guard guard, boolean announce)
    {
        cancelTimers(guard);
        removeZombie(guard, guard.zombieId != null);
        dropItems(guard);
        guards.remove(guard.owner);
        session.eliminateOffline(guard.owner, guard.ownerName, announce);
    }

    // ===== финал и очистка =====

    /** Финальная битва: стражи ликвидируются, их владельцы выбывают. */
    public void onFinalBattle()
    {
        for (Guard guard : List.copyOf(guards.values()))
        {
            finishGuard(guard, true);
        }
    }

    public void clear()
    {
        for (Guard guard : List.copyOf(guards.values()))
        {
            cancelTimers(guard);
            removeZombie(guard, false);
        }
        guards.clear();
    }

    private void cancelTimers(Guard guard)
    {
        cancelTask(guard.zombieTimer);
        cancelTask(guard.deadlineTimer);
        guard.zombieTimer = null;
        guard.deadlineTimer = null;
    }

    private void cancelTask(BukkitTask task)
    {
        if (task != null) {task.cancel();}
    }
}
