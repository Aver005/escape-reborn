package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.game.GameSession;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Приёмка Мусорщика (§14): скупка сломанного снаряжения за золото.
 * Цена = база(материал) x доля износа — чем ушатаннее, тем больше. Принимает
 * только вещи с прочностью, изношенные не меньше порога, и НИКОГДА системные
 * (вилка/помощник/блок возрождения/контракты/темки — по PDC).
 * Клик по вещи в списке продаёт её сразу; предмет берётся из инвентаря игрока,
 * поэтому потерять его в меню нельзя.
 */
public class ScavengerMenu extends Menu
{
    private static final int[] SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_INFO = 4;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_SELL_ALL = 53;

    private final EscapePlugin plugin;
    private final GameSession session;
    private final TraderType trader;
    private final Villager backVillager; // null — без кнопки «назад»

    public ScavengerMenu(EscapePlugin plugin, GameSession session, TraderType trader, Villager backVillager)
    {
        super(54, Msg.get("scavenger.title-prefix").append(trader.displayName()));
        this.plugin = plugin;
        this.session = session;
        this.trader = trader;
        this.backVillager = backVillager;
    }

    @Override
    public void open(Player p)
    {
        render(p);
        super.open(p);
    }

    public void render(Player p)
    {
        inventory.clear();
        fillBorder(Material.BROWN_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_INFO, Items.named(Material.CAULDRON,
            Msg.get("scavenger.info-name"),
            Msg.getList("scavenger.info-lore", Msg.ph("wear", trader.getScrapMinWearPercent()))));
        if (backVillager != null)
        {
            inventory.setItem(SLOT_BACK, Items.named(Material.ARROW,
                Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        }

        ItemStack[] contents = p.getInventory().getContents();
        int shown = 0;
        int total = 0;
        for (int i = 0; i < contents.length && shown < SLOTS.length; i++)
        {
            int price = scrapPrice(contents[i]);
            if (price <= 0) {continue;}
            inventory.setItem(SLOTS[shown], icon(contents[i], i, price));
            shown++;
            total += price;
        }

        if (shown == 0)
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("scavenger.empty-name"), Msg.getList("scavenger.empty-lore")));
            return;
        }
        inventory.setItem(SLOT_SELL_ALL, Items.named(Material.GOLD_BLOCK,
            Msg.get("scavenger.sell-all-name"),
            Msg.getList("scavenger.sell-all-lore", Msg.ph("total", total), Msg.ph("n", shown))));
    }

    /** Иконка продаваемой вещи: копия предмета + цена/износ в лоре + слот инвентаря в PDC. */
    private ItemStack icon(ItemStack source, int slot, int price)
    {
        ItemStack display = source.clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Items.flat(Msg.get("scavenger.price-lore", Msg.ph("price", price), Msg.ph("wear", wearPercent(source)))));
        lore.add(Items.flat(Msg.get("scavenger.sell-hint")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.SCRAP_SLOT, PersistentDataType.INTEGER, slot);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isPlaying(p.getUniqueId())) {return;}
        int raw = e.getRawSlot();
        if (raw == SLOT_BACK && backVillager != null)
        {
            new NpcMenu(plugin, session, trader, backVillager).open(p);
            return;
        }
        if (raw == SLOT_SELL_ALL)
        {
            sellAll(p);
            return;
        }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {return;}
        Integer slot = clicked.getItemMeta().getPersistentDataContainer().get(Keys.SCRAP_SLOT, PersistentDataType.INTEGER);
        if (slot == null) {return;}
        sellSlot(p, slot);
        render(p);
    }

    private void sellSlot(Player p, int slot)
    {
        ItemStack item = p.getInventory().getItem(slot);
        int price = scrapPrice(item);
        if (price <= 0) {return;} // устарело (уже продано/сменилось)
        p.getInventory().setItem(slot, null);
        session.giveGold(p, price);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
        DebugLog.log(Cat.SHOP, "scrap-sell player=%s trader=%s item=%s price=%d",
            p.getName(), trader.getId(), DebugLog.item(item), price);
        Msg.send(p, "scavenger.sold", Msg.ph("price", price), Msg.ph("rest", Items.countMaterial(p, Material.GOLD_INGOT)));
    }

    private void sellAll(Player p)
    {
        ItemStack[] contents = p.getInventory().getContents();
        int total = 0;
        int count = 0;
        for (int i = 0; i < contents.length; i++)
        {
            int price = scrapPrice(contents[i]);
            if (price <= 0) {continue;}
            p.getInventory().setItem(i, null);
            total += price;
            count++;
        }
        if (count == 0) {Msg.send(p, "scavenger.nothing"); return;}
        session.giveGold(p, total);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
        DebugLog.log(Cat.SHOP, "scrap-sell-all player=%s trader=%s items=%d gold=%d",
            p.getName(), trader.getId(), count, total);
        Msg.send(p, "scavenger.sold-all",
            Msg.ph("n", count), Msg.ph("total", total),
            Msg.ph("rest", Items.countMaterial(p, Material.GOLD_INGOT)));
        render(p);
    }

    /** Цена скрапа за вещь или 0, если не принимается. */
    private int scrapPrice(ItemStack item)
    {
        if (item == null || item.getType().isAir()) {return 0;}
        if (hasPluginData(item)) {return 0;}
        int max = item.getType().getMaxDurability();
        if (max <= 0) {return 0;}
        Integer base = trader.getScrapPrices().get(item.getType());
        if (base == null) {return 0;}
        int damage = (item.getItemMeta() instanceof Damageable d) ? d.getDamage() : 0;
        double wear = (double) damage / max;
        if (wear * 100.0 < trader.getScrapMinWearPercent()) {return 0;}
        int price = (int) Math.round(base * wear);
        return Math.max(1, price) * Math.max(1, item.getAmount());
    }

    private int wearPercent(ItemStack item)
    {
        int max = item.getType().getMaxDurability();
        if (max <= 0) {return 0;}
        int damage = (item.getItemMeta() instanceof Damageable d) ? d.getDamage() : 0;
        return (int) Math.round(100.0 * damage / max);
    }

    /** Системные/игровые предметы (вилка, проводник, блок возрождения, контракты, темки, маркеры) не продаются. */
    private static boolean hasPluginData(ItemStack item)
    {
        if (!item.hasItemMeta()) {return false;}
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.SPECIAL_ITEM) || pdc.has(Keys.CONTRACT_ID)
            || pdc.has(Keys.THEME_ID) || pdc.has(Keys.RESPAWN_OWNER) || pdc.has(Keys.MARKER_TYPE);
    }
}
