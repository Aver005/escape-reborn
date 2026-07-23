package me.aver005.escape.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

import ru.kiviuly.mg.api.util.Items;

/**
 * Игро-специфичные хелперы предметов Escape. Общие сборщики предметов — в
 * {@link ru.kiviuly.mg.api.util.Items} (тулкит платформы).
 */
public final class EscapeItems
{
    private EscapeItems() {}

    /** Навести компас-«помощник» в инвентаре игрока на точку. false — компаса нет. */
    public static boolean pointAssistantCompass(Player p, Location target)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (!Items.isSpecial(item, "assistant")) {continue;}
            if (!(item.getItemMeta() instanceof CompassMeta meta)) {continue;}
            meta.setLodestoneTracked(false);
            meta.setLodestone(target);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }
}
