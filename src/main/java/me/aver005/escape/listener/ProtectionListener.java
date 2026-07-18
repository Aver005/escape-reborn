package me.aver005.escape.listener;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.game.GameSession;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
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
        // как в оригинале: во время матча поджиг разрешён, вне матча — нет
        GameSession session = arena.getSession();
        if (session == null || session.getPhase() != GameSession.Phase.RUNNING) {e.setCancelled(true);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e)
    {
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
}
