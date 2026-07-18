package me.aver005.escape.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Все NamespacedKey плагина (PDC-метки предметов). */
public final class Keys
{
    public static NamespacedKey SPECIAL_ITEM;      // "leave" | "assistant" | "fork"
    public static NamespacedKey CONTRACT_ID;
    public static NamespacedKey CONTRACT_PROGRESS;
    public static NamespacedKey MARKER_TYPE;       // тип маркера настройки арены
    public static NamespacedKey MARKER_ARENA;
    public static NamespacedKey MARKER_EXTRA;      // имя рычага / тип торговца
    public static NamespacedKey LOOT_INDEX;        // индекс записи в редакторе лута
    public static NamespacedKey PAPER_NONCE;       // анти-стак для бумаг-контрактов
    public static NamespacedKey TRADER_TYPE;       // тип торговца на сущности жителя
    public static NamespacedKey RESPAWN_OWNER;     // владелец предмета-блока возрождения
    public static NamespacedKey THEME_ID;          // id темки на пакете-«передачке»
    public static NamespacedKey THEME_OWNER;       // uuid взявшего темку (на пакете)

    private Keys() {}

    public static void init(Plugin plugin)
    {
        SPECIAL_ITEM = new NamespacedKey(plugin, "special_item");
        CONTRACT_ID = new NamespacedKey(plugin, "contract_id");
        CONTRACT_PROGRESS = new NamespacedKey(plugin, "contract_progress");
        MARKER_TYPE = new NamespacedKey(plugin, "marker_type");
        MARKER_ARENA = new NamespacedKey(plugin, "marker_arena");
        MARKER_EXTRA = new NamespacedKey(plugin, "marker_extra");
        LOOT_INDEX = new NamespacedKey(plugin, "loot_index");
        PAPER_NONCE = new NamespacedKey(plugin, "paper_nonce");
        TRADER_TYPE = new NamespacedKey(plugin, "trader_type");
        RESPAWN_OWNER = new NamespacedKey(plugin, "respawn_owner");
        THEME_ID = new NamespacedKey(plugin, "theme_id");
        THEME_OWNER = new NamespacedKey(plugin, "theme_owner");
    }
}
