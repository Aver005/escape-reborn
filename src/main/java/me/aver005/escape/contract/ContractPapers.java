package me.aver005.escape.contract;

import java.util.List;

import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Бумага «Задание»: данные в PDC, лор — только отображение. */
public final class ContractPapers
{
    private ContractPapers() {}

    public static ItemStack create(Contract contract)
    {
        ItemStack paper = Items.named(Material.PAPER, Msg.get("contract.item-name"));
        ItemMeta meta = paper.getItemMeta();
        // случайный nonce, чтобы бумаги не складывались в стак (у каждой свой прогресс)
        meta.getPersistentDataContainer().set(Keys.PAPER_NONCE, PersistentDataType.INTEGER,
            java.util.concurrent.ThreadLocalRandom.current().nextInt());
        paper.setItemMeta(meta);
        write(paper, contract, 0);
        return paper;
    }

    public static String contractIdOf(ItemStack item)
    {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {return null;}
        return item.getItemMeta().getPersistentDataContainer().get(Keys.CONTRACT_ID, PersistentDataType.STRING);
    }

    public static int progressOf(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return 0;}
        Integer progress = item.getItemMeta().getPersistentDataContainer().get(Keys.CONTRACT_PROGRESS, PersistentDataType.INTEGER);
        return progress == null ? 0 : progress;
    }

    /** Записывает прогресс в PDC и перерисовывает лор. */
    public static void write(ItemStack item, Contract contract, int progress)
    {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.CONTRACT_ID, PersistentDataType.STRING, contract.getId());
        meta.getPersistentDataContainer().set(Keys.CONTRACT_PROGRESS, PersistentDataType.INTEGER, progress);
        meta.displayName(Items.flat(Msg.get("contract.item-name")));
        meta.lore(List.of(
            Items.flat(Msg.get("contract.item-lore-desc", Msg.phMm("description", contract.getDescription()))),
            Items.flat(Msg.get("contract.item-lore-progress",
                Msg.ph("progress", progress), Msg.ph("amount", contract.getAmount()))),
            Items.flat(Msg.get("contract.item-lore-price", Msg.ph("price", contract.getPrice())))));
        item.setItemMeta(meta);
    }
}
