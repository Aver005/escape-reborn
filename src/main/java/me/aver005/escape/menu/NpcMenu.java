package me.aver005.escape.menu;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Совмещённый NPC (§10): выбор роли — магазин или темки. */
public class NpcMenu extends Menu
{
    private static final int SLOT_SHOP = 11;
    private static final int SLOT_THEMES = 15;

    private final EscapePlugin plugin;
    private final GameSession session;
    private final TraderType npc;
    private final Villager villager;

    public NpcMenu(EscapePlugin plugin, GameSession session, TraderType npc, Villager villager)
    {
        super(27, Msg.get("npc.chooser-title-prefix").append(npc.displayName()));
        this.plugin = plugin;
        this.session = session;
        this.npc = npc;
        this.villager = villager;
        fillAll(Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_SHOP, Items.named(Material.GOLD_INGOT,
            Msg.get("npc.shop-button"), Msg.getList("npc.shop-button-lore")));
        inventory.setItem(SLOT_THEMES, Items.named(Material.WRITABLE_BOOK,
            Msg.get("npc.themes-button"), Msg.getList("npc.themes-button-lore")));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_SHOP -> new ShopMenu(plugin, session, npc, villager).open(p);
            case SLOT_THEMES -> new ThemesMenu(plugin, session, npc, villager, true).open(p);
            default -> {}
        }
    }
}
