package me.aver005.escape.game;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;

/** Per-match данные участника. */
public class MatchPlayer
{
    public final UUID uuid;
    public final String name;

    public int kills = 0;
    public int quests = 0;
    public int trades = 0;
    public int ores = 0;

    /** Сундуки, которые игрок уже «облутал» (для LOOT-контрактов). */
    public final Set<Location> lootedChests = new HashSet<>();

    /** Последний ударивший (для кредита убийства), и когда. */
    public UUID lastDamager = null;
    public long lastDamagerAt = 0L;

    // ===== активная темка (задание смотрящего) =====
    public String themeId = null;
    public int themeProgress = 0;
    /** Сущность-житель, выдавшая темку (для сдачи SELF). */
    public UUID themeIssuer = null;
    /** До какого момента нельзя брать новую темку (после броска). */
    public long themeCooldownUntil = 0L;

    public MatchPlayer(UUID uuid, String name)
    {
        this.uuid = uuid;
        this.name = name;
    }
}
