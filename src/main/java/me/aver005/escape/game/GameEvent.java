package me.aver005.escape.game;

import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Случайные события матча.
 * Жизненный цикл: canStart -> announce (заголовок + строки) -> onAnnounce ->
 * windowSeconds тиков onTick -> resolvePlayer для каждого живого -> onEnd.
 * windowSeconds == 0 — мгновенное событие (только onAnnounce).
 */
public enum GameEvent
{
    // ===== классические (оригинал 2021, исправленные) =====

    ROOF(8, "events.roof-1", "events.roof-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            Location loc = p.getLocation();
            int top = p.getWorld().getMaxHeight();
            boolean covered = false;
            for (int y = loc.getBlockY() + 2; y < top; y++)
            {
                if (!loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).isPassable()) {covered = true; break;}
            }
            if (covered) {Msg.send(p, "events.roof-ok"); return;}
            p.getWorld().strikeLightningEffect(p.getLocation());
            p.damage(4.0);
            Msg.send(p, "events.roof-fail");
        }
    },

    GRASS(8, "events.grass-1", "events.grass-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            if (p.getLocation().clone().add(0, -1, 0).getBlock().getType() == Material.GRASS_BLOCK)
            {
                Msg.send(p, "events.grass-ok");
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 0));
            Msg.send(p, "events.grass-fail");
        }
    },

    SNEAK(8, "events.sneak-1", "events.sneak-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            if (p.isSneaking()) {Msg.send(p, "events.sneak-ok"); return;}
            p.damage(3.0);
            Msg.send(p, "events.sneak-fail");
        }
    },

    GOLD_IN_HAND(8, "events.gold-1", "events.gold-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.GOLD_INGOT && hand.getAmount() == 10)
            {
                Msg.send(p, "events.gold-ok");
                return;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 18, 0));
            Msg.send(p, "events.gold-fail");
        }
    },

    NO_ARMOR(8, "events.armor-1", "events.armor-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            PlayerInventory inv = p.getInventory();
            boolean naked = empty(inv.getHelmet()) && empty(inv.getChestplate())
                && empty(inv.getLeggings()) && empty(inv.getBoots());
            if (!naked) {Msg.send(p, "events.armor-fail"); return;}
            p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 8));
            Msg.send(p, "events.armor-ok");
        }
    },

    // ===== новые (согласованы 2026-07-18) =====

    /** N1 «Отбой»: не двигаться 8 сек, иначе 2 сердца урона. */
    LOCKDOWN(8, "events.lockdown-1", "events.lockdown-2")
    {
        @Override
        public void onAnnounce(GameSession s)
        {
            s.captureEventPositions();
        }

        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            Location start = s.getEventPositions().get(p.getUniqueId());
            boolean moved = start == null
                || start.getWorld() != p.getWorld()
                || start.distanceSquared(p.getLocation()) > 1.0;
            if (!moved) {Msg.send(p, "events.lockdown-ok"); return;}
            p.damage(4.0);
            Msg.send(p, "events.lockdown-fail");
        }
    },

    /** N2 «Обыск»: выбросить любой предмет за 8 сек, иначе случайная вещь выпадает сама. */
    SEARCH(8, "events.search-1", "events.search-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            if (s.isEventFlagged(p)) {Msg.send(p, "events.search-ok"); return;}

            ItemStack[] contents = p.getInventory().getContents();
            java.util.List<Integer> slots = new java.util.ArrayList<>();
            for (int i = 0; i < contents.length; i++)
            {
                if (contents[i] != null && !contents[i].getType().isAir()) {slots.add(i);}
            }
            if (slots.isEmpty()) {Msg.send(p, "events.search-ok"); return;}

            int slot = slots.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(slots.size()));
            ItemStack taken = contents[slot];
            p.getInventory().setItem(slot, null);
            Item drop = p.getWorld().dropItemNaturally(p.getLocation().add(0, 0.5, 0), taken);
            s.trackDrop(drop);
            Msg.send(p, "events.search-fail");
        }
    },

    /** N3 «Взятка надзирателю»: 5 золота в руке — изымается, взамен случайный предмет из лута. */
    BRIBE(8, "events.bribe-1", "events.bribe-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() != Material.GOLD_INGOT || hand.getAmount() < 5)
            {
                Msg.send(p, "events.bribe-skip");
                return;
            }
            hand.setAmount(hand.getAmount() - 5);
            if (!s.giveRandomLoot(p)) {p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 5));}
            else {Msg.send(p, "events.bribe-ok");}
        }
    },

    /** N4 «Подкоп»: сломать любую руду за 20 сек — награда 6 золота. */
    DIG(20, "events.dig-1", "events.dig-2")
    {
        @Override
        public void resolvePlayer(GameSession s, Player p)
        {
            if (!s.isEventFlagged(p)) {Msg.send(p, "events.dig-fail"); return;}
            s.giveGold(p, 6);
            Msg.send(p, "events.dig-ok");
        }
    },

    /** N5 «Кровавая луна»: только ночью; 120 сек урон между игроками x1.5 + всем ночное зрение. */
    BLOOD_MOON(120, "events.bloodmoon-1", "events.bloodmoon-2")
    {
        @Override
        public boolean canStart(GameSession s)
        {
            var world = s.getArena().getWorld();
            return world != null && !world.isDayTime();
        }

        @Override
        public void onAnnounce(GameSession s)
        {
            s.setBloodMoon(true);
            s.forEachPlaying(p ->
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 120, 0, false, false)));
        }

        @Override
        public void onEnd(GameSession s)
        {
            s.setBloodMoon(false);
            s.gameChat().systemKey("events.bloodmoon-end");
        }
    },

    /** N6 «Туман»: 50 сек слепота всем, кто не сидит на корточках. */
    FOG(50, "events.fog-1", "events.fog-2")
    {
        @Override
        public void onTick(GameSession s)
        {
            s.forEachPlaying(p ->
            {
                if (!p.isSneaking())
                {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                }
            });
        }

        @Override
        public void onEnd(GameSession s)
        {
            s.gameChat().systemKey("events.fog-end");
        }
    },

    /** N8 «Голодный паёк»: мгновенно — еда всех падает до 3 полосок. */
    RATION(0, "events.ration-1", "events.ration-2")
    {
        @Override
        public void onAnnounce(GameSession s)
        {
            s.forEachPlaying(p -> p.setFoodLevel(Math.min(p.getFoodLevel(), 6)));
        }
    };

    private final int windowSeconds;
    private final String[] announceKeys;

    GameEvent(int windowSeconds, String... announceKeys)
    {
        this.windowSeconds = windowSeconds;
        this.announceKeys = announceKeys;
    }

    public int windowSeconds() {return windowSeconds;}
    public String[] announceKeys() {return announceKeys;}

    /** Можно ли запустить событие сейчас (например, «кровавая луна» — только ночью). */
    public boolean canStart(GameSession s) {return true;}

    /** Вызывается сразу после объявления. */
    public void onAnnounce(GameSession s) {}

    /** Вызывается каждую секунду, пока окно события открыто. */
    public void onTick(GameSession s) {}

    /** Вызывается для каждого живого игрока по окончании окна. */
    public void resolvePlayer(GameSession s, Player p) {}

    /** Вызывается по окончании окна (после resolvePlayer). */
    public void onEnd(GameSession s) {}

    /** Пустой слот брони приходит как null ИЛИ как предмет AIR — учитываем оба. */
    private static boolean empty(ItemStack item) {return item == null || item.getType().isAir();}
}
