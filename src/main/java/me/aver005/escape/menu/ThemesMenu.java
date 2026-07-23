package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.game.MatchPlayer;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.trader.TraderType;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Темки смотрящего: список на взятие + активная темка с кнопкой броска. */
public class ThemesMenu extends Menu
{
    /** Список темок занимает слоты 10-16 — больше семи в меню не поместится. */
    public static final int MAX_THEMES = 7;

    private static final int SLOT_ACTIVE = 22;
    private static final int SLOT_BACK = 27;

    private final EscapePlugin plugin;
    private final GameSession session;
    private final TraderType npc;
    private final Villager villager;
    private final boolean showBack;
    private final Map<Integer, Theme> themeBySlot = new HashMap<>();

    public ThemesMenu(EscapePlugin plugin, GameSession session, TraderType npc, Villager villager)
    {
        this(plugin, session, npc, villager, false);
    }

    /** showBack — открыто из меню совмещённого NPC, показать «Назад». */
    public ThemesMenu(EscapePlugin plugin, GameSession session, TraderType npc, Villager villager, boolean showBack)
    {
        super(36, Msg.get("theme.menu-title-prefix").append(npc.displayName()));
        this.plugin = plugin;
        this.session = session;
        this.npc = npc;
        this.villager = villager;
        this.showBack = showBack;
        render(null);
    }

    private void render(Player viewer)
    {
        themeBySlot.clear();
        inventory.clear();
        fillBorder(Material.GREEN_STAINED_GLASS_PANE);

        MatchPlayer data = viewer == null ? null : session.matchData(viewer.getUniqueId());

        int slot = 10;
        for (String themeId : npc.getThemes())
        {
            Theme theme = plugin.themes().get(themeId);
            if (theme == null || !theme.isComplete()) {continue;}
            if (slot > 16) {break;}
            if (data != null && data.completedThemes.contains(themeId))
            {
                inventory.setItem(slot, completedItem(theme));
            }
            else
            {
                inventory.setItem(slot, themeItem(theme));
                themeBySlot.put(slot, theme);
            }
            slot++;
        }

        if (data != null && data.themeId != null)
        {
            Theme active = plugin.themes().get(data.themeId);
            if (active != null)
            {
                inventory.setItem(SLOT_ACTIVE, activeItem(active, data));
            }
        }

        if (showBack)
        {
            inventory.setItem(SLOT_BACK, Items.named(Material.ARROW,
                Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        }
    }

    @Override
    public void open(Player p)
    {
        render(p);
        super.open(p);
    }

    private ItemStack themeItem(Theme theme)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("theme.lore-description", Msg.phMm("description", theme.getDescription())));
        lore.add(Msg.get("theme.lore-gold", Msg.ph("n", theme.getGold())));
        lore.add(turnInLine(theme));
        lore.add(Component.empty());
        lore.add(Msg.get("theme.lore-take"));
        return Items.named(Material.BOOK, Msg.get("theme.item-name", Msg.ph("id", theme.getId())), lore);
    }

    private ItemStack completedItem(Theme theme)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("theme.lore-description", Msg.phMm("description", theme.getDescription())));
        lore.add(Component.empty());
        lore.add(Msg.get("theme.lore-completed"));
        return Items.named(Material.GRAY_DYE, Msg.get("theme.completed-name", Msg.ph("id", theme.getId())), lore);
    }

    private ItemStack activeItem(Theme theme, MatchPlayer data)
    {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("theme.lore-description", Msg.phMm("description", theme.getDescription())));
        lore.add(Msg.get("theme.lore-progress",
            Msg.ph("progress", data.themeProgress), Msg.ph("amount", theme.getAmount())));
        lore.add(Msg.get("theme.lore-gold", Msg.ph("n", theme.getGold())));
        lore.add(turnInLine(theme));
        lore.add(Component.empty());
        lore.add(Msg.get("theme.lore-drop"));
        ItemStack item = Items.named(Material.WRITABLE_BOOK,
            Msg.get("theme.active-name", Msg.ph("id", theme.getId())), lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private Component turnInLine(Theme theme)
    {
        if (theme.isTurnInAny()) {return Msg.get("theme.lore-turn-in-any");}
        if (theme.isTurnInSelf()) {return Msg.get("theme.lore-turn-in-self");}
        TraderType target = plugin.traders().get(theme.turnInTarget());
        return Msg.get("theme.lore-turn-in-target",
            Msg.phC("npc", target != null ? target.displayName() : Component.text(theme.getTurnIn())));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}

        if (showBack && e.getRawSlot() == SLOT_BACK)
        {
            new NpcMenu(plugin, session, npc, villager).open(p);
            return;
        }

        if (e.getRawSlot() == SLOT_ACTIVE)
        {
            session.themes().abandon(p);
            render(p);
            return;
        }

        Theme theme = themeBySlot.get(e.getRawSlot());
        if (theme == null) {return;}
        if (session.themes().take(p, theme, villager))
        {
            p.closeInventory();
        }
    }
}
