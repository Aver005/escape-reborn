package me.aver005.escape.menu;
import ru.kiviuly.mg.api.menu.Menu;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Мастер создания жителя: три режима — из инвентаря, из сундука перед игроком,
 * копией другого жителя. Id генерируется автоматически (свободный «VILLAGER…»);
 * переименовать потом можно командой. Товары из инвентаря/сундука получают цену
 * {@link TradeCreate#DEFAULT_PRICE}, правится в списке товаров.
 */
public class TraderCreateMenu extends Menu
{
    private static final int SLOT_FROM_INVENTORY = 11;
    private static final int SLOT_FROM_CHEST = 13;
    private static final int SLOT_FROM_COPY = 15;
    private static final int SLOT_BACK_CREATE = 22;
    private static final String BASE_ID = "VILLAGER";

    private final EscapePlugin plugin;

    public TraderCreateMenu(EscapePlugin plugin)
    {
        super(27, Msg.get("trader-create.title"));
        this.plugin = plugin;
        render();
    }

    private void render()
    {
        inventory.setItem(SLOT_FROM_INVENTORY, Items.named(Material.BUNDLE,
            Msg.get("trader-create.from-inventory-name"), Msg.getList("trader-create.from-inventory-lore")));
        inventory.setItem(SLOT_FROM_CHEST, Items.named(Material.CHEST,
            Msg.get("trader-create.from-chest-name"), Msg.getList("trader-create.from-chest-lore")));
        inventory.setItem(SLOT_FROM_COPY, Items.named(Material.WRITABLE_BOOK,
            Msg.get("trader-create.from-copy-name"), Msg.getList("trader-create.from-copy-lore")));
        inventory.setItem(SLOT_BACK_CREATE, Items.named(Material.OAK_DOOR,
            Msg.get("npc.back-button"), Msg.getList("npc.back-button-lore")));
        fillAll(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        switch (e.getRawSlot())
        {
            case SLOT_FROM_INVENTORY -> createFromInventory(p);
            case SLOT_FROM_CHEST -> createFromChest(p);
            case SLOT_FROM_COPY -> new TraderCopySourceMenu(plugin).open(p);
            case SLOT_BACK_CREATE -> new TraderListMenu(plugin).open(p);
            default -> {}
        }
    }

    private void createFromInventory(Player p)
    {
        String id = TradeCreate.freshId(plugin.traders(), BASE_ID);
        TraderType trader = TradeCreate.fromInventory(p, id);
        if (trader.getTrades().isEmpty()) {Msg.send(p, "trader-create.empty-inventory"); return;}
        saveAndOpen(p, trader, "inventory");
    }

    private void createFromChest(Player p)
    {
        String id = TradeCreate.freshId(plugin.traders(), BASE_ID);
        TraderType trader = TradeCreate.fromTargetChest(p, id);
        if (trader == null) {Msg.send(p, "trader-create.no-target-chest"); return;}
        if (trader.getTrades().isEmpty()) {Msg.send(p, "trader-create.empty-chest"); return;}
        saveAndOpen(p, trader, "chest");
    }

    private void saveAndOpen(Player p, TraderType trader, String source)
    {
        plugin.traders().add(trader);
        plugin.traders().save();
        DebugLog.log(Cat.ADMIN, "trader-create admin=%s trader=%s source=%s trades=%d",
            p.getName(), trader.getId(), source, trader.getTrades().size());
        Msg.send(p, "trader-create.created", Msg.ph("id", trader.getId()));
        new TradeListEditorMenu(plugin, trader).open(p);
    }
}
