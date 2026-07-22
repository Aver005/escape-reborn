package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.kit.Kit;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Касты арены: список стартовых наборов; ЛКМ по касту открывает редактор его
 * предметов ({@link KitEditorMenu}). Имя/иконка/золото/каст-по-умолчанию/создание
 * и копирование глобального каста — командами (подсказки в лоре и на кнопке-инфо).
 */
public class ArenaKitsMenu extends Menu
{
    private static final int SLOT_INFO = 51;

    private final EscapePlugin plugin;
    private final Arena arena;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public ArenaKitsMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("arena-kits.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        idBySlot.clear();
        List<Kit> kits = arena.getKits();
        int pages = pageCount(kits.size());
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            int index = page * PAGE_SIZE + i;
            if (index >= kits.size()) {break;}
            Kit kit = kits.get(index);
            inventory.setItem(i, icon(kit));
            idBySlot.put(i, kit.getId());
        }
        renderControls(page, pages, true, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(SLOT_INFO, Items.named(Material.BOOK,
            Msg.get("arena-kits.info-name"), Msg.getList("arena-kits.info-lore", Msg.ph("arena", arena.getId()))));
        if (kits.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("arena-kits.empty-name"), Msg.getList("arena-kits.empty-lore", Msg.ph("arena", arena.getId()))));
        }
    }

    private ItemStack icon(Kit kit)
    {
        boolean isDefault = kit.getId().equalsIgnoreCase(arena.getDefaultKit());
        ItemStack item = new ItemStack(kit.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.flat(Msg.mm(kit.getNameRaw() == null ? kit.getId() : kit.getNameRaw())));
        List<Component> lore = new ArrayList<>();
        lore.add(Items.flat(Msg.get("arena-kits.lore-id", Msg.ph("id", kit.getId()))));
        lore.add(Items.flat(Msg.get("arena-kits.lore-gold",
            Msg.ph("gold", kit.getGold() < 0 ? Msg.raw("arena-kits.gold-arena") : String.valueOf(kit.getGold())))));
        lore.add(Items.flat(Msg.get("arena-kits.lore-items", Msg.ph("n", kit.getItems().size()))));
        if (isDefault) {lore.add(Items.flat(Msg.get("arena-kits.lore-default")));}
        lore.addAll(Msg.getList("arena-kits.lore-edit"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(arena.getKits().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_BACK) {new ArenaHubMenu(plugin, arena).open(p); return;}
        if (raw == SLOT_INFO) {Msg.send(p, "arena-kits.info-hint", Msg.ph("arena", arena.getId())); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        String id = idBySlot.get(raw);
        if (id == null) {return;}
        Kit kit = arena.getKit(id);
        if (kit == null) {render(); return;}
        new KitEditorMenu(plugin, arena, kit).open(p);
    }
}
