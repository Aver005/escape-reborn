package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.game.GameSession;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;

/** Защита миров с аренами: огонь, листва, висячие сущности. */
public class ProtectionListener implements Listener
{
    private final EscapePlugin plugin;

    public ProtectionListener(EscapePlugin plugin) {this.plugin = plugin;}

    private Arena arenaInWorld(World world)
    {
        for (Arena arena : plugin.arenas().all().values())
        {
            if (world.getName().equals(arena.getWorldName())) {return arena;}
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e)
    {
        if (arenaInWorld(e.getBlock().getWorld()) != null) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e)
    {
        Arena arena = arenaInWorld(e.getBlock().getWorld());
        if (arena == null) {return;}
        GameSession session = arena.getSession();
        if (session == null || session.getPhase() != GameSession.Phase.RUNNING) {e.setCancelled(true); return;}

        // в матче — только НАМЕРЕННЫЙ поджиг (огниво / огненный шар / горящая
        // стрела). Огонь регистрируется как игровой: не распространяется, не жжёт
        // блоки, гаснет по таймеру. Лава/распространение/молния/взрыв запрещены.
        switch (e.getCause())
        {
            case FLINT_AND_STEEL, FIREBALL, ARROW ->
            {
                if (!session.registerMatchFire(e.getBlock().getLocation())) {e.setCancelled(true);}
            }
            default -> e.setCancelled(true);
        }
    }

    /**
     * Игровой огонь держим живым до нашего таймера: ваниль иначе гасит его на
     * негорючей поверхности почти сразу. Огонь, бывший на карте изначально, гаснет
     * как обычно.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e)
    {
        if (e.getBlock().getType() != Material.FIRE) {return;}
        Arena arena = arenaInWorld(e.getBlock().getWorld());
        if (arena == null) {return;}
        GameSession session = arena.getSession();
        if (session != null && session.isMatchFire(e.getBlock().getLocation())) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e)
    {
        if (arenaInWorld(e.getBlock().getWorld()) != null) {e.setCancelled(true);}
    }

    /**
     * Жидкости в мирах арен не растекаются: источник воды/лавы (разлитый игроком
     * ведром или лежащий на карте) остаётся ровно одним блоком и не заливает
     * соседние. Так вода-спасение при падении и лава для PvP не портят карту и не
     * требуют отдельной чистки — под откат попадает только сам блок источника.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e)
    {
        if (!e.getBlock().isLiquid()) {return;}
        if (arenaInWorld(e.getBlock().getWorld()) != null) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent e)
    {
        if (arenaInWorld(e.getBlock().getWorld()) != null) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e)
    {
        if (e.getRemover() instanceof Player p && plugin.arenas().inSession(p)) {e.setCancelled(true);}
    }

    /**
     * Яйца в матче — метательное оружие, а не инкубатор: вылупившиеся куры
     * остались бы на карте после матча (в сессии они не зарегистрированы).
     */
    @EventHandler(ignoreCancelled = true)
    public void onEggHatch(CreatureSpawnEvent e)
    {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.EGG) {return;}
        if (arenaInWorld(e.getLocation().getWorld()) != null) {e.setCancelled(true);}
    }
}
