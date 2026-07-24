package me.aver005.escape.arena;

import ru.kiviuly.mg.api.arena.Arena;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;

/**
 * Состояние одного админа в мастере настройки категорий сундуков.
 * {@code order} — снимок точек арены на момент старта (стабильный порядок из
 * LinkedHashMap): и телепорт, и GUI-прыжки адресуют одну и ту же нумерацию.
 */
public class WizardState
{
    private final UUID admin;
    private final Arena arena;
    private final List<Location> order;
    private int index;

    public WizardState(UUID admin, Arena arena, List<Location> order)
    {
        this.admin = admin;
        this.arena = arena;
        this.order = order;
        this.index = 0;
    }

    public UUID getAdmin() {return admin;}
    public Arena getArena() {return arena;}
    public List<Location> getOrder() {return order;}
    public int getIndex() {return index;}
    public void setIndex(int i) {this.index = i;}
    public int size() {return order.size();}

    public Location current()
    {
        return index >= 0 && index < order.size() ? order.get(index) : null;
    }
}
