package me.aver005.escape.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Игро-специфичные NamespacedKey Escape (PDC-метки предметов и сущностей).
 *
 * <p>Общие ключи платформы ({@code SPECIAL_ITEM}, {@code MARKER_TYPE},
 * {@code MARKER_ARENA}, {@code MARKER_EXTRA}) живут в
 * {@link ru.kiviuly.mg.api.util.Keys} и инициализируются ядром MgCore — здесь
 * только то, что принадлежит самой игре.</p>
 */
public final class EscapeKeys
{
    public static NamespacedKey CONTRACT_ID;
    public static NamespacedKey CONTRACT_PROGRESS;
    public static NamespacedKey LOOT_INDEX;        // индекс записи в редакторе лута
    public static NamespacedKey PAPER_NONCE;       // анти-стак для бумаг-контрактов
    public static NamespacedKey TRADER_TYPE;       // тип торговца на сущности жителя
    public static NamespacedKey RESPAWN_OWNER;     // владелец предмета-блока возрождения
    public static NamespacedKey THEME_ID;          // id темки на пакете-«передачке»
    public static NamespacedKey THEME_OWNER;       // uuid взявшего темку (на пакете)
    public static NamespacedKey KIT_ID;            // id каста на иконке меню выбора
    public static NamespacedKey SCRAP_SLOT;        // индекс слота инвентаря на иконке приёмки Мусорщика
    public static NamespacedKey CATEGORY_ID;       // id категории сундука на жезле мастера настройки

    private EscapeKeys() {}

    public static void init(Plugin plugin)
    {
        CONTRACT_ID = new NamespacedKey(plugin, "contract_id");
        CONTRACT_PROGRESS = new NamespacedKey(plugin, "contract_progress");
        LOOT_INDEX = new NamespacedKey(plugin, "loot_index");
        PAPER_NONCE = new NamespacedKey(plugin, "paper_nonce");
        TRADER_TYPE = new NamespacedKey(plugin, "trader_type");
        RESPAWN_OWNER = new NamespacedKey(plugin, "respawn_owner");
        THEME_ID = new NamespacedKey(plugin, "theme_id");
        THEME_OWNER = new NamespacedKey(plugin, "theme_owner");
        KIT_ID = new NamespacedKey(plugin, "kit_id");
        SCRAP_SLOT = new NamespacedKey(plugin, "scrap_slot");
        CATEGORY_ID = new NamespacedKey(plugin, "category_id");
    }
}
