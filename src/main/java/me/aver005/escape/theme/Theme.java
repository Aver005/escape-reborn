package me.aver005.escape.theme;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Темка — задание смотрящего (глобальный реестр, привязывается к типам NPC).
 * В отличие от контракта, темку надо сдать смотрящему (свойство turnIn).
 */
public class Theme
{
    /** Кому сдавать: тому же жителю, что выдал. */
    public static final String TURN_IN_SELF = "SELF";
    /** Кому сдавать: любому смотрящему. */
    public static final String TURN_IN_ANY = "ANY";

    private final String id;
    private ThemeType type;
    private String idle = "";        // цель: имя материала или имя рычага
    private String description = ""; // MiniMessage
    private int amount = 1;
    private int gold = 0;
    private String turnIn = TURN_IN_SELF; // SELF | ANY | id типа NPC

    public Theme(String id) {this.id = id;}

    public static Theme load(String id, ConfigurationSection sec)
    {
        Theme t = new Theme(id);
        t.type = ThemeType.parse(sec.getString("type", ""));
        t.idle = sec.getString("idle", "");
        t.description = sec.getString("description", "");
        t.amount = sec.getInt("amount", 1);
        t.gold = sec.getInt("gold", 0);
        t.turnIn = sec.getString("turn-in", TURN_IN_SELF);
        return t;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("type", type == null ? null : type.name());
        sec.set("idle", idle);
        sec.set("description", description);
        sec.set("amount", amount);
        sec.set("gold", gold);
        sec.set("turn-in", turnIn);
    }

    /** Готова ли темка к выдаче смотрящими. */
    public boolean isComplete()
    {
        if (type == null || amount <= 0) {return false;}
        // передачку нельзя сдавать «самому себе» — нужен адресат
        return type != ThemeType.COURIER || (!isTurnInSelf() && !isTurnInAny());
    }

    public boolean isTurnInSelf() {return TURN_IN_SELF.equalsIgnoreCase(turnIn);}
    public boolean isTurnInAny() {return TURN_IN_ANY.equalsIgnoreCase(turnIn);}
    /** id типа NPC-адресата (только если не SELF/ANY). */
    public String turnInTarget() {return isTurnInSelf() || isTurnInAny() ? null : turnIn;}

    public String getId() {return id;}
    public ThemeType getType() {return type;}
    public void setType(ThemeType type) {this.type = type;}
    public String getIdle() {return idle;}
    public void setIdle(String idle) {this.idle = idle;}
    public String getDescription() {return description;}
    public void setDescription(String description) {this.description = description;}
    public int getAmount() {return amount;}
    public void setAmount(int amount) {this.amount = amount;}
    public int getGold() {return gold;}
    public void setGold(int gold) {this.gold = gold;}
    public String getTurnIn() {return turnIn;}
    public void setTurnIn(String turnIn) {this.turnIn = turnIn;}
}
