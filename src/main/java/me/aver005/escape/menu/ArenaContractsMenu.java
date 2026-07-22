package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Контракты арены: список ВСЕХ глобальных контрактов; ЛКМ переключает привязку
 * контракта к этой арене (arena.contractIds). Привязанные помечены. Заменяет
 * команду /escape addcontract визуальным тумблером (как chesttag для категорий).
 */
public class ArenaContractsMenu extends Menu
{
    private final EscapePlugin plugin;
    private final Arena arena;
    private final Map<Integer, String> idBySlot = new HashMap<>();
    private int page;

    public ArenaContractsMenu(EscapePlugin plugin, Arena arena)
    {
        super(54, Msg.get("arena-contracts.title").append(Component.text(arena.getId())));
        this.plugin = plugin;
        this.arena = arena;
    }

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private List<Contract> contracts()
    {
        List<Contract> out = new ArrayList<>();
        for (String cid : plugin.contracts().ids())
        {
            Contract c = plugin.contracts().get(cid);
            if (c != null) {out.add(c);}
        }
        return out;
    }

    private void render()
    {
        inventory.clear();
        idBySlot.clear();
        List<Contract> list = contracts();
        int pages = pageCount(list.size());
        if (page >= pages) {page = pages - 1;}
        if (page < 0) {page = 0;}
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            int index = page * PAGE_SIZE + i;
            if (index >= list.size()) {break;}
            Contract c = list.get(index);
            inventory.setItem(i, icon(c));
            idBySlot.put(i, c.getId());
        }
        renderControls(page, pages, true, Material.BLACK_STAINED_GLASS_PANE);
        if (list.isEmpty())
        {
            inventory.setItem(22, Items.named(Material.BARRIER,
                Msg.get("arena-contracts.empty-name"), Msg.getList("arena-contracts.empty-lore")));
        }
    }

    private ItemStack icon(Contract c)
    {
        boolean attached = arena.getContractIds().contains(c.getId());
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.get("arena-contracts.lore-type",
            Msg.ph("type", c.getType() == null ? Msg.raw("arena-contracts.no-type") : c.getType().name())));
        lore.add(Msg.get(attached ? "arena-contracts.lore-attached" : "arena-contracts.lore-detached"));
        lore.add(Msg.get(attached ? "arena-contracts.pick-detach" : "arena-contracts.pick-attach"));
        Material mat = attached ? Material.WRITTEN_BOOK : Material.PAPER;
        return Items.named(mat, Component.text(c.getId()), lore);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        if (!(e.getWhoClicked() instanceof Player p)) {return;}
        int raw = e.getRawSlot();
        int pages = pageCount(contracts().size());
        if (raw == SLOT_PREV && page > 0) {page--; render(); return;}
        if (raw == SLOT_NEXT && page < pages - 1) {page++; render(); return;}
        if (raw == SLOT_BACK) {new ArenaHubMenu(plugin, arena).open(p); return;}
        if (raw < 0 || raw >= PAGE_SIZE) {return;}

        String id = idBySlot.get(raw);
        if (id == null) {return;}
        Contract c = plugin.contracts().get(id);
        if (c == null) {render(); return;}

        boolean attached = arena.getContractIds().remove(id);
        if (!attached) {arena.getContractIds().add(id);}
        plugin.arenas().save(arena);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, attached ? 0.8f : 1.3f);
        DebugLog.log(Cat.ADMIN, "arena-contract admin=%s arena=%s contract=%s attached=%b",
            p.getName(), arena.getId(), id, !attached);
        Msg.send(p, attached ? "arena-contracts.detached" : "arena-contracts.attached",
            Msg.ph("arena", arena.getId()), Msg.ph("id", id));
        render();
    }
}
