package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.EscapeRules;
import me.aver005.escape.game.RespawnBlock;
import me.aver005.escape.game.RespawnTier;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Меню прокачки блока возрождения (ПКМ по своему блоку). */
public class RespawnUpgradeMenu extends Menu
{
    private static final int SLOT_INFO = 11;
    private static final int SLOT_UPGRADE = 15;

    private final EscapeRules session;
    private final RespawnBlock block;

    public RespawnUpgradeMenu(EscapePlugin plugin, EscapeRules session, RespawnBlock block)
    {
        super(27, Msg.get("respawn-block.menu-title"));
        this.session = session;
        this.block = block;
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
        render();
    }

    private void render()
    {
        List<Component> info = new ArrayList<>();
        info.add(Msg.get("respawn-block.item-lore-tier", Msg.phC("tier", Msg.get(block.tier.nameKey()))));
        info.add(Msg.get("respawn-block.item-lore-charges", Msg.ph("charges", block.charges)));
        if (block.tier == RespawnTier.DIAMOND)
        {
            info.add(Msg.get("respawn-block.menu-kills-line",
                Msg.ph("kills", block.killsSinceDiamond), Msg.ph("need", RespawnTier.EMERALD_KILLS_REQUIRED)));
        }
        inventory.setItem(SLOT_INFO, Items.named(block.tier.material(),
            Msg.get("respawn-block.item-name"), info));

        RespawnTier next = block.tier.next();
        if (next == null)
        {
            inventory.setItem(SLOT_UPGRADE, Items.named(Material.BARRIER,
                Msg.get("respawn-block.menu-max-name"), Msg.getList("respawn-block.menu-max-lore")));
            return;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("respawn-block.menu-next-tier", Msg.phC("tier", Msg.get(next.nameKey()))));
        lore.add(Msg.get("respawn-block.menu-next-charges", Msg.ph("n", RespawnTier.UPGRADE_CHARGES)));
        lore.add(Component.empty());
        if (next == RespawnTier.EMERALD)
        {
            lore.add(Msg.get("respawn-block.menu-cost-kills",
                Msg.ph("kills", block.killsSinceDiamond), Msg.ph("need", RespawnTier.EMERALD_KILLS_REQUIRED)));
            lore.add(Msg.get("respawn-block.menu-emerald-note", Msg.ph("n", RespawnTier.EMERALD_MIN_ALIVE)));
        }
        else
        {
            lore.add(Msg.get("respawn-block.menu-cost-gold", Msg.ph("cost", next.upgradeCostGold())));
        }
        lore.add(Component.empty());
        lore.add(Msg.get("respawn-block.menu-click-hint"));
        inventory.setItem(SLOT_UPGRADE, Items.named(next.material(),
            Msg.get("respawn-block.menu-upgrade-name"), lore));
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (e.getRawSlot() != SLOT_UPGRADE) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}

        if (session.respawnBlocks().tryUpgrade(p, block))
        {
            render();
        }
    }
}
