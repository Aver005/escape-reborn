package me.aver005.escape.game;

import java.util.UUID;

import org.bukkit.Location;

/** Блок возрождения одного игрока (данные живут в сессии, предмет — лишь «ручка»). */
public class RespawnBlock
{
    public final UUID owner;
    public final String ownerName;

    public RespawnTier tier = RespawnTier.COPPER;
    public int charges = RespawnTier.START_CHARGES;
    /** Убийства владельца с момента прокачки до алмазного (для изумрудного). */
    public int killsSinceDiamond = 0;
    /** null — блок не установлен (носится в инвентаре). */
    public Location placedAt = null;

    public RespawnBlock(UUID owner, String ownerName)
    {
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public boolean isPlaced() {return placedAt != null;}
}
