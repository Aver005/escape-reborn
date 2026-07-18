package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.game.GameEvent;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.menu.AssistantMenu;
import me.aver005.escape.menu.ShopMenu;
import me.aver005.escape.player.PlayerSnapshot;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
        if (session(e.getPlayer()) != null) {e.setCancelled(true);}
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

        if (type == Material.IRON_BARS)
        {
            // побег: решётку можно ломать, после матча она восстановится
            session.rememberEditedBlock(block);
            e.setDropItems(false);
            return;
        }

        boolean ore = name.endsWith("_ORE") || name.startsWith("INFESTED_");
        if (!ore) {e.setCancelled(true); return;}

        e.setDropItems(false);
        e.setExpToDrop(0);

        var data = session.matchData(p.getUniqueId());
        if (data != null) {data.ores++;}
        plugin.stats().add(p.getUniqueId(), p.getName(), "ores_mined", 1);

        if (session.getCurrentEvent() == GameEvent.DIG) {session.flagEventAction(p);}

        session.progressContracts(p, ContractType.MINE, c -> type == Material.matchMaterial(c.getIdle()), 1);
        session.progressContracts(p, ContractType.BREAK, c -> type == Material.matchMaterial(c.getIdle()), 1);
    }

    // ===== бой =====

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e)
    {
        Player damager = resolveDamager(e.getDamager());
        if (damager == null) {return;}
        GameSession session = session(damager);
        if (session == null) {return;}

        if (e.getEntity() instanceof org.bukkit.entity.ItemFrame) {e.setCancelled(true); return;}
        if (!(e.getEntity() instanceof Player victim)) {return;}

        // разминка в лобби: урона нет, счёт идёт
        if (session.isLobbyMember(victim.getUniqueId()) && session.isLobbyMember(damager.getUniqueId()))
        {
            session.addWarmupDamage(damager, e.getDamage());
            e.setCancelled(true);
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
            e.setCancelled(true);
            session.dropInventory(p, p.getLocation());
            p.setHealth(20.0);
            session.eliminate(p, false);
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
            if (Items.isSpecial(hand, "leave")
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
            {
                e.setCancelled(true);
                if (session.leave(p)) {Msg.send(p, "lobby.left-match");}
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
            if (leverName != null)
            {
                session.progressContracts(p, ContractType.ACTIVATE, c -> leverName.equals(c.getIdle()), 1);
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
            .get(me.aver005.escape.util.Keys.TRADER_TYPE, org.bukkit.persistence.PersistentDataType.STRING);
        TraderType trader = typeId != null ? plugin.traders().get(typeId) : null;
        if (trader == null && villager.customName() != null)
        {
            trader = plugin.traders().byDisplayName(villager.customName());
        }
        if (trader == null) {return;}

        e.setCancelled(true);
        new ShopMenu(plugin, session, trader).open(p);
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
        if (session.getActiveChests().contains(loc))
        {
            session.handleChestLooted(p, loc);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e)
    {
        if (!(e.getPlayer() instanceof Player p)) {return;}
        GameSession session = session(p);
        if (session == null || !session.isPlaying(p.getUniqueId())) {return;}
        if (!(e.getInventory().getHolder() instanceof Chest chest)) {return;}

        session.refreshFindContracts(p);
        session.scheduleRefillIfEmpty(chest.getBlock());
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
            session.trackDrop(e.getItemDrop());
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
        // восстановление после краша/выхода во время матча
        if (session(p) == null && PlayerSnapshot.exists(plugin, p.getUniqueId()))
        {
            plugin.getServer().getScheduler().runTask(plugin, () ->
            {
                if (p.isOnline()) {PlayerSnapshot.restore(plugin, p);}
            });
        }
    }
}
