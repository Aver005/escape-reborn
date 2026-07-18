package me.aver005.escape.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Изолированный канал чата (лобби / игра / наблюдатели). */
public class ChatChannel
{
    private final String formatKey;
    private final Set<UUID> members = new HashSet<>();

    public ChatChannel(String formatKey) {this.formatKey = formatKey;}

    public void add(UUID player) {members.add(player);}
    public void remove(UUID player) {members.remove(player);}
    public boolean contains(UUID player) {return members.contains(player);}
    public void clear() {members.clear();}
    public Set<UUID> members() {return members;}

    /** Системное сообщение всем участникам канала. */
    public void system(Component message)
    {
        for (UUID id : members)
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {p.sendMessage(message);}
        }
    }

    public void systemKey(String key, TagResolver... resolvers)
    {
        system(Msg.get(key, resolvers));
    }

    /** Реплика игрока в канал. Потокобезопасно (вызывается из async chat). */
    public void chat(Player author, String plainMessage)
    {
        Component line = Msg.get(formatKey,
            Msg.ph("player", author.getName()),
            Msg.ph("message", plainMessage));
        for (UUID id : members)
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {p.sendMessage(line);}
        }
    }
}
