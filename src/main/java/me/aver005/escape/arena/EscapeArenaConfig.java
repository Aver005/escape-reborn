package me.aver005.escape.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Игро-специфичный конфиг одной арены Escape: {@code plugins/Escape/game/<ID>.yml}.
 *
 * <p>Платформа хранит у себя только общее (мир, спавны, лимиты, тайминги) плюс числа
 * и точки в «карманах» арены. Контент — списки — платформе не нужен и живёт здесь.</p>
 *
 * <p>Киты теперь ГЛОБАЛЬНЫЕ (общий {@code kits.yml} через KitRegistry), а арена хранит
 * лишь список разрешённых на ней id и выбор по умолчанию. Пустой список разрешённых =
 * доступны все киты реестра.</p>
 */
public final class EscapeArenaConfig
{
    private final String arenaId;

    /** id разрешённых на арене китов; пусто = разрешены все из глобального реестра. */
    private final List<String> allowedKits = new ArrayList<>();
    /** Выбор по умолчанию: {@code random} | {@code none} | id кита. */
    private String defaultKit = "random";
    /** id контрактов, доступных на арене. */
    private final List<String> contractIds = new ArrayList<>();
    /** Фразы, показываемые при гибели. */
    private final List<String> deadMessages = new ArrayList<>();

    public EscapeArenaConfig(String arenaId) {this.arenaId = arenaId;}

    public static EscapeArenaConfig load(String arenaId, File file)
    {
        EscapeArenaConfig c = new EscapeArenaConfig(arenaId);
        if (!file.exists()) {return c;}
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        c.allowedKits.addAll(cfg.getStringList("allowed-kits"));
        c.defaultKit = cfg.getString("default-kit", "random");
        c.contractIds.addAll(cfg.getStringList("contracts"));
        c.deadMessages.addAll(cfg.getStringList("dead-messages"));
        return c;
    }

    public void save(File file)
    {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("allowed-kits", new ArrayList<>(allowedKits));
        cfg.set("default-kit", defaultKit);
        cfg.set("contracts", new ArrayList<>(contractIds));
        cfg.set("dead-messages", new ArrayList<>(deadMessages));
        try {file.getParentFile().mkdirs(); cfg.save(file);}
        catch (IOException e) {throw new RuntimeException("Failed to save game config for arena " + arenaId, e);}
    }

    public String arenaId() {return arenaId;}

    /** Живой список id разрешённых китов (пусто = все). */
    public List<String> allowedKits() {return allowedKits;}

    /** Разрешён ли кит на этой арене. */
    public boolean allowsKit(String kitId) {return allowedKits.isEmpty() || allowedKits.contains(kitId);}

    public String defaultKit() {return defaultKit;}
    public void setDefaultKit(String v) {this.defaultKit = v == null || v.isBlank() ? "random" : v;}

    public List<String> contractIds() {return contractIds;}
    public List<String> deadMessages() {return deadMessages;}
}
