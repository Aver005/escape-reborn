package me.aver005.escape.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Блоки возрождения (docs/07-decisions.md §7): выдача, установка, перенос,
 * прокачка, возрождение, молнии, «Молния прозрения», аннуляция на финале.
 */
public class RespawnBlocks
{
    public enum DestroyReason {BROKEN, EXHAUSTED, ANNULLED, OWNER_LEFT}

    private static final Random RANDOM = new Random();

    private final EscapePlugin plugin;
    private final GameSession session;

    private final Map<UUID, RespawnBlock> byOwner = new HashMap<>();
    private final Map<Location, UUID> ownerByLocation = new HashMap<>();
    /** Ожидающие возрождения: игрок (в спектаторах) -> отложенная задача. */
    private final Map<UUID, BukkitTask> pendingRespawns = new HashMap<>();

    public RespawnBlocks(EscapePlugin plugin, GameSession session)
    {
        this.plugin = plugin;
        this.session = session;
    }

    public RespawnBlock get(UUID owner) {return byOwner.get(owner);}

    public RespawnBlock byLocation(Location loc)
    {
        UUID owner = ownerByLocation.get(loc);
        return owner == null ? null : byOwner.get(owner);
    }

    public boolean hasPlacedBlocks()
    {
        return !ownerByLocation.isEmpty();
    }

    // ===== выдача =====

    /** Создаёт данные блока для игрока и возвращает предмет для кита. */
    public ItemStack initFor(Player p)
    {
        RespawnBlock rb = new RespawnBlock(p.getUniqueId(), p.getName());
        byOwner.put(p.getUniqueId(), rb);
        return createItem(rb);
    }

    /** Предмет-блок: уровень/заряды в лоре, владелец в PDC. */
    public ItemStack createItem(RespawnBlock rb)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("respawn-block.item-lore-tier", Msg.phC("tier", Msg.get(rb.tier.nameKey()))));
        lore.add(Msg.get("respawn-block.item-lore-charges", Msg.ph("charges", rb.charges)));
        for (var line : Msg.getList("respawn-block.item-lore-hint")) {lore.add(line);}
        ItemStack item = Items.special(rb.tier.material(), Msg.get("respawn-block.item-name"), lore, "respawn_block");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.RESPAWN_OWNER, PersistentDataType.STRING, rb.owner.toString());
        item.setItemMeta(meta);
        return item;
    }

    public static UUID itemOwner(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return null;}
        String raw = item.getItemMeta().getPersistentDataContainer().get(Keys.RESPAWN_OWNER, PersistentDataType.STRING);
        try {return raw == null ? null : UUID.fromString(raw);}
        catch (IllegalArgumentException e) {return null;}
    }

    // ===== установка и перенос =====

    /** Установка блока из предмета. false — событие нужно отменить. */
    public boolean tryPlace(Player p, BlockPlaceEvent e)
    {
        UUID itemOwner = itemOwner(e.getItemInHand());
        if (itemOwner == null || !itemOwner.equals(p.getUniqueId()))
        {
            Msg.send(p, "respawn-block.not-yours");
            return false;
        }
        RespawnBlock rb = byOwner.get(p.getUniqueId());
        if (rb == null || rb.charges <= 0) {return false;}
        if (session.isFinalBattle()) {Msg.send(p, "respawn-block.final-battle"); return false;}
        if (rb.isPlaced()) {return false;} // блок уже стоит, второго предмета быть не должно

        Block block = e.getBlock();
        Block above1 = block.getRelative(0, 1, 0);
        Block above2 = block.getRelative(0, 2, 0);
        Block below = block.getRelative(0, -1, 0);
        if (!above1.isPassable() || !above2.isPassable() || !below.getType().isSolid())
        {
            Msg.send(p, "respawn-block.bad-spot");
            return false;
        }

        session.rememberEditedBlock(block); // прежнее состояние (обычно воздух) — для отката
        rb.placedAt = block.getLocation();
        ownerByLocation.put(rb.placedAt, rb.owner);

        strike(rb.placedAt); // молния при установке — позиция раскрывается
        Msg.send(p, "respawn-block.placed", Msg.ph("charges", rb.charges));
        return true;
    }

    /**
     * Ломание установленного блока. true — событие обработано (это был блок возрождения).
     * Свой блок — переносится (предмет возвращается), чужой — уничтожается с наградой.
     */
    public boolean handleBreak(Player breaker, Block block, BlockBreakEvent e)
    {
        RespawnBlock rb = byLocation(block.getLocation());
        if (rb == null) {return false;}
        e.setDropItems(false);

        if (rb.owner.equals(breaker.getUniqueId()))
        {
            ownerByLocation.remove(rb.placedAt);
            rb.placedAt = null;
            breaker.getInventory().addItem(createItem(rb));
            Msg.send(breaker, "respawn-block.picked-up");
            return true;
        }

        destroy(rb, DestroyReason.BROKEN, breaker);
        return true;
    }

    // ===== уничтожение =====

    public void destroy(RespawnBlock rb, DestroyReason reason, Player breaker)
    {
        Location loc = rb.placedAt;
        if (loc != null)
        {
            ownerByLocation.remove(loc);
            rb.placedAt = null;

            // анимация исчезновения своя у каждого уровня + молния
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            loc.getWorld().spawnParticle(rb.tier.particle(), center, 60, 0.4, 0.4, 0.4, 0.05);
            loc.getWorld().playSound(center, rb.tier.sound(), 1.0f, 1.0f);
            strike(loc);
            loc.getBlock().setType(Material.AIR);
        }

        if (reason == DestroyReason.BROKEN && breaker != null)
        {
            session.giveGold(breaker, rb.tier.breakerGold());
            if (rb.tier == RespawnTier.EMERALD)
            {
                breaker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 30, 1, false, true));
            }
            Msg.send(breaker, "respawn-block.broke-enemy",
                Msg.ph("player", rb.ownerName), Msg.ph("n", rb.tier.breakerGold()));
        }

        Player owner = Bukkit.getPlayer(rb.owner);
        if (owner != null && session.isPlaying(rb.owner))
        {
            switch (reason)
            {
                case BROKEN -> Msg.send(owner, "respawn-block.destroyed-by-enemy");
                case EXHAUSTED -> Msg.send(owner, "respawn-block.exhausted");
                case ANNULLED -> Msg.send(owner, "respawn-block.annulled");
                case OWNER_LEFT -> {}
            }
        }

        // владелец ждал возрождения на этом блоке — возрождение срывается
        if (cancelPendingRespawn(rb.owner))
        {
            if (owner != null && session.isPlaying(rb.owner))
            {
                Msg.send(owner, "respawn-block.respawn-cancelled");
                session.eliminateAfterFailedRespawn(owner);
            }
        }

        byOwner.remove(rb.owner);
    }

    /** Владелец окончательно выбыл: снести его блок без наград. */
    public void onOwnerEliminated(UUID owner)
    {
        RespawnBlock rb = byOwner.get(owner);
        if (rb == null) {return;}
        if (rb.isPlaced()) {destroy(rb, DestroyReason.OWNER_LEFT, null);}
        else {byOwner.remove(owner);}
    }

    // ===== возрождение (отложенное: 5 сек в спектаторах) =====

    /**
     * Смерть при доступном блоке: заряд резервируется, игрок уходит в спектаторы,
     * через delay секунд появляется на блоке (без вещей). true — возрождение запланировано.
     * Если блок уничтожат за время ожидания — возрождение срывается (см. destroy).
     */
    public boolean tryScheduleRespawn(Player p)
    {
        UUID id = p.getUniqueId();
        RespawnBlock rb = byOwner.get(id);
        if (rb == null || !rb.isPlaced() || rb.charges <= 0 || session.isFinalBattle()) {return false;}
        if (pendingRespawns.containsKey(id)) {return true;}

        rb.charges--;
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);

        int delay = Math.max(1, plugin.getConfig().getInt("respawn-block.respawn-delay-seconds", 5));
        Msg.send(p, "respawn-block.respawn-wait", Msg.ph("seconds", delay));
        p.showTitle(Title.title(
            Msg.get("respawn-block.respawn-wait-title"),
            Msg.get("respawn-block.respawn-wait-subtitle", Msg.ph("seconds", delay))));

        pendingRespawns.put(id, Bukkit.getScheduler().runTaskLater(plugin,
            () -> completeRespawn(id), delay * 20L));
        return true;
    }

    private void completeRespawn(UUID id)
    {
        pendingRespawns.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline() || !session.isPlaying(id)) {return;}

        RespawnBlock rb = byOwner.get(id);
        if (rb == null || !rb.isPlaced())
        {
            // блок пропал за время ожидания (страховка: обычно destroy() выбивает раньше)
            session.eliminateAfterFailedRespawn(p);
            return;
        }

        applyRespawn(p, rb);
        if (rb.charges <= 0) {destroy(rb, DestroyReason.EXHAUSTED, null);}
    }

    /** Мгновенная материализация на блоке (возврат из оффлайна): тратит заряд. */
    public void materializeOnBlock(Player p)
    {
        RespawnBlock rb = byOwner.get(p.getUniqueId());
        if (rb == null || !rb.isPlaced() || rb.charges <= 0) {return;}
        rb.charges--;
        applyRespawn(p, rb);
        if (rb.charges <= 0) {destroy(rb, DestroyReason.EXHAUSTED, null);}
    }

    private void applyRespawn(Player p, RespawnBlock rb)
    {
        Location target = rb.placedAt.clone().add(0.5, 1, 0.5);
        target.getChunk().load();
        p.teleport(target);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 1, false, false));
        if (session.isGlowActive())
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        Msg.send(p, "respawn-block.respawned", Msg.ph("charges", rb.charges));
    }

    /** Отменить ожидающее возрождение (выход из матча). true — оно было. */
    public boolean cancelPendingRespawn(UUID id)
    {
        var task = pendingRespawns.remove(id);
        if (task == null) {return false;}
        task.cancel();
        return true;
    }

    public boolean isAwaitingRespawn(UUID id)
    {
        return pendingRespawns.containsKey(id);
    }

    // ===== прокачка =====

    /** Проверка и применение прокачки (вызывается из меню). */
    public boolean tryUpgrade(Player p, RespawnBlock rb)
    {
        if (!rb.isPlaced() || !rb.owner.equals(p.getUniqueId())) {return false;}
        RespawnTier next = rb.tier.next();
        if (next == null) {Msg.send(p, "respawn-block.max-tier"); return false;}

        if (next == RespawnTier.EMERALD)
        {
            if (session.aliveCount() < RespawnTier.EMERALD_MIN_ALIVE)
            {
                Msg.send(p, "respawn-block.emerald-few-players", Msg.ph("n", RespawnTier.EMERALD_MIN_ALIVE));
                return false;
            }
            if (rb.killsSinceDiamond < RespawnTier.EMERALD_KILLS_REQUIRED)
            {
                Msg.send(p, "respawn-block.emerald-need-kills",
                    Msg.ph("kills", rb.killsSinceDiamond), Msg.ph("need", RespawnTier.EMERALD_KILLS_REQUIRED));
                return false;
            }
        }
        else
        {
            int cost = next.upgradeCostGold();
            if (Items.countMaterial(p, Material.GOLD_INGOT) < cost)
            {
                Msg.send(p, "respawn-block.not-enough-gold", Msg.ph("cost", cost));
                return false;
            }
            Items.takeMaterial(p, Material.GOLD_INGOT, cost);
        }

        rb.tier = next;
        rb.charges += RespawnTier.UPGRADE_CHARGES;
        if (next == RespawnTier.DIAMOND) {rb.killsSinceDiamond = 0;}

        Block block = rb.placedAt.getBlock();
        block.setType(next.material());
        Location center = rb.placedAt.clone().add(0.5, 0.5, 0.5);
        center.getWorld().spawnParticle(next.particle(), center, 40, 0.4, 0.4, 0.4, 0.05);
        center.getWorld().playSound(center, next.sound(), 1.0f, 1.2f);

        Msg.send(p, "respawn-block.upgraded",
            Msg.phC("tier", Msg.get(next.nameKey())), Msg.ph("charges", rb.charges));
        return true;
    }

    /** Убийство владельцем: прогресс к изумрудному (только на алмазном уровне). */
    public void onOwnerKill(Player killer)
    {
        RespawnBlock rb = byOwner.get(killer.getUniqueId());
        if (rb == null || rb.tier != RespawnTier.DIAMOND) {return;}
        rb.killsSinceDiamond++;
        if (rb.killsSinceDiamond <= RespawnTier.EMERALD_KILLS_REQUIRED)
        {
            Msg.send(killer, "respawn-block.emerald-progress",
                Msg.ph("kills", rb.killsSinceDiamond), Msg.ph("need", RespawnTier.EMERALD_KILLS_REQUIRED));
        }
    }

    // ===== молнии =====

    private void strike(Location loc)
    {
        loc.getWorld().strikeLightningEffect(loc.clone().add(0.5, 0, 0.5));
    }

    /** Молния по всем установленным блокам (интервал 5 минут / «Молния прозрения»). */
    public void strikeAll()
    {
        for (Location loc : List.copyOf(ownerByLocation.keySet())) {strike(loc);}
    }

    // ===== «Молния прозрения» =====

    public ItemStack createInsightItem()
    {
        return Items.special(Material.LIGHTNING_ROD,
            Msg.get("respawn-block.insight-name"), Msg.getList("respawn-block.insight-lore"), "insight");
    }

    /** Разложить квоту «Молний прозрения» по активным сундукам: 1 + по 1 за каждую пару игроков. */
    public void distributeInsight(Set<Location> activeChests, int playerCount)
    {
        int quota = 1 + playerCount / 2;
        List<Location> pool = new ArrayList<>(activeChests);
        java.util.Collections.shuffle(pool, RANDOM);
        int placed = 0;
        for (Location loc : pool)
        {
            if (placed >= quota) {break;}
            if (!(loc.getBlock().getState() instanceof Chest chest)) {continue;}
            int slot = chest.getInventory().firstEmpty();
            if (slot == -1) {continue;}
            chest.getInventory().setItem(slot, createInsightItem());
            placed++;
        }
    }

    /** Использование «Молнии прозрения»: молния по всем установленным блокам. */
    public void useInsight(Player p, ItemStack item)
    {
        if (ownerByLocation.isEmpty())
        {
            Msg.send(p, "respawn-block.insight-no-targets");
            return;
        }
        item.setAmount(item.getAmount() - 1);
        strikeAll();
        session.gameChat().systemKey("respawn-block.insight-used");
        for (UUID owner : List.copyOf(ownerByLocation.values()))
        {
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null) {Msg.send(ownerPlayer, "respawn-block.insight-warning");}
        }
    }

    // ===== финал и очистка =====

    /** Финальная битва: аннулировать все блоки. */
    public void annulAll()
    {
        for (UUID owner : List.copyOf(ownerByLocation.values()))
        {
            RespawnBlock rb = byOwner.get(owner);
            if (rb != null) {destroy(rb, DestroyReason.ANNULLED, null);}
        }
    }

    public void clear()
    {
        for (var task : pendingRespawns.values()) {task.cancel();}
        pendingRespawns.clear();
        byOwner.clear();
        ownerByLocation.clear();
    }
}
