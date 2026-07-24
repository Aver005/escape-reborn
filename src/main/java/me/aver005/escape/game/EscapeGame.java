package me.aver005.escape.game;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.data.DataKey;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.game.MinigameDescriptor;
import ru.kiviuly.mg.api.util.Msg;

/**
 * Escape как игра платформы: тонкий адаптер между ядром и правилами.
 *
 * <p>Каркас (лобби, отсчёт, фазы, ростер, снапшоты игроков, откат мира, HUD,
 * статистика) ведёт ядро и зовёт хуки. Сами правила Escape живут в
 * {@link EscapeRules} — по объекту на матч, хранится в {@link EscapeState}.</p>
 *
 * <p>Класс без состояния: одна инстанция на плагин, матчей может идти несколько.</p>
 */
public final class EscapeGame extends Minigame
{
    private final EscapePlugin plugin;

    public EscapeGame(EscapePlugin plugin, MgCore core)
    {
        super(core, plugin);
        this.plugin = plugin;
    }

    @Override
    public String id() {return "escape";}

    @Override
    public String displayName() {return Msg.raw("game.display-name");}

    /** Короткий узел прав: esc.admin (плюс общий mg.admin из ядра). */
    @Override
    public String adminPermission() {return "esc.admin";}

    @Override
    public MinigameDescriptor descriptor()
    {
        return new MinigameDescriptor(id(), Component.text("Escape"),
            new ItemStack(Material.IRON_BARS), 2, 12, false);
    }

    /** Правила матча живут в самом матче: по объекту на матч. */
    private static final DataKey<EscapeRules> RULES = DataKey.of("escape-rules", EscapeRules.class);

    /** Правила текущего матча (создаются при первом обращении). */
    public EscapeRules rules(Match m)
    {
        EscapeRules r = m.get(RULES);
        if (r == null) {r = new EscapeRules(plugin, m); m.set(RULES, r);}
        return r;
    }

    // ===== хуки жизненного цикла (ядро -> правила) =====

    @Override
    public void onLobbyJoin(Match m, Player p) {rules(m).onLobbyJoin(p);}

    @Override
    public void onStart(Match m) {rules(m).onStart();}

    @Override
    public void giveLoadout(Match m, Player p) {rules(m).giveLoadout(p);}

    @Override
    public void onTick(Match m) {rules(m).onTick();}

    @Override
    public boolean onLethalDamage(Match m, Player p) {return rules(m).onLethalDamage(p);}

    @Override
    public void onPlayerEliminated(Match m, MatchPlayer mp) {rules(m).onEliminated(mp);}

    @Override
    public void onPlayerRemoved(Match m, UUID id) {rules(m).onPlayerRemoved(id);}

    @Override
    public MatchResult checkResult(Match m) {return rules(m).checkResult();}

    @Override
    public void onEnd(Match m, MatchResult result) {rules(m).onEnd(result);}

    @Override
    public void onCleanup(Match m) {rules(m).onCleanup();}

    @Override
    public List<Component> scoreboardLines(Match m, Player viewer) {return rules(m).scoreboardLines(viewer);}

    @Override
    public void onReload() {plugin.arenaConfigs().clear();}
}
