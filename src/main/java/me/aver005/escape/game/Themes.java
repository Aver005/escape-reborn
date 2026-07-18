package me.aver005.escape.game;

import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Темки — задания смотрящих (docs/07-decisions.md §10).
 * Одна активная темка на игрока; сдача смотрящему (SELF/ANY/конкретному);
 * награда — золото + шанс «Волшебного ключика» (принудительный рефилл сундука).
 */
public class Themes
{
    private static final Random RANDOM = new Random();

    private final EscapePlugin plugin;
    private final GameSession session;

    public Themes(EscapePlugin plugin, GameSession session)
    {
        this.plugin = plugin;
        this.session = session;
    }

    public Theme activeOf(Player p)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        return data == null || data.themeId == null ? null : plugin.themes().get(data.themeId);
    }

    // ===== взятие и бросок =====

    /** Секунды кулдауна на новую темку (0 — можно брать). */
    public long cooldownLeft(Player p)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        if (data == null) {return 0;}
        return Math.max(0, (data.themeCooldownUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public boolean take(Player p, Theme theme, Villager issuer)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        if (data == null || !theme.isComplete()) {return false;}
        if (data.completedThemes.contains(theme.getId())) {Msg.send(p, "theme.already-completed"); return false;}
        if (data.themeId != null) {Msg.send(p, "theme.already-active"); return false;}
        long left = cooldownLeft(p);
        if (left > 0) {Msg.send(p, "theme.cooldown", Msg.ph("seconds", left)); return false;}

        data.themeId = theme.getId();
        data.themeProgress = 0;
        data.themeIssuer = issuer != null ? issuer.getUniqueId() : null;

        if (theme.getType() == ThemeType.COURIER)
        {
            giveOrDrop(p, createPackage(theme, p));
        }

        Msg.send(p, "theme.taken", Msg.phMm("description", theme.getDescription()), Msg.ph("gold", theme.getGold()));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
        return true;
    }

    public void abandon(Player p)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        if (data == null || data.themeId == null) {return;}
        String themeId = data.themeId;
        clearActive(data);

        int cooldown = Math.max(0, plugin.getConfig().getInt("themes.drop-cooldown-seconds", 60));
        data.themeCooldownUntil = System.currentTimeMillis() + cooldown * 1000L;

        // пакет брошенной передачки изымается
        for (ItemStack item : p.getInventory().getContents())
        {
            if (Items.isSpecial(item, "theme_package") && themeId.equals(packageThemeId(item)))
            {
                item.setAmount(0);
            }
        }
        Msg.send(p, "theme.dropped", Msg.ph("seconds", cooldown));
    }

    private void clearActive(MatchPlayer data)
    {
        data.themeId = null;
        data.themeProgress = 0;
        data.themeIssuer = null;
    }

    // ===== прогресс (KILLS/ACTIVATE/MINE/BREAK/LOOT) =====

    public void progress(Player p, ThemeType type, Predicate<Theme> matcher, int delta)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null || theme.getType() != type || !matcher.test(theme)) {return;}
        if (data.themeProgress >= theme.getAmount()) {return;}

        data.themeProgress = Math.min(theme.getAmount(), data.themeProgress + delta);
        Msg.send(p, "theme.progress",
            Msg.ph("progress", data.themeProgress), Msg.ph("amount", theme.getAmount()));
        if (data.themeProgress >= theme.getAmount()) {announceReady(p, theme);}
    }

    /** FIND: прогресс = сколько предметов-целей у игрока сейчас (максимум запоминается). */
    public void refreshFind(Player p)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null || theme.getType() != ThemeType.FIND) {return;}
        Material target = Material.matchMaterial(theme.getIdle());
        if (target == null) {return;}

        int count = Math.min(theme.getAmount(), Items.countMaterial(p, target));
        if (count <= data.themeProgress) {return;}
        data.themeProgress = count;
        Msg.send(p, "theme.progress",
            Msg.ph("progress", data.themeProgress), Msg.ph("amount", theme.getAmount()));
        if (data.themeProgress >= theme.getAmount()) {announceReady(p, theme);}
    }

    private void announceReady(Player p, Theme theme)
    {
        String targetKey = theme.isTurnInAny() ? "theme.ready-any"
            : theme.isTurnInSelf() ? "theme.ready-self" : "theme.ready-target";
        TraderType target = theme.turnInTarget() == null ? null : plugin.traders().get(theme.turnInTarget());
        Msg.send(p, targetKey, Msg.phC("npc", target != null ? target.displayName()
            : net.kyori.adventure.text.Component.empty()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
    }

    // ===== сдача =====

    /** Пытается сдать что-нибудь этому NPC. true — сдача произошла, меню не открывать. */
    public boolean tryTurnIn(Player p, TraderType npc, Villager entity)
    {
        if (deliverPackages(p, npc)) {return true;}

        MatchPlayer data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null) {return false;}
        if (!turnInMatches(theme, npc, entity, data)) {return false;}

        switch (theme.getType())
        {
            case COURIER -> {return false;} // только через пакет
            case DELIVERY ->
            {
                Material target = Material.matchMaterial(theme.getIdle());
                if (target == null || Items.countMaterial(p, target) < theme.getAmount()) {return false;}
                Items.takeMaterial(p, target, theme.getAmount());
            }
            case FIND ->
            {
                Material target = Material.matchMaterial(theme.getIdle());
                if (target == null || Items.countMaterial(p, target) < theme.getAmount()) {return false;}
            }
            default ->
            {
                if (data.themeProgress < theme.getAmount()) {return false;}
            }
        }

        clearActive(data);
        reward(p, theme);
        return true;
    }

    private boolean turnInMatches(Theme theme, TraderType npc, Villager entity, MatchPlayer data)
    {
        if (theme.isTurnInAny()) {return npc.isOverseer();}
        if (theme.isTurnInSelf()) {return entity != null && entity.getUniqueId().equals(data.themeIssuer);}
        return npc.getId().equals(theme.turnInTarget());
    }

    /** Сдача передачек из инвентаря: сдать может кто угодно, кто донёс пакет. */
    private boolean deliverPackages(Player p, TraderType npc)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (!Items.isSpecial(item, "theme_package")) {continue;}
            String themeId = packageThemeId(item);
            Theme theme = themeId == null ? null : plugin.themes().get(themeId);
            if (theme == null) {item.setAmount(0); continue;} // осиротевший пакет
            if (!npc.getId().equals(theme.turnInTarget())) {continue;}

            // взявшему темку — закрыть её (награда достаётся доставившему)
            String ownerRaw = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.THEME_OWNER, PersistentDataType.STRING);
            if (ownerRaw != null)
            {
                MatchPlayer owner = session.matchData(UUID.fromString(ownerRaw));
                if (owner != null && themeId.equals(owner.themeId)) {clearActive(owner);}
            }

            item.setAmount(item.getAmount() - 1);
            reward(p, theme);
            return true;
        }
        return false;
    }

    private void reward(Player p, Theme theme)
    {
        session.giveGold(p, theme.getGold());
        MatchPlayer data = session.matchData(p.getUniqueId());
        if (data != null)
        {
            data.quests++;
            data.completedThemes.add(theme.getId());
        }
        plugin.stats().add(p.getUniqueId(), p.getName(), "quests_completed", 1);
        Msg.send(p, "theme.complete", Msg.ph("gold", theme.getGold()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        double chance = plugin.getConfig().getDouble("themes.key-chance", 0.10);
        if (RANDOM.nextDouble() < chance)
        {
            giveOrDrop(p, createMagicKey());
            Msg.send(p, "theme.key-received");
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.6f);
        }
    }

    // ===== предметы =====

    private ItemStack createPackage(Theme theme, Player owner)
    {
        TraderType target = plugin.traders().get(theme.turnInTarget());
        ItemStack item = Items.special(Material.LEATHER,
            Msg.get("theme.package-name"),
            Msg.getList("theme.package-lore", Msg.phC("npc",
                target != null ? target.displayName() : net.kyori.adventure.text.Component.empty())),
            "theme_package");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.THEME_ID, PersistentDataType.STRING, theme.getId());
        meta.getPersistentDataContainer().set(Keys.THEME_OWNER, PersistentDataType.STRING,
            owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private String packageThemeId(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return null;}
        return item.getItemMeta().getPersistentDataContainer().get(Keys.THEME_ID, PersistentDataType.STRING);
    }

    public ItemStack createMagicKey()
    {
        return Items.special(Material.TRIPWIRE_HOOK,
            Msg.get("theme.key-name"), Msg.getList("theme.key-lore"), "magic_key");
    }

    /** «Волшебный ключик»: ПКМ по игровому сундуку — принудительный рефилл. */
    public void useKey(Player p, ItemStack key, Block chest)
    {
        if (!session.forceRefillChest(chest))
        {
            Msg.send(p, "theme.key-wrong-chest");
            return;
        }
        key.setAmount(key.getAmount() - 1);
        Location center = chest.getLocation().add(0.5, 1.0, 0.5);
        center.getWorld().spawnParticle(Particle.ENCHANT, center, 40, 0.4, 0.4, 0.4, 0.5);
        center.getWorld().playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        Msg.send(p, "theme.key-used");
    }

    /** Отладка: заполнить прогресс активной темки до цели. */
    public boolean debugComplete(Player p)
    {
        MatchPlayer data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null) {return false;}
        data.themeProgress = theme.getAmount();
        announceReady(p, theme);
        return true;
    }

    private void giveOrDrop(Player p, ItemStack item)
    {
        var leftovers = p.getInventory().addItem(item);
        for (ItemStack rest : leftovers.values())
        {
            Item drop = p.getWorld().dropItem(p.getLocation().add(0, 0.5, 0), rest);
            session.trackDrop(drop);
        }
    }
}
