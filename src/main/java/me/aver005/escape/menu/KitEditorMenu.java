package me.aver005.escape.menu;

import java.util.ArrayList;
import java.util.List;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import me.aver005.escape.kit.Kit;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Редактор предметов каста (с пагинацией, нижний ряд — управление): разложите
 * предметы и закройте меню — набор пересобирается из содержимого всех страниц.
 * Броня при выдаче каста авто-надевается. Имя/иконка/золото — через /escape kit.
 */
public class KitEditorMenu extends PaginatedEditorMenu
{
    private final EscapePlugin plugin;
    private final Arena arena;
    private final Kit kit;

    public KitEditorMenu(EscapePlugin plugin, Arena arena, Kit kit)
    {
        super(Msg.get("kit.editor-title-prefix").append(Component.text(kit.getId())), clones(kit));
        this.plugin = plugin;
        this.arena = arena;
        this.kit = kit;
    }

    private static List<ItemStack> clones(Kit kit)
    {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : kit.getItems()) {out.add(item.clone());}
        return out;
    }

    @Override
    protected void onSave(Player p, List<ItemStack> items)
    {
        kit.getItems().clear();
        for (ItemStack item : items) {kit.getItems().add(item.clone());}
        plugin.arenas().save(arena);
        Msg.send(p, "kit.editor-saved", Msg.ph("id", kit.getId()), Msg.ph("arena", arena.getId()));
    }
}
