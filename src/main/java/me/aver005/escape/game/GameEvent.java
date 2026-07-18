package me.aver005.escape.game;

import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Случайные события матча. Объявление -> через 8 секунд проверка каждого живого. */
public enum GameEvent
{
    ROOF("events.roof-1", "events.roof-2")
    {
        @Override
        public boolean check(Player p)
        {
            // Честная проверка: есть ли сплошной блок над головой до верха мира
            Location loc = p.getLocation();
            int top = p.getWorld().getMaxHeight();
            for (int y = loc.getBlockY() + 2; y < top; y++)
            {
                if (!loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).isPassable()) {return true;}
            }
            return false;
        }

        @Override
        public void onFail(Player p)
        {
            p.getWorld().strikeLightningEffect(p.getLocation());
            p.damage(4.0);
            Msg.send(p, "events.roof-fail");
        }

        @Override
        public void onPass(Player p) {Msg.send(p, "events.roof-ok");}
    },

    GRASS("events.grass-1", "events.grass-2")
    {
        @Override
        public boolean check(Player p)
        {
            return p.getLocation().clone().add(0, -1, 0).getBlock().getType() == Material.GRASS_BLOCK;
        }

        @Override
        public void onFail(Player p)
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 0));
            Msg.send(p, "events.grass-fail");
        }

        @Override
        public void onPass(Player p) {Msg.send(p, "events.grass-ok");}
    },

    SNEAK("events.sneak-1", "events.sneak-2")
    {
        @Override
        public boolean check(Player p) {return p.isSneaking();}

        @Override
        public void onFail(Player p)
        {
            p.damage(3.0);
            Msg.send(p, "events.sneak-fail");
        }

        @Override
        public void onPass(Player p) {Msg.send(p, "events.sneak-ok");}
    },

    GOLD_IN_HAND("events.gold-1", "events.gold-2")
    {
        @Override
        public boolean check(Player p)
        {
            ItemStack hand = p.getInventory().getItemInMainHand();
            return hand.getType() == Material.GOLD_INGOT && hand.getAmount() == 10;
        }

        @Override
        public void onFail(Player p)
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 18, 0));
            Msg.send(p, "events.gold-fail");
        }

        @Override
        public void onPass(Player p) {Msg.send(p, "events.gold-ok");}
    },

    NO_ARMOR("events.armor-1", "events.armor-2")
    {
        @Override
        public boolean check(Player p)
        {
            PlayerInventory inv = p.getInventory();
            return inv.getHelmet() == null && inv.getChestplate() == null
                && inv.getLeggings() == null && inv.getBoots() == null;
        }

        @Override
        public void onFail(Player p) {Msg.send(p, "events.armor-fail");}

        @Override
        public void onPass(Player p)
        {
            p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 8));
            Msg.send(p, "events.armor-ok");
        }
    };

    private final String[] announceKeys;

    GameEvent(String... announceKeys) {this.announceKeys = announceKeys;}

    public String[] announceKeys() {return announceKeys;}

    /** true — условие выполнено. */
    public abstract boolean check(Player p);
    public abstract void onFail(Player p);
    public abstract void onPass(Player p);
}
