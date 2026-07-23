package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * GUI-редактор числовых и булевых настроек арены (всё, что раньше меняла команда
 * /escape set + мин/макс игроков). Каждая настройка — кнопка: ЛКМ +1 (Shift +10),
 * ПКМ -1 (Shift -10); булевы (dynamicchests) переключаются любым кликом. Пишется
 * сразу через ArenaManager.save. Открывается из хаба ({@link ArenaHubMenu}).
 */
public class ArenaSettingsMenu extends Menu
{
    private static final int SLOT_BACK_HUB = 49;

    private final EscapePlugin plugin;
    private final Arena arena;
    private final List<Setting> settings;
    private final Map<Integer, Setting> bySlot = new HashMap<>();

    /** Одна редактируемая настройка: слот, ключ имени, иконка, тип, границы, гет/сет. */
    private record Setting(int slot, String key, Material icon, boolean bool, int min, int max,
        IntSupplier get, IntConsumer set) {}

    public ArenaSettingsMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("arena-settings.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
        this.settings = build(arena);
        for (Setting s : settings) {bySlot.put(s.slot(), s);}
        render();
    }

    private static List<Setting> build(Arena a)
    {
        List<Setting> s = new ArrayList<>();
        s.add(new Setting(0, "minplayers", Material.IRON_HELMET, false, 1, 128, a::getMinPlayers, a::setMinPlayers));
        s.add(new Setting(1, "maxplayers", Material.DIAMOND_HELMET, false, 1, 128, a::getMaxPlayers, a::setMaxPlayers));
        s.add(new Setting(2, "duration", Material.CLOCK, false, 1, 100000, a::getDurationSeconds, a::setDurationSeconds));
        s.add(new Setting(3, "startdelay", Material.REPEATER, false, 1, 100000, a::getStartDelaySeconds, a::setStartDelaySeconds));
        s.add(new Setting(4, "startdelayfull", Material.COMPARATOR, false, 1, 100000, a::getStartDelayFullSeconds, a::setStartDelayFullSeconds));
        s.add(new Setting(5, "eventinterval", Material.BELL, false, 1, 100000, a::getEventIntervalSeconds, a::setEventIntervalSeconds));
        s.add(new Setting(6, "salaryinterval", Material.COMPASS, false, 1, 100000, a::getSalaryIntervalSeconds, a::setSalaryIntervalSeconds));
        s.add(new Setting(7, "glowtime", Material.GLOWSTONE, false, 1, 100000, a::getGlowSecondsBeforeEnd, a::setGlowSecondsBeforeEnd));
        s.add(new Setting(9, "salarygold", Material.GOLD_INGOT, false, 1, 100000, a::getSalaryGold, a::setSalaryGold));
        s.add(new Setting(10, "startgold", Material.GOLD_NUGGET, false, 1, 100000, a::getStartGold, a::setStartGold));
        s.add(new Setting(11, "glowgold", Material.GLOWSTONE_DUST, false, 1, 100000, a::getGlowBonusGold, a::setGlowBonusGold));
        s.add(new Setting(12, "forkuses", Material.STICK, false, 1, 100000, a::getForkUses, a::setForkUses));
        s.add(new Setting(14, "traders", Material.EMERALD, false, 1, 100000, a::getTraderCount, a::setTraderCount));
        s.add(new Setting(15, "tables", Material.ENCHANTING_TABLE, false, 1, 100000, a::getTableCount, a::setTableCount));
        s.add(new Setting(16, "wearmin", Material.DAMAGED_ANVIL, false, 0, 99, a::getWearMinPercent, a::setWearMinPercent));
        s.add(new Setting(17, "wearmax", Material.ANVIL, false, 0, 99, a::getWearMaxPercent, a::setWearMaxPercent));
        s.add(new Setting(18, "dynamicchests", Material.TRAPPED_CHEST, true, 0, 1,
            () -> a.isDynamicChests() ? 1 : 0, v -> a.setDynamicChests(v > 0)));
        s.add(new Setting(20, "contractminarena", Material.PAPER, false, 0, 100000, a::getContractsMinPerArena, a::setContractsMinPerArena));
        s.add(new Setting(21, "contractmaxarena", Material.MAP, false, -1, 100000, a::getContractsMaxPerArena, a::setContractsMaxPerArena));
        s.add(new Setting(22, "contractminchest", Material.BOOK, false, 0, 100000, a::getContractsMinPerChest, a::setContractsMinPerChest));
        s.add(new Setting(23, "contractmaxchest", Material.WRITTEN_BOOK, false, 0, 100000, a::getContractsMaxPerChest, a::setContractsMaxPerChest));
        return s;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        for (Setting s : settings) {inventory.setItem(s.slot(), button(s));}
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_BACK_HUB, Items.named(Material.OAK_DOOR,
            Msg.get("arena-hub.back-name"), Msg.getList("arena-hub.back-lore")));
    }

    private ItemStack button(Setting s)
    {
        List<Component> lore = new ArrayList<>();
        if (s.bool())
        {
            boolean on = s.get().getAsInt() > 0;
            lore.add(Msg.get("arena-settings.value", Msg.ph("value", Msg.raw(on ? "arena-settings.state-on" : "arena-settings.state-off"))));
            lore.addAll(Msg.getList("arena-settings.toggle"));
        }
        else
        {
            lore.add(Msg.get("arena-settings.value", Msg.ph("value", fmt(s.get().getAsInt()))));
            if (s.min() < 0) {lore.add(Msg.get("arena-settings.note-unlimited"));}
            lore.addAll(Msg.getList("arena-settings.adjust"));
        }
        return Items.named(s.icon(), Msg.get("arena-settings.name-" + s.key()), lore);
    }

    /** -1 показываем как «∞», иначе число. */
    private String fmt(int v)
    {
        return v < 0 ? Msg.raw("arena-settings.unlimited") : String.valueOf(v);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_BACK_HUB) {new ArenaHubMenu(plugin, arena).open(p); return;}
        Setting s = bySlot.get(raw);
        if (s == null) {return;}

        int cur = s.get().getAsInt();
        int next;
        if (s.bool()) {next = cur > 0 ? 0 : 1;}
        else
        {
            int delta = (e.isLeftClick() ? 1 : -1) * (e.isShiftClick() ? 10 : 1);
            next = Math.max(s.min(), Math.min(s.max(), cur + delta));
        }
        if (next == cur && !s.bool()) {return;}
        s.set().accept(next);
        plugin.arenas().save(arena);
        inventory.setItem(s.slot(), button(s));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, next >= cur ? 1.3f : 0.8f);
        DebugLog.log(Cat.ADMIN, "arena-set admin=%s arena=%s key=%s value=%d",
            p.getName(), arena.getId(), s.key(), next);
    }
}
