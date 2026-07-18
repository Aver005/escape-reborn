package me.aver005.escape.listener;

import java.util.Set;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/** Изоляция чата по каналам сессии и блокировка команд в матче. */
public class ChatListener implements Listener
{
    private static final Set<String> ALLOWED_SUBCOMMANDS = Set.of("leave", "help", "stats", "info");
    private static final Set<String> COMMAND_ALIASES = Set.of("escape", "es", "esc");

    private final EscapePlugin plugin;

    public ChatListener(EscapePlugin plugin) {this.plugin = plugin;}

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent e)
    {
        Player p = e.getPlayer();
        GameSession session = plugin.arenas().sessionOf(p);
        if (session == null) {return;}

        e.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());

        if (session.isLobbyMember(p.getUniqueId())) {session.lobbyChat().chat(p, plain);}
        else if (session.isPlaying(p.getUniqueId())) {session.gameChat().chat(p, plain);}
        else if (session.isSpectating(p.getUniqueId())) {session.spectatorChat().chat(p, plain);}
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e)
    {
        Player p = e.getPlayer();
        if (p.hasPermission("escape.admin")) {return;}
        GameSession session = plugin.arenas().sessionOf(p);
        if (session == null) {return;}

        String[] parts = e.getMessage().substring(1).split("\\s+");
        if (parts.length == 0) {e.setCancelled(true); return;}
        String command = parts[0].toLowerCase();
        int colon = command.indexOf(':');
        if (colon >= 0) {command = command.substring(colon + 1);}

        if (!COMMAND_ALIASES.contains(command)) {e.setCancelled(true); return;}
        if (parts.length >= 2 && !ALLOWED_SUBCOMMANDS.contains(parts[1].toLowerCase()))
        {
            e.setCancelled(true);
        }
    }
}
