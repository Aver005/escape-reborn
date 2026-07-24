package me.aver005.escape.menu;

import me.aver005.escape.arena.EscapeArena;
import ru.kiviuly.mg.api.menu.Menu;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.game.EscapeRules;
import me.aver005.escape.kit.Kit;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Меню выбора каста в лобби: касты арены + «Без касты» и «Случайная». */
public class KitSelectMenu extends Menu
{
    // спец-значения PDC вместо id каста
    private static final String NONE = "__none__";
    private static final String RANDOM = "__random__";
    // внутренние слоты сетки 54 (4 ряда под касты) + нижний ряд под none/random
    private static final int[] KIT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_NONE = 48;
    private static final int SLOT_RANDOM = 50;

    private final EscapePlugin plugin;
    private final EscapeRules session;
    private final Arena arena;
    private final UUID viewer;

    public KitSelectMenu(EscapePlugin plugin, EscapeRules session, UUID viewer)
    {
        super(54, Msg.get("kit.menu-title"));
        this.plugin = plugin;
        this.session = session;
        this.arena = session.getArena();
        this.viewer = viewer;
        render();
    }

    private void render()
    {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        String choice = session.getChosenKit(viewer);
        String effective = choice != null ? choice : plugin.arenaConfigs().of(arena).defaultKit();

        List<Kit> kits = plugin.kitsFor(arena);
        for (int i = 0; i < kits.size() && i < KIT_SLOTS.length; i++)
        {
            Kit kit = kits.get(i);
            boolean selected = kit.getId().equalsIgnoreCase(effective);
            inventory.setItem(KIT_SLOTS[i], kitIcon(kit, selected));
        }

        boolean noneSel = "none".equalsIgnoreCase(effective);
        boolean randomSel = effective == null || "random".equalsIgnoreCase(effective);
        inventory.setItem(SLOT_NONE, tagged(Material.BARRIER,
            Msg.get("kit.menu-none-name"), Msg.getList("kit.menu-none-lore"), NONE, noneSel));
        inventory.setItem(SLOT_RANDOM, tagged(Material.ENDER_PEARL,
            Msg.get("kit.menu-random-name"), Msg.getList("kit.menu-random-lore"), RANDOM, randomSel));
    }

    private ItemStack kitIcon(Kit kit, boolean selected)
    {
        List<Component> lore = new ArrayList<>();
        for (String line : kit.getLoreRaw()) {lore.add(Items.flat(Msg.mm(line)));}
        lore.add(Component.empty());
        int gold = kit.getGold() >= 0 ? kit.getGold() : EscapeArena.startGold(arena);
        lore.add(Items.flat(Msg.get("kit.menu-gold-line", Msg.ph("n", gold))));
        for (ItemStack item : kit.getItems())
        {
            lore.add(Items.flat(Component.text("  " + item.getAmount() + "x ", NamedTextColor.GRAY)
                .append(item.effectiveName())));
        }
        lore.add(Component.empty());
        lore.add(Items.flat(Msg.get(selected ? "kit.menu-selected-line" : "kit.menu-click-hint")));
        return tagged(kit.getIcon(), Msg.mm(kit.getNameRaw()), lore, kit.getId(), selected);
    }

    private ItemStack tagged(Material mat, Component name, List<Component> lore, String kitId, boolean selected)
    {
        ItemStack item = Items.named(mat, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(EscapeKeys.KIT_ID, PersistentDataType.STRING, kitId);
        if (selected) {meta.setEnchantmentGlintOverride(true);}
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        if (!session.isLobbyMember(p.getUniqueId())) {p.closeInventory(); return;}
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {return;}
        String tag = clicked.getItemMeta().getPersistentDataContainer()
            .get(EscapeKeys.KIT_ID, PersistentDataType.STRING);
        if (tag == null) {return;}

        switch (tag)
        {
            case NONE ->
            {
                session.setChosenKit(p.getUniqueId(), "none");
                Msg.send(p, "kit.chosen-none");
            }
            case RANDOM ->
            {
                session.setChosenKit(p.getUniqueId(), "random");
                Msg.send(p, "kit.chosen-random");
            }
            default ->
            {
                Kit kit = plugin.kitFor(arena, tag);
                if (kit == null) {return;}
                session.setChosenKit(p.getUniqueId(), kit.getId());
                Msg.send(p, "kit.chosen", Msg.phMm("name", kit.getNameRaw()));
            }
        }
        render();
    }
}
