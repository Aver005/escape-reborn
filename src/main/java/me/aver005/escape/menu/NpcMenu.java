package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.trader.TraderType;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Совмещённый NPC (§10, §14): выбор роли — магазин / темки / Мусорщик. */
public class NpcMenu extends Menu
{
    // до трёх ролей — раскладываем по центральным слотам
    private static final int[] BUTTON_SLOTS = {11, 13, 15};

    private enum Role {SHOP, THEMES, SCAVENGER}

    private final EscapePlugin plugin;
    private final GameSession session;
    private final TraderType npc;
    private final Villager villager;
    private final List<Role> roles = new ArrayList<>();

    public NpcMenu(EscapePlugin plugin, GameSession session, TraderType npc, Villager villager)
    {
        super(27, Msg.get("npc.chooser-title-prefix").append(npc.displayName()));
        this.plugin = plugin;
        this.session = session;
        this.npc = npc;
        this.villager = villager;
        fillAll(Material.GRAY_STAINED_GLASS_PANE);

        if (npc.isShop()) {roles.add(Role.SHOP);}
        if (npc.isOverseer()) {roles.add(Role.THEMES);}
        if (npc.isScavenger()) {roles.add(Role.SCAVENGER);}

        // центрируем кнопки: 1 роль -> слот 13, 2 -> 11/15, 3 -> 11/13/15
        int start = roles.size() == 1 ? 1 : 0;
        for (int i = 0; i < roles.size() && i < BUTTON_SLOTS.length; i++)
        {
            inventory.setItem(BUTTON_SLOTS[start + i], button(roles.get(i)));
        }
    }

    private org.bukkit.inventory.ItemStack button(Role role)
    {
        return switch (role)
        {
            case SHOP -> Items.named(Material.GOLD_INGOT,
                Msg.get("npc.shop-button"), Msg.getList("npc.shop-button-lore"));
            case THEMES -> Items.named(Material.WRITABLE_BOOK,
                Msg.get("npc.themes-button"), Msg.getList("npc.themes-button-lore"));
            case SCAVENGER -> Items.named(Material.CAULDRON,
                Msg.get("npc.scavenger-button"), Msg.getList("npc.scavenger-button-lore"));
        };
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}
        int start = roles.size() == 1 ? 1 : 0;
        for (int i = 0; i < roles.size() && i < BUTTON_SLOTS.length; i++)
        {
            if (e.getRawSlot() != BUTTON_SLOTS[start + i]) {continue;}
            switch (roles.get(i))
            {
                case SHOP -> new ShopMenu(plugin, session, npc, villager).open(p);
                case THEMES -> new ThemesMenu(plugin, session, npc, villager, true).open(p);
                case SCAVENGER -> new ScavengerMenu(plugin, session, npc, villager).open(p);
            }
            return;
        }
    }
}
