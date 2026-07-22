package me.aver005.escape.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.menu.ShopMenu;
import me.aver005.escape.menu.ThemesMenu;
import me.aver005.escape.theme.Theme;
import me.aver005.escape.trader.TraderType;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Валидация арены: CRITICAL (играть нельзя/контент сломан), WARNING
 * (подозрительно), GOOD (сводка). Запускается из /escape check и при enable.
 */
public final class ArenaCheck
{
    public enum Severity {CRITICAL, WARNING, GOOD}

    public record Finding(Severity severity, Component message, String hint) {}

    private ArenaCheck() {}

    public static boolean hasCritical(List<Finding> findings)
    {
        return findings.stream().anyMatch(f -> f.severity() == Severity.CRITICAL);
    }

    public static List<Finding> run(EscapePlugin plugin, Arena arena)
    {
        List<Finding> out = new ArrayList<>();
        String id = arena.getId();

        // ===== мир и базовая геометрия =====
        if (arena.getWorld() == null)
        {
            out.add(crit(Msg.get("check.msg.world-missing", Msg.ph("world", String.valueOf(arena.getWorldName()))), null));
        }
        if (arena.getLobby() == null)
        {
            out.add(crit(Msg.get("check.msg.lobby-missing"), "/escape setlobby " + id));
        }
        if (arena.getSpawns().isEmpty())
        {
            out.add(crit(Msg.get("check.msg.spawns-missing"), "/escape addspawn " + id));
        }
        else if (arena.getSpawns().size() < arena.getMaxPlayers())
        {
            out.add(warn(Msg.get("check.msg.spawns-few",
                Msg.ph("spawns", arena.getSpawns().size()), Msg.ph("max", arena.getMaxPlayers())),
                "/escape addspawn " + id + "  |  /escape setmaxplayers " + id + " " + arena.getSpawns().size()));
        }
        if (arena.getMinPlayers() > arena.getMaxPlayers())
        {
            out.add(crit(Msg.get("check.msg.min-gt-max",
                Msg.ph("min", arena.getMinPlayers()), Msg.ph("max", arena.getMaxPlayers())),
                "/escape setminplayers " + id + " " + Math.min(2, arena.getMaxPlayers())));
        }

        // ===== сундуки и лут =====
        // лут теперь глобальный: суммарное число позиций во всех категориях loot/*.yml
        int lootTotal = 0;
        for (LootCategory cat : plugin.loot().all()) {lootTotal += cat.getLoot().size();}
        if (lootTotal == 0)
        {
            out.add(crit(Msg.get("check.msg.loot-empty"), "/escape loot"));
        }
        else if (lootTotal < 15)
        {
            out.add(warn(Msg.get("check.msg.loot-small", Msg.ph("n", lootTotal)), "/escape loot"));
        }
        if (arena.getChestSpots().isEmpty() && !arena.isDynamicChests())
        {
            out.add(crit(Msg.get("check.msg.chests-missing"),
                "/escape addchest " + id + "  |  /escape set " + id + " dynamicchests 1"));
        }
        if (arena.getChestSpots().isEmpty() && arena.isDynamicChests())
        {
            out.add(warn(Msg.get("check.msg.insight-undeliverable"), "/escape addchest " + id));
        }

        // точки сундуков ссылаются на ГЛОБАЛЬНЫЕ категории лута (0..N id на точку)
        int orphanPoints = 0;   // точка ссылается на несуществующую категорию
        int emptyPoints = 0;    // точка без категорий вообще -> сундук не появится
        for (List<String> cats : arena.getChestSpots().values())
        {
            if (cats == null || cats.isEmpty()) {emptyPoints++; continue;}
            for (String catId : cats)
            {
                if (!plugin.loot().exists(catId)) {orphanPoints++; break;}
            }
        }
        if (orphanPoints > 0)
        {
            out.add(warn(Msg.get("check.msg.cat-orphan-points", Msg.ph("n", orphanPoints)),
                "/escape chesttag " + id));
        }
        if (emptyPoints > 0)
        {
            out.add(warn(Msg.get("check.msg.chest-points-empty", Msg.ph("n", emptyPoints)),
                "/escape chesttag " + id));
        }
        if (arena.getTraderCount() > arena.getTraderSpots().size())
        {
            out.add(warn(Msg.get("check.msg.trader-count-high",
                Msg.ph("count", arena.getTraderCount()), Msg.ph("spots", arena.getTraderSpots().size())),
                "/escape set " + id + " traders " + arena.getTraderSpots().size()));
        }
        if (arena.getTableCount() > arena.getTableSpots().size())
        {
            out.add(warn(Msg.get("check.msg.table-count-high",
                Msg.ph("count", arena.getTableCount()), Msg.ph("spots", arena.getTableSpots().size())),
                "/escape set " + id + " tables " + arena.getTableSpots().size()));
        }

        // ===== тайминги =====
        if (arena.getGlowSecondsBeforeEnd() >= arena.getDurationSeconds() / 2)
        {
            out.add(warn(Msg.get("check.msg.glow-long",
                Msg.ph("glow", arena.getGlowSecondsBeforeEnd()), Msg.ph("duration", arena.getDurationSeconds())),
                "/escape set " + id + " glowtime 90"));
        }
        if (arena.getSalaryIntervalSeconds() >= arena.getDurationSeconds())
        {
            out.add(warn(Msg.get("check.msg.salary-never"), "/escape set " + id + " salaryinterval 600"));
        }
        if (arena.getEventIntervalSeconds() >= arena.getDurationSeconds())
        {
            out.add(warn(Msg.get("check.msg.events-never"), "/escape set " + id + " eventinterval 210"));
        }

        // ===== торговцы/NPC на точках =====
        Set<String> placedNpcTypes = new LinkedHashSet<>(arena.getTraderSpots().values());
        int overseers = 0;
        for (String typeId : placedNpcTypes)
        {
            TraderType npc = plugin.traders().get(typeId);
            if (npc == null)
            {
                out.add(crit(Msg.get("check.msg.npc-type-missing", Msg.ph("npc", typeId)),
                    "/escape createvillager " + typeId));
                continue;
            }
            if (npc.isOverseer()) {overseers++;}
            if (!npc.isShop() && !npc.isOverseer() && !npc.isScavenger())
            {
                out.add(warn(Msg.get("check.msg.npc-empty", Msg.ph("npc", typeId)),
                    "/escape addtrade " + typeId + "  |  /escape addtheme " + typeId + " <TID>"));
            }
            if (npc.getTrades().size() > ShopMenu.MAX_TRADES)
            {
                out.add(warn(Msg.get("check.msg.npc-trades-overflow",
                    Msg.ph("npc", typeId), Msg.ph("n", npc.getTrades().size()),
                    Msg.ph("max", ShopMenu.MAX_TRADES)),
                    "traders.yml: " + typeId + " -> trades"));
            }
            if (npc.getThemes().size() > ThemesMenu.MAX_THEMES)
            {
                out.add(warn(Msg.get("check.msg.npc-themes-overflow",
                    Msg.ph("npc", typeId), Msg.ph("n", npc.getThemes().size()),
                    Msg.ph("max", ThemesMenu.MAX_THEMES)),
                    "/escape removetheme " + typeId + " <TID>"));
            }
            for (String themeId : npc.getThemes())
            {
                checkTheme(plugin, arena, npc, themeId, out);
            }
        }

        // ===== контракты арены =====
        int contractsOk = 0;
        for (String cid : arena.getContractIds())
        {
            Contract contract = plugin.contracts().get(cid);
            if (contract == null)
            {
                out.add(crit(Msg.get("check.msg.contract-missing", Msg.ph("contract", cid)),
                    "/escape createcontract " + cid));
                continue;
            }
            if (contract.getType() == null)
            {
                out.add(crit(Msg.get("check.msg.contract-no-type", Msg.ph("contract", cid)),
                    "/escape contracttype " + cid + " <Type>"));
                continue;
            }
            String idle = contract.getIdle();
            switch (contract.getType())
            {
                case ACTIVATE ->
                {
                    if (!arena.getLevers().containsValue(idle))
                    {
                        out.add(crit(Msg.get("check.msg.contract-lever-missing",
                            Msg.ph("contract", cid), Msg.ph("lever", idle)),
                            "/escape addlever " + id + " " + idle + "  |  /escape contractidle " + cid + " <рычаг>"));
                        continue;
                    }
                }
                case MINE, BREAK, FIND ->
                {
                    if (Material.matchMaterial(idle) == null)
                    {
                        out.add(crit(Msg.get("check.msg.contract-bad-material",
                            Msg.ph("contract", cid), Msg.ph("material", idle)),
                            "/escape contractidle " + cid + " <MATERIAL>"));
                        continue;
                    }
                }
                default -> {}
            }
            contractsOk++;
        }
        if (arena.getContractIds().isEmpty())
        {
            out.add(warn(Msg.get("check.msg.no-contracts"), "/escape addcontract " + id + " <CID>"));
        }

        // ===== сводка (GOOD) =====
        out.add(good(Msg.get("check.msg.summary-geometry",
            Msg.ph("spawns", arena.getSpawns().size()),
            Msg.ph("finals", arena.getFinalSpawns().isEmpty()
                ? Msg.raw("check.msg.finals-fallback") : String.valueOf(arena.getFinalSpawns().size())),
            Msg.ph("ores", arena.getOreSpots().size()),
            Msg.ph("levers", arena.getLevers().size()),
            Msg.ph("tables", arena.getTableSpots().size()))));
        if (arena.isDynamicChests())
        {
            out.add(good(Msg.get("check.msg.summary-chests-dynamic",
                Msg.ph("spots", arena.getChestSpots().size()))));
        }
        else
        {
            out.add(good(Msg.get("check.msg.summary-chests-static",
                Msg.ph("spots", arena.getChestSpots().size()),
                Msg.ph("cats", plugin.loot().ids().size()),
                Msg.ph("empty", emptyPoints))));
        }
        out.add(good(Msg.get("check.msg.summary-npc",
            Msg.ph("spots", arena.getTraderSpots().size()),
            Msg.ph("types", placedNpcTypes.size()),
            Msg.ph("overseers", overseers))));
        out.add(good(Msg.get("check.msg.summary-content",
            Msg.ph("contracts", contractsOk),
            Msg.ph("loot", lootTotal))));
        return out;
    }

    private static void checkTheme(EscapePlugin plugin, Arena arena, TraderType npc, String themeId, List<Finding> out)
    {
        Theme theme = plugin.themes().get(themeId);
        if (theme == null)
        {
            out.add(crit(Msg.get("check.msg.theme-missing",
                Msg.ph("theme", themeId), Msg.ph("npc", npc.getId())),
                "/escape createtheme " + themeId + "  |  /escape removetheme " + npc.getId() + " " + themeId));
            return;
        }
        if (theme.getType() == null)
        {
            out.add(crit(Msg.get("check.msg.theme-no-type", Msg.ph("theme", themeId)),
                "/escape themetype " + themeId + " <Type>"));
            return;
        }
        String idle = theme.getIdle();
        switch (theme.getType())
        {
            case ACTIVATE ->
            {
                if (!arena.getLevers().containsValue(idle))
                {
                    out.add(crit(Msg.get("check.msg.theme-lever-missing",
                        Msg.ph("theme", themeId), Msg.ph("lever", idle)),
                        "/escape themeidle " + themeId + " <рычаг>"));
                }
            }
            case MINE, BREAK, FIND, DELIVERY ->
            {
                if (Material.matchMaterial(idle) == null)
                {
                    out.add(crit(Msg.get("check.msg.theme-bad-material",
                        Msg.ph("theme", themeId), Msg.ph("material", idle)),
                        "/escape themeidle " + themeId + " <MATERIAL>"));
                }
            }
            case COURIER ->
            {
                String target = theme.turnInTarget();
                if (target == null || !plugin.traders().exists(target))
                {
                    out.add(crit(Msg.get("check.msg.theme-courier-target",
                        Msg.ph("theme", themeId), Msg.ph("target", String.valueOf(theme.getTurnIn()))),
                        "/escape themereturn " + themeId + " <ID типа NPC>"));
                }
            }
            default -> {}
        }
    }

    // ===== отчёт =====

    public static void report(Player p, Arena arena, List<Finding> findings)
    {
        long criticals = findings.stream().filter(f -> f.severity() == Severity.CRITICAL).count();
        long warnings = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();

        Msg.send(p, "check.header", Msg.ph("arena", arena.getId()));
        for (Finding finding : findings)
        {
            String lineKey = switch (finding.severity())
            {
                case CRITICAL -> "check.line-critical";
                case WARNING -> "check.line-warning";
                case GOOD -> "check.line-good";
            };
            p.sendMessage(Msg.get(lineKey, Msg.phC("message", finding.message())));
            if (finding.hint() != null)
            {
                p.sendMessage(Msg.get("check.hint", Msg.ph("command", finding.hint())));
            }
        }
        Msg.send(p, "check.summary", Msg.ph("criticals", criticals), Msg.ph("warnings", warnings));
    }

    private static Finding crit(Component message, String hint) {return new Finding(Severity.CRITICAL, message, hint);}
    private static Finding warn(Component message, String hint) {return new Finding(Severity.WARNING, message, hint);}
    private static Finding good(Component message) {return new Finding(Severity.GOOD, message, null);}
}
