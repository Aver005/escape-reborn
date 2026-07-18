package me.aver005.escape.contract;

import org.bukkit.configuration.ConfigurationSection;

/** Контракт-задание (глобальный, привязывается к аренам по ID). */
public class Contract
{
    private final String id;
    private ContractType type;
    private String idle = "";        // цель: имя материала или имя рычага
    private String description = ""; // MiniMessage
    private int amount = 1;
    private int price = 0;

    public Contract(String id) {this.id = id;}

    public static Contract load(String id, ConfigurationSection sec)
    {
        Contract c = new Contract(id);
        c.type = ContractType.parse(sec.getString("type", ""));
        c.idle = sec.getString("idle", "");
        c.description = sec.getString("description", "");
        c.amount = sec.getInt("amount", 1);
        c.price = sec.getInt("price", 0);
        return c;
    }

    public void save(ConfigurationSection sec)
    {
        sec.set("type", type == null ? null : type.name());
        sec.set("idle", idle);
        sec.set("description", description);
        sec.set("amount", amount);
        sec.set("price", price);
    }

    /** Готов ли контракт к выдаче в сундуках. */
    public boolean isComplete()
    {
        return type != null && amount > 0;
    }

    public String getId() {return id;}
    public ContractType getType() {return type;}
    public void setType(ContractType type) {this.type = type;}
    public String getIdle() {return idle;}
    public void setIdle(String idle) {this.idle = idle;}
    public String getDescription() {return description;}
    public void setDescription(String description) {this.description = description;}
    public int getAmount() {return amount;}
    public void setAmount(int amount) {this.amount = amount;}
    public int getPrice() {return price;}
    public void setPrice(int price) {this.price = price;}
}
