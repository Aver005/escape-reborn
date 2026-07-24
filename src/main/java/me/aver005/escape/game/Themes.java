package me.aver005.escape.game;
import me.aver005.escape.util.EscapeKeys;

import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.text.Component;
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
    private final EscapeRules session;

    public Themes(EscapePlugin plugin, EscapeRules session)
    {
        this.plugin = plugin;
        this.session = session;
    }

    public Theme activeOf(Player p)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        return data == null || data.themeId == null ? null : plugin.themes().get(data.themeId);
    }

    // ===== взятие и бросок =====

    /** Секунды кулдауна на новую темку (0 — можно брать). */
    public long cooldownLeft(Player p)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        if (data == null) {return 0;}
        return Math.max(0, (data.themeCooldownUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public boolean take(Player p, Theme theme, Villager issuer)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        if (data == null || !theme.isComplete())
        {
            DebugLog.log(Cat.THEME, "take-deny player=%s theme=%s reason=%s",
                p.getName(), theme.getId(), data == null ? "no-match-data" : "theme-incomplete");
            return false;
        }
        if (data.completedThemes.contains(theme.getId()))
        {
            DebugLog.log(Cat.THEME, "take-deny player=%s theme=%s reason=already-completed", p.getName(), theme.getId());
            Msg.send(p, "theme.already-completed");
            return false;
        }
        if (data.themeId != null)
        {
            DebugLog.log(Cat.THEME, "take-deny player=%s theme=%s reason=active-theme=%s",
                p.getName(), theme.getId(), data.themeId);
            Msg.send(p, "theme.already-active");
            return false;
        }
        long left = cooldownLeft(p);
        if (left > 0)
        {
            DebugLog.log(Cat.THEME, "take-deny player=%s theme=%s reason=cooldown left=%ds",
                p.getName(), theme.getId(), left);
            Msg.send(p, "theme.cooldown", Msg.ph("seconds", left));
            return false;
        }

        data.themeId = theme.getId();
        data.themeProgress = 0;
        data.themeIssuer = issuer != null ? issuer.getUniqueId() : null;
        DebugLog.log(Cat.THEME, "take player=%s theme=%s type=%s amount=%d gold=%d turn-in=%s",
            p.getName(), theme.getId(), theme.getType(), theme.getAmount(), theme.getGold(), theme.getTurnIn());

        if (theme.getType() == ThemeType.COURIER)
        {
            giveOrDrop(p, createPackage(theme, p));
        }

        Msg.send(p, "theme.taken", Msg.phMm("description", theme.getDescription()), Msg.ph("n", theme.getGold()));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
        return true;
    }

    public void abandon(Player p)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        if (data == null || data.themeId == null) {return;}
        String themeId = data.themeId;
        clearActive(data);

        int cooldown = Math.max(0, plugin.getConfig().getInt("themes.drop-cooldown-seconds", 60));
        data.themeCooldownUntil = System.currentTimeMillis() + cooldown * 1000L;
        DebugLog.log(Cat.THEME, "abandon player=%s theme=%s cooldown=%ds", p.getName(), themeId, cooldown);

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

    private void clearActive(EscapePlayerData data)
    {
        data.themeId = null;
        data.themeProgress = 0;
        data.themeIssuer = null;
    }

    // ===== прогресс (KILLS/ACTIVATE/MINE/BREAK/LOOT) =====

    public void progress(Player p, ThemeType type, Predicate<Theme> matcher, int delta)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null || theme.getType() != type || !matcher.test(theme)) {return;}
        if (data.themeProgress >= theme.getAmount()) {return;}

        data.themeProgress = Math.min(theme.getAmount(), data.themeProgress + delta);
        DebugLog.log(Cat.THEME, "progress player=%s theme=%s type=%s %d/%d",
            p.getName(), theme.getId(), type, data.themeProgress, theme.getAmount());
        Msg.send(p, "theme.progress",
            Msg.ph("progress", data.themeProgress), Msg.ph("amount", theme.getAmount()));
        if (data.themeProgress >= theme.getAmount()) {announceReady(p, theme);}
    }

    /** FIND: прогресс = сколько предметов-целей у игрока сейчас (максимум запоминается). */
    public void refreshFind(Player p)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null || theme.getType() != ThemeType.FIND) {return;}
        Material target = Material.matchMaterial(theme.getIdle());
        if (target == null) {return;}

        int count = Math.min(theme.getAmount(), Items.countMaterial(p, target));
        if (count <= data.themeProgress) {return;}
        data.themeProgress = count;
        DebugLog.log(Cat.THEME, "progress-find player=%s theme=%s target=%s %d/%d",
            p.getName(), theme.getId(), target, data.themeProgress, theme.getAmount());
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
            : Component.empty()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
    }

    // ===== сдача =====

    /** Пытается сдать что-нибудь этому NPC. true — сдача произошла, меню не открывать. */
    public boolean tryTurnIn(Player p, TraderType npc, Villager entity)
    {
        if (deliverPackages(p, npc)) {return true;}

        EscapePlayerData data = session.matchData(p.getUniqueId());
        Theme theme = activeOf(p);
        if (data == null || theme == null) {return false;}
        if (!turnInMatches(theme, npc, entity, data))
        {
            DebugLog.log(Cat.THEME, "turn-in-deny player=%s theme=%s npc=%s reason=wrong-npc turn-in=%s",
                p.getName(), theme.getId(), npc.getId(), theme.getTurnIn());
            return false;
        }

        switch (theme.getType())
        {
            case COURIER -> {return false;} // только через пакет
            case DELIVERY ->
            {
                Material target = Material.matchMaterial(theme.getIdle());
                if (target == null || Items.countMaterial(p, target) < theme.getAmount())
                {
                    DebugLog.log(Cat.THEME, "turn-in-deny player=%s theme=%s reason=no-items target=%s need=%d",
                        p.getName(), theme.getId(), theme.getIdle(), theme.getAmount());
                    return false;
                }
                Items.takeMaterial(p, target, theme.getAmount());
                DebugLog.log(Cat.THEME, "delivery-take player=%s theme=%s item=%s amount=%d",
                    p.getName(), theme.getId(), target, theme.getAmount());
            }
            case FIND ->
            {
                Material target = Material.matchMaterial(theme.getIdle());
                if (target == null || Items.countMaterial(p, target) < theme.getAmount())
                {
                    DebugLog.log(Cat.THEME, "turn-in-deny player=%s theme=%s reason=no-items target=%s need=%d",
                        p.getName(), theme.getId(), theme.getIdle(), theme.getAmount());
                    return false;
                }
            }
            default ->
            {
                if (data.themeProgress < theme.getAmount())
                {
                    DebugLog.log(Cat.THEME, "turn-in-deny player=%s theme=%s reason=progress %d/%d",
                        p.getName(), theme.getId(), data.themeProgress, theme.getAmount());
                    return false;
                }
            }
        }

        DebugLog.log(Cat.THEME, "turn-in player=%s theme=%s type=%s npc=%s",
            p.getName(), theme.getId(), theme.getType(), npc.getId());
        clearActive(data);
        reward(p, theme);
        return true;
    }

    private boolean turnInMatches(Theme theme, TraderType npc, Villager entity, EscapePlayerData data)
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
                .get(EscapeKeys.THEME_OWNER, PersistentDataType.STRING);
            if (ownerRaw != null)
            {
                EscapePlayerData owner = session.matchData(UUID.fromString(ownerRaw));
                if (owner != null && themeId.equals(owner.themeId)) {clearActive(owner);}
            }

            DebugLog.log(Cat.THEME, "package-delivered carrier=%s theme=%s npc=%s owner=%s",
                p.getName(), themeId, npc.getId(), ownerRaw == null ? "-" : ownerRaw);
            item.setAmount(item.getAmount() - 1);
            reward(p, theme);
            return true;
        }
        return false;
    }

    private void reward(Player p, Theme theme)
    {
        session.giveGold(p, theme.getGold());
        EscapePlayerData data = session.matchData(p.getUniqueId());
        if (data != null)
        {
            data.quests++;
            data.completedThemes.add(theme.getId());
        }
        plugin.stats().add(p.getUniqueId(), p.getName(), "quests_completed", 1);
        Msg.send(p, "theme.complete", Msg.ph("n", theme.getGold()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        DebugLog.log(Cat.THEME, "reward player=%s theme=%s gold=%d quests=%d",
            p.getName(), theme.getId(), theme.getGold(), data == null ? -1 : data.quests);

        double chance = plugin.getConfig().getDouble("themes.key-chance", 0.10);
        if (RANDOM.nextDouble() < chance)
        {
            DebugLog.log(Cat.THEME, "key-drop player=%s chance=%.2f", p.getName(), chance);
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
                target != null ? target.displayName() : Component.empty())),
            "theme_package");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(EscapeKeys.THEME_ID, PersistentDataType.STRING, theme.getId());
        meta.getPersistentDataContainer().set(EscapeKeys.THEME_OWNER, PersistentDataType.STRING,
            owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private String packageThemeId(ItemStack item)
    {
        if (item == null || !item.hasItemMeta()) {return null;}
        return item.getItemMeta().getPersistentDataContainer().get(EscapeKeys.THEME_ID, PersistentDataType.STRING);
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
            DebugLog.log(Cat.THEME, "key-deny player=%s at=%s reason=not-game-chest",
                p.getName(), DebugLog.at(chest.getLocation()));
            Msg.send(p, "theme.key-wrong-chest");
            return;
        }
        DebugLog.log(Cat.THEME, "key-used player=%s at=%s", p.getName(), DebugLog.at(chest.getLocation()));
        key.setAmount(key.getAmount() - 1);
        Location center = chest.getLocation().add(0.5, 1.0, 0.5);
        center.getWorld().spawnParticle(Particle.ENCHANT, center, 40, 0.4, 0.4, 0.4, 0.5);
        center.getWorld().playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        Msg.send(p, "theme.key-used");
    }

    /** Отладка: заполнить прогресс активной темки до цели. */
    public boolean debugComplete(Player p)
    {
        EscapePlayerData data = session.matchData(p.getUniqueId());
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
