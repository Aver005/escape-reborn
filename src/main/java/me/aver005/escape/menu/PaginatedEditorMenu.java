package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * База редакторов «разложи предметы»: свободная раскладка в области 0..44,
 * нижний ряд (45..53) — управление страницами (защищён {@link #isProtectedSlot}
 * от кладки/перетаскивания). Содержимое каждой страницы фиксируется при листании
 * и при закрытии, затем собирается в один список и уходит в {@link #onSave}.
 * «Вперёд» доступно всегда — можно завести новую страницу под новые предметы.
 */
public abstract class PaginatedEditorMenu extends Menu
{
    private final Map<Integer, List<ItemStack>> pages = new HashMap<>();
    private int page;
    private int maxPage;

    protected PaginatedEditorMenu(Component title, List<ItemStack> initial)
    {
        super(54, title);
        for (int i = 0; i < initial.size(); i++)
        {
            pages.computeIfAbsent(i / PAGE_SIZE, k -> new ArrayList<>()).add(initial.get(i));
        }
        maxPage = initial.isEmpty() ? 0 : (initial.size() - 1) / PAGE_SIZE;
    }

    @Override
    public boolean allowsInteraction() {return true;}

    @Override
    public boolean isProtectedSlot(int slot) {return slot >= PAGE_SIZE;}

    @Override
    public void open(Player p)
    {
        render();
        super.open(p);
    }

    private void render()
    {
        inventory.clear();
        List<ItemStack> content = pages.getOrDefault(page, List.of());
        for (int i = 0; i < PAGE_SIZE && i < content.size(); i++)
        {
            inventory.setItem(i, content.get(i));
        }
        renderControls(page, maxPage + 1, false, Material.BLACK_STAINED_GLASS_PANE);
        // «вперёд» всегда: с последней страницы можно завести новую под новые предметы
        inventory.setItem(SLOT_NEXT, Items.named(Material.ARROW, Msg.get("menu.page-next")));
    }

    /** Считать текущую страницу (0..44) обратно в модель. */
    private void capture()
    {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE; i++)
        {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {list.add(item.clone());}
        }
        pages.put(page, list);
    }

    @Override
    public void onClick(InventoryClickEvent e)
    {
        int raw = e.getRawSlot();
        if (raw == SLOT_PREV && page > 0) {capture(); page--; render(); return;}
        if (raw == SLOT_NEXT) {capture(); page++; if (page > maxPage) {maxPage = page;} render(); return;}
        // слоты содержимого (0..44) свободны, ряд управления защищён isProtectedSlot
    }

    @Override
    public void onClose(InventoryCloseEvent e)
    {
        capture();
        List<ItemStack> all = new ArrayList<>();
        for (int pg = 0; pg <= maxPage; pg++)
        {
            all.addAll(pages.getOrDefault(pg, List.of()));
        }
        if (e.getPlayer() instanceof Player p) {onSave(p, all);}
    }

    /** Сохранить собранный со всех страниц список предметов. */
    protected abstract void onSave(Player p, List<ItemStack> items);
}
