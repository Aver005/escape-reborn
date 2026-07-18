package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** «Личный помощник» — 4 способности с кулдаунами. */
public class AssistantMenu extends Menu
{
    private static final int SLOT_CHEST = 1;
    private static final int SLOT_PLAYER = 3;
    private static final int SLOT_TRADER = 5;
    private static final int SLOT_PLACES = 7;

    private final EscapePlugin plugin;
    private final GameSession session;

    public AssistantMenu(EscapePlugin plugin, GameSession session)
    {
        super(9, Msg.get("assistant.menu-title"));
        this.plugin = plugin;
        this.session = session;
        fillAll(Material.VINE);
        inventory.setItem(SLOT_CHEST, Items.named(Material.CHEST,
            Msg.get("assistant.nearest-chest-name"), Msg.getList("assistant.nearest-chest-lore")));
        inventory.setItem(SLOT_PLAYER, Items.named(Material.PLAYER_HEAD,
            Msg.get("assistant.random-player-name"), Msg.getList("assistant.random-player-lore")));
        inventory.setItem(SLOT_TRADER, Items.named(Material.GOLD_INGOT,
            Msg.get("assistant.nearest-trader-name"), Msg.getList("assistant.nearest-trader-lore")));
        inventory.setItem(SLOT_PLACES, Items.named(Material.MAP,
            Msg.get("assistant.places-name"), Msg.getList("assistant.places-lore")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}

        switch (e.getRawSlot())
        {
            case SLOT_CHEST -> useNearest(p, "nearest-chest", "assistant.nearest-chest-name",
                new ArrayList<>(session.getActiveChests()), "assistant.no-chests");
            case SLOT_PLAYER -> useRandomPlayer(p);
            case SLOT_TRADER -> useNearest(p, "nearest-trader", "assistant.nearest-trader-name",
                session.getTraderLocations(), "assistant.no-traders");
            case SLOT_PLACES -> usePlaces(p);
            default -> {}
        }
    }

    private boolean checkCooldown(Player p, String ability)
    {
        long left = session.cooldownLeft(p.getUniqueId() + ":" + ability);
        if (left <= 0) {return true;}
        Msg.send(p, "assistant.cooldown-1");
        Msg.send(p, "assistant.cooldown-2");
        Msg.send(p, "assistant.cooldown-3", Msg.ph("seconds", left / 1000));
        return false;
    }

    private void startCooldown(Player p, String ability, String configKey)
    {
        int seconds = plugin.getConfig().getInt("assistant." + configKey, 120);
        session.setCooldown(p.getUniqueId() + ":" + ability, seconds * 1000L);
    }

    private void sendCoords(Player p, String headerKey, Location loc)
    {
        Msg.send(p, "assistant.used-header", Msg.phC("ability", Msg.get(headerKey)));
        if (Items.pointAssistantCompass(p, loc))
        {
            Msg.send(p, "assistant.compass-points");
        }
        else
        {
            // компас утерян (выпал при смерти) — фолбэк на координаты
            Msg.send(p, "assistant.coords",
                Msg.ph("x", loc.getBlockX()), Msg.ph("y", loc.getBlockY()), Msg.ph("z", loc.getBlockZ()));
        }
        p.closeInventory();
    }

    private void useNearest(Player p, String ability, String nameKey, List<Location> pool, String emptyKey)
    {
        if (pool.isEmpty()) {Msg.send(p, emptyKey); return;}
        if (!checkCooldown(p, ability)) {return;}

        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (Location loc : pool)
        {
            if (loc == null || loc.getWorld() != p.getWorld()) {continue;}
            double dist = loc.distanceSquared(p.getLocation());
            if (dist < bestDist) {bestDist = dist; best = loc;}
        }
        if (best == null) {Msg.send(p, emptyKey); return;}

        sendCoords(p, nameKey, best);
        startCooldown(p, ability, ability.equals("nearest-chest") ? "nearest-chest" : "nearest-trader");
    }

    private void useRandomPlayer(Player p)
    {
        List<UUID> others = new ArrayList<>(session.playingSet());
        others.remove(p.getUniqueId());
        others.removeIf(id -> Bukkit.getPlayer(id) == null);
        if (session.playingSet().size() < 3 || others.isEmpty())
        {
            Msg.send(p, "assistant.too-few-players");
            return;
        }
        if (!checkCooldown(p, "random-player")) {return;}

        UUID targetId = others.get(new java.util.Random().nextInt(others.size()));
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {return;}
        sendCoords(p, "assistant.random-player-name", target.getLocation());
        startCooldown(p, "random-player", "random-player");
    }

    private void usePlaces(Player p)
    {
        if (session.getArena().getLevers().isEmpty()) {Msg.send(p, "assistant.no-places"); return;}
        if (!checkCooldown(p, "places")) {return;}
        new PlacesMenu(plugin, session).open(p);
    }
}
