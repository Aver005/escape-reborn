package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.game.GameEvent;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.menu.AssistantMenu;
import me.aver005.escape.menu.KitSelectMenu;
import me.aver005.escape.menu.Menu;
import me.aver005.escape.menu.NpcMenu;
import me.aver005.escape.menu.RespawnUpgradeMenu;
import me.aver005.escape.menu.ScavengerMenu;
import me.aver005.escape.menu.ShopMenu;
import me.aver005.escape.menu.ThemesMenu;
import me.aver005.escape.player.PlayerSnapshot;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Вся игровая логика: бой, смерть, лут, контракты, предметы. */
public class GameListener implements Listener
{
    private final EscapePlugin plugin;

    public GameListener(EscapePlugin plugin) {this.plugin = plugin;}

    private GameSession session(Player p) {return plugin.arenas().sessionOf(p);}

    // ===== блоки =====

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}

        // блок возрождения — единственное, что игрок может ставить
        if (session.isPlaying(p.getUniqueId()) && Items.isSpecial(e.getItemInHand(), "respawn_block"))
        {
            if (!session.respawnBlocks().tryPlace(p, e)) {e.setCancelled(true);}
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}
        if (!session.isPlaying(p.getUniqueId())) {e.setCancelled(true); return;}

        Block block = e.getBlock();
        Material type = block.getType();
        String name = type.name();

        // блок возрождения: свой — перенос, чужой — уничтожение с наградой
        if (session.respawnBlocks().handleBreak(p, block, e)) {return;}

        if (type == Material.IRON_BARS)
        {
            // побег: решётку можно ломать, после матча она восстановится
            DebugLog.log(Cat.WORLD, "bars-broken player=%s at=%s", p.getName(), DebugLog.at(block.getLocation()));
            session.rememberEditedBlock(block);
            e.setDropItems(false);
            return;
        }

        // отмеченные ломаемые блоки: игрок ломает, после матча вернутся (обе половины дверей/кроватей)
        if (session.getArena().getBreakables().contains(block.getLocation()))
        {
            DebugLog.log(Cat.WORLD, "breakable-broken player=%s block=%s at=%s",
                p.getName(), type, DebugLog.at(block.getLocation()));
            session.rememberStructure(block);
            e.setDropItems(false);
            return;
        }

        // огонь: свой/чужой игрок-огонь можно потушить рукой; огонь карты — нельзя
        if (type == Material.FIRE)
        {
            if (session.isMatchFire(block.getLocation()))
            {
                session.douseMatchFire(block.getLocation());
                DebugLog.log(Cat.WORLD, "fire-punch player=%s at=%s", p.getName(), DebugLog.at(block.getLocation()));
                return; // ваниль убирает блок огня
            }
            e.setCancelled(true);
            return;
        }

        boolean ore = name.endsWith("_ORE") || name.startsWith("INFESTED_");
        if (!ore)
        {
            DebugLog.log(Cat.WORLD, "break-denied player=%s block=%s at=%s",
                p.getName(), type, DebugLog.at(block.getLocation()));
            e.setCancelled(true);
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        DebugLog.log(Cat.WORLD, "ore-mined player=%s ore=%s at=%s", p.getName(), type, DebugLog.at(block.getLocation()));

        var data = session.matchData(p.getUniqueId());
        if (data != null) {data.ores++;}
        plugin.stats().add(p.getUniqueId(), p.getName(), "ores_mined", 1);

        if (session.getCurrentEvent() == GameEvent.DIG) {session.flagEventAction(p);}

        session.progressContracts(p, ContractType.MINE, c -> type == Material.matchMaterial(c.getIdle()), 1);
        session.progressContracts(p, ContractType.BREAK, c -> type == Material.matchMaterial(c.getIdle()), 1);
        session.themes().progress(p, ThemeType.MINE, t -> type == Material.matchMaterial(t.getIdle()), 1);
        session.themes().progress(p, ThemeType.BREAK, t -> type == Material.matchMaterial(t.getIdle()), 1);
    }

    // ===== жидкости: разлить воду/лаву без последствий =====

    /**
     * Разлив ведром в матче: ставим ОДИН блок-источник воды/лавы. Прежний блок
     * (воздух/растение) запоминаем ДО разлива — cleanup вернёт и его, и жидкость.
     * Растекание глушит ProtectionListener.onLiquidFlow, поэтому источник остаётся
     * одним блоком. Разрешены только вода и лава: рыбные вёдра плодят
     * незарегистрированных сущностей, поэтому режем всё прочее.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}
        if (!session.isPlaying(p.getUniqueId()) || !liquidAllowed(e.getBucket()))
        {
            e.setCancelled(true);
            return;
        }
        Block block = e.getBlock(); // блок, который станет жидкостью (учитывает замену растения)
        session.rememberEditedBlock(block);
        DebugLog.log(Cat.WORLD, "liquid-pour player=%s bucket=%s was=%s at=%s",
            p.getName(), e.getBucket(), block.getType(), DebugLog.at(block.getLocation()));
    }

    /**
     * Наполнение ведра в матче: игрок снова забирает разлитую (или лежащую на
     * карте) жидкость. Источник станет воздухом — запоминаем его ДО изъятия, чтобы
     * cleanup вернул исходное состояние (putIfAbsent сохранит самый первый вариант,
     * так что вода-карты вернётся водой, а разлитая — тем, что было под ней).
     */
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}
        if (!session.isPlaying(p.getUniqueId()) || !plugin.getConfig().getBoolean("liquids.enabled", true))
        {
            e.setCancelled(true);
            return;
        }
        Block block = e.getBlock();
        session.rememberEditedBlock(block);
        DebugLog.log(Cat.WORLD, "liquid-scoop player=%s bucket=%s at=%s",
            p.getName(), e.getItemStack() == null ? "?" : e.getItemStack().getType(), DebugLog.at(block.getLocation()));
    }

    /** Разлив включён и данный тип ведра (вода/лава) разрешён конфигом? */
    private boolean liquidAllowed(Material bucket)
    {
        if (!plugin.getConfig().getBoolean("liquids.enabled", true)) {return false;}
        if (bucket == Material.WATER_BUCKET) {return plugin.getConfig().getBoolean("liquids.allow-water", true);}
        if (bucket == Material.LAVA_BUCKET) {return plugin.getConfig().getBoolean("liquids.allow-lava", true);}
        return false;
    }

    // ===== бой =====

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e)
    {
        Player damager = resolveDamager(e.getDamager());
        if (damager == null) {return;}
        GameSession session = session(damager);
        if (session == null) {return;}

        if (e.getEntity() instanceof ItemFrame) {e.setCancelled(true); return;}
        if (!(e.getEntity() instanceof Player victim)) {return;}

        // разминка в лобби: реального урона нет, но бьём с анимацией/звуком/отдачей, счёт идёт
        if (session.isLobbyMember(victim.getUniqueId()) && session.isLobbyMember(damager.getUniqueId()))
        {
            session.addWarmupDamage(damager, e.getDamage());
            e.setCancelled(true);
            playWarmupHitFeedback(victim, damager);
            return;
        }

        if (session.isPlaying(victim.getUniqueId()) && session.isPlaying(damager.getUniqueId()))
        {
            // «Кровавая луна»: урон между игроками x1.5
            if (session.isBloodMoon()) {e.setDamage(e.getDamage() * 1.5);}
            session.recordDamager(victim, damager);
        }
    }

    private Player resolveDamager(Entity damager)
    {
        if (damager instanceof Player p) {return p;}
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {return p;}
        return null;
    }

    // Визуал удара в разминке: событие отменено (урона нет), но клиенту показываем
    // красный флэш, звук получения урона и лёгкую отдачу от атакующего.
    private void playWarmupHitFeedback(Player victim, Player damager)
    {
        Location vLoc = victim.getLocation();
        double dx = damager.getX() - vLoc.getX();
        double dz = damager.getZ() - vLoc.getZ();
        float sourceYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        victim.playHurtAnimation(sourceYaw - vLoc.getYaw());
        victim.getWorld().playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        victim.knockback(0.4, dx, dz);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e)
    {
        if (!(e.getEntity() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null) {return;}

        if (session.isLobbyMember(p.getUniqueId())) {e.setCancelled(true); return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}

        if (e.getFinalDamage() >= p.getHealth())
        {
            DebugLog.log(Cat.COMBAT, "lethal-damage player=%s cause=%s damage=%.1f hp=%.1f",
                p.getName(), e.getCause(), e.getFinalDamage(), p.getHealth());
            e.setCancelled(true);
            session.dropInventory(p, p.getLocation());
            p.setHealth(20.0);
            session.handleDeath(p);
        }
    }

    // ===== предметы и взаимодействия =====

    @EventHandler
    public void onInteract(PlayerInteractEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}
        if (e.getHand() == EquipmentSlot.OFF_HAND && e.getAction() != Action.PHYSICAL) {return;}

        // лобби: выход по магма-крему
        if (session.isLobbyMember(p.getUniqueId()))
        {
            ItemStack hand = p.getInventory().getItemInMainHand();
            boolean rightClick = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
            if (Items.isSpecial(hand, "leave") && rightClick)
            {
                e.setCancelled(true);
                if (session.leave(p)) {Msg.send(p, "lobby.left-match");}
            }
            else if (Items.isSpecial(hand, "kit-select") && rightClick)
            {
                e.setCancelled(true);
                new KitSelectMenu(plugin, session, p.getUniqueId()).open(p);
            }
            else if (Items.isSpecial(hand, "modifier-vote") && rightClick)
            {
                e.setCancelled(true);
                session.toggleModifierVote(p);
            }
            return;
        }

        if (!session.isPlaying(p.getUniqueId())) {return;}

        Action action = e.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
        {
            ItemStack item = e.getItem();
            if (Items.isSpecial(item, "assistant"))
            {
                e.setCancelled(true);
                new AssistantMenu(plugin, session).open(p);
                return;
            }
            if (Items.isSpecial(item, "insight"))
            {
                e.setCancelled(true);
                session.respawnBlocks().useInsight(p, item);
                return;
            }
        }

        if (action == Action.PHYSICAL)
        {
            Block block = e.getClickedBlock();
            if (block != null && !block.getType().name().endsWith("PLATE")) {e.setCancelled(true);}
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK) {return;}
        Block block = e.getClickedBlock();
        if (block == null) {return;}

        // «Волшебный ключик»: ПКМ по игровому сундуку — принудительный рефилл
        if (block.getType() == Material.CHEST && Items.isSpecial(e.getItem(), "magic_key"))
        {
            e.setCancelled(true);
            session.themes().useKey(p, e.getItem(), block);
            return;
        }

        // ПКМ по блоку возрождения: свой — меню прокачки
        var respawnBlock = session.respawnBlocks().byLocation(block.getLocation());
        if (respawnBlock != null)
        {
            e.setCancelled(true);
            if (respawnBlock.owner.equals(p.getUniqueId()))
            {
                new RespawnUpgradeMenu(plugin, session, respawnBlock).open(p);
            }
            else
            {
                Msg.send(p, "respawn-block.enemy-info", Msg.ph("player", respawnBlock.ownerName));
            }
            return;
        }

        Material type = block.getType();

        // запрещённые станции (зачарование разрешено!)
        if (type.name().endsWith("SHULKER_BOX")
            || type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER
            || type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL
            || type == Material.CRAFTING_TABLE
            || type == Material.ENDER_CHEST)
        {
            e.setCancelled(true);
            return;
        }

        if (type == Material.LEVER)
        {
            String leverName = session.getArena().getLevers().get(block.getLocation());
            DebugLog.log(Cat.WORLD, "lever player=%s named=%s at=%s",
                p.getName(), leverName == null ? "-" : leverName, DebugLog.at(block.getLocation()));
            if (leverName != null)
            {
                session.progressContracts(p, ContractType.ACTIVATE, c -> leverName.equals(c.getIdle()), 1);
                session.themes().progress(p, ThemeType.ACTIVATE, t -> leverName.equals(t.getIdle()), 1);
            }
        }
    }

    /** Клик по невидимой стойке (хитбокс перекрывает сундук) — открыть сундук. */
    @EventHandler
    public void onInteractAtStand(PlayerInteractAtEntityEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        if (!(e.getRightClicked() instanceof ArmorStand stand)) {return;}

        Block above = stand.getLocation().clone().add(0, 1, 0).getBlock();
        if (above.getType() != Material.CHEST) {return;}
        if (!session.getActiveChests().contains(above.getLocation())) {return;}

        e.setCancelled(true);
        if (above.getState() instanceof Chest chest)
        {
            p.openInventory(chest.getInventory());
        }
    }

    @EventHandler
    public void onInteractVillager(PlayerInteractEntityEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        if (!(e.getRightClicked() instanceof Villager villager)) {return;}

        String typeId = villager.getPersistentDataContainer()
            .get(Keys.TRADER_TYPE, PersistentDataType.STRING);
        TraderType trader = typeId != null ? plugin.traders().get(typeId) : null;
        if (trader == null && villager.customName() != null)
        {
            trader = plugin.traders().byDisplayName(villager.customName());
        }
        if (trader == null) {return;}

        e.setCancelled(true);

        // сначала попытка сдачи (готовая темка / принесённая передачка)
        if (session.themes().tryTurnIn(p, trader, villager)) {return;}

        boolean shop = trader.isShop();
        boolean overseer = trader.isOverseer();
        boolean scavenger = trader.isScavenger();
        int roles = (shop ? 1 : 0) + (overseer ? 1 : 0) + (scavenger ? 1 : 0);
        DebugLog.log(Cat.SHOP, "npc-open player=%s trader=%s shop=%b overseer=%b scavenger=%b at=%s",
            p.getName(), trader.getId(), shop, overseer, scavenger, DebugLog.at(villager.getLocation()));
        if (roles >= 2) {new NpcMenu(plugin, session, trader, villager).open(p);}
        else if (overseer) {new ThemesMenu(plugin, session, trader, villager).open(p);}
        else if (scavenger) {new ScavengerMenu(plugin, session, trader, null).open(p);}
        else {new ShopMenu(plugin, session, trader).open(p);}
    }

    // ===== сундуки: LOOT / FIND / рефилл =====

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e)
    {
        if (!(e.getPlayer() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        if (!(e.getInventory().getHolder() instanceof Chest chest)) {return;}

        Location loc = chest.getBlock().getLocation();
        if (!session.getActiveChests().contains(loc))
        {
            // dynamic-chests: немаркированный сундук становится игровым при открытии
            if (!session.registerDynamicChest(chest)) {return;}
        }
        session.handleChestLooted(p, loc);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e)
    {
        if (!(e.getPlayer() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        if (!(e.getInventory().getHolder() instanceof Chest chest)) {return;}

        session.refreshFindContracts(p);
        session.scheduleRefill(chest.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e)
    {
        if (!(e.getEntity() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        // прогресс FIND пересчитается на следующий тик, когда предмет уже в инвентаре
        plugin.getServer().getScheduler().runTask(plugin, () ->
        {
            if (p.isOnline()) {session.refreshFindContracts(p);}
        });
    }

    /** Блок возрождения нельзя спрятать в сундук/контейнер. */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        var top = e.getView().getTopInventory();
        if (top.getHolder() instanceof Menu) {return;}
        if (top.getType() == InventoryType.CRAFTING) {return;}

        boolean intoTop = e.getRawSlot() >= 0 && e.getRawSlot() < top.getSize()
            && Items.isSpecial(e.getCursor(), "respawn_block");
        boolean shiftFromBottom = e.getRawSlot() >= top.getSize()
            && e.isShiftClick() && Items.isSpecial(e.getCurrentItem(), "respawn_block");
        if (intoTop || shiftFromBottom) {e.setCancelled(true);}
    }

    /**
     * Системные предметы нельзя разбирать/крафтить (блок возрождения -> слитки,
     * передачка-темка из кожи -> броня и т.п.): если в сетке лежит предмет с
     * меткой special, результат обнуляется. PrepareItemCraftEvent не Cancellable —
     * отмена только через setResult(null); закрывает и крафт 2x2, и верстак.
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e)
    {
        if (!(e.getView().getPlayer() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null) {return;}
        for (ItemStack item : e.getInventory().getMatrix())
        {
            String tag = Items.specialTag(item);
            if (tag == null) {continue;}
            e.getInventory().setResult(null);
            DebugLog.log(Cat.PLAYER, "craft-denied player=%s special=%s", p.getName(), tag);
            return;
        }
    }

    // ===== разное =====

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = session(p);
        if (session == null) {return;}
        if (session.isLobbyMember(p.getUniqueId())) {e.setCancelled(true); return;}
        if (session.isPlaying(p.getUniqueId()))
        {
            // блок возрождения нельзя выбросить вручную
            if (Items.isSpecial(e.getItemDrop().getItemStack(), "respawn_block"))
            {
                e.setCancelled(true);
                return;
            }
            session.trackDrop(e.getItemDrop());
            DebugLog.log(Cat.PLAYER, "item-drop player=%s item=%s at=%s",
                p.getName(), DebugLog.item(e.getItemDrop().getItemStack()), DebugLog.at(p.getLocation()));
            if (session.getCurrentEvent() == GameEvent.SEARCH) {session.flagEventAction(p);}
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent e)
    {
        if (!(e.getEntity() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session != null && session.isLobbyMember(p.getUniqueId())) {e.setCancelled(true);}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e)
    {
        GameSession session = session(e.getPlayer());
        if (session != null) {session.handleQuit(e.getPlayer());}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e)
    {
        Player p = e.getPlayer();

        // возврат в матч, которым всё ещё владеет сессия (оффлайн-страж)
        GameSession session = session(p);
        if (session != null)
        {
            if (session.handleRejoin(p)) {return;}
            plugin.arenas().unbind(p.getUniqueId());
        }

        // восстановление после краша/выбытия в отсутствие
        if (PlayerSnapshot.exists(plugin, p.getUniqueId()))
        {
            plugin.getServer().getScheduler().runTask(plugin, () ->
            {
                if (p.isOnline() && plugin.arenas().sessionOf(p) == null)
                {
                    PlayerSnapshot.restore(plugin, p);
                }
            });
        }
    }

    /** Смерть зомби-стража: убийца-игрок = смерть владельца. */
    @EventHandler
    public void onZombieDeath(EntityDeathEvent e)
    {
        if (!(e.getEntity() instanceof Zombie zombie)) {return;}
        for (var arena : plugin.arenas().all().values())
        {
            GameSession session = arena.getSession();
            if (session == null || !session.offlineGuards().ownsZombie(zombie.getUniqueId())) {continue;}
            e.getDrops().clear();
            e.setDroppedExp(0);
            session.offlineGuards().onZombieDeath(zombie.getUniqueId(), zombie.getKiller());
            return;
        }
    }
}
