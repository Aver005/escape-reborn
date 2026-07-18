package me.aver005.escape.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Каталог сообщений (messages.yml, MiniMessage). */
public final class Msg
{
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static JavaPlugin plugin;
    private static YamlConfiguration config;

    private Msg() {}

    public static void init(JavaPlugin pl)
    {
        plugin = pl;
        reload();
    }

    public static void reload()
    {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {plugin.saveResource("messages.yml", false);}
        config = YamlConfiguration.loadConfiguration(file);
        // дефолты из jar: новые ключи после обновления плагина работают без ручного слияния
        try (java.io.InputStream in = plugin.getResource("messages.yml"))
        {
            if (in != null)
            {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)));
            }
        }
        catch (java.io.IOException ignored) {}
    }

    public static String raw(String key)
    {
        String s = config.getString(key);
        if (s == null) {return "<red>[нет строки: " + key + "]";}
        return s;
    }

    public static Component mm(String rawText, TagResolver... resolvers)
    {
        return MM.deserialize(rawText, resolvers);
    }

    public static Component get(String key, TagResolver... resolvers)
    {
        return MM.deserialize(raw(key), resolvers);
    }

    public static List<Component> getList(String key, TagResolver... resolvers)
    {
        List<Component> out = new ArrayList<>();
        for (String line : config.getStringList(key)) {out.add(MM.deserialize(line, resolvers));}
        return out;
    }

    public static List<String> rawList(String key)
    {
        return config.getStringList(key);
    }

    public static void send(Audience to, String key, TagResolver... resolvers)
    {
        to.sendMessage(get(key, resolvers));
    }

    public static TagResolver ph(String name, Object value)
    {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver phMm(String name, String miniMessageValue)
    {
        return Placeholder.component(name, MM.deserialize(miniMessageValue));
    }

    public static TagResolver phC(String name, Component value)
    {
        return Placeholder.component(name, value);
    }
}
