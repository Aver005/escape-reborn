package me.aver005.escape.game;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Уровни блока возрождения (docs/07-decisions.md §7).
 * У каждого уровня — своя анимация исчезновения (частицы + звук).
 */
public enum RespawnTier
{
    COPPER(Material.COPPER_BLOCK, 8, 0, Particle.ELECTRIC_SPARK, Sound.BLOCK_COPPER_BREAK),
    IRON(Material.IRON_BLOCK, 12, 32, Particle.CLOUD, Sound.BLOCK_ANVIL_LAND),
    GOLD(Material.GOLD_BLOCK, 16, 56, Particle.WAX_ON, Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
    DIAMOND(Material.DIAMOND_BLOCK, 24, 128, Particle.ENCHANT, Sound.BLOCK_AMETHYST_BLOCK_BREAK),
    EMERALD(Material.EMERALD_BLOCK, 32, 0, Particle.TOTEM_OF_UNDYING, Sound.ENTITY_EVOKER_CAST_SPELL);

    /** Убийств для прокачки алмазного в изумрудный. */
    public static final int EMERALD_KILLS_REQUIRED = 3;
    /** Минимум живых игроков для прокачки в изумрудный. */
    public static final int EMERALD_MIN_ALIVE = 6;
    /** Сколько зарядов даёт каждая прокачка. */
    public static final int UPGRADE_CHARGES = 2;
    /** Заряды медного блока на старте. */
    public static final int START_CHARGES = 1;

    private final Material material;
    private final int breakerGold;
    private final int upgradeCostGold; // цена прокачки В ЭТОТ уровень (0 = не за золото)
    private final Particle particle;
    private final Sound sound;

    RespawnTier(Material material, int breakerGold, int upgradeCostGold, Particle particle, Sound sound)
    {
        this.material = material;
        this.breakerGold = breakerGold;
        this.upgradeCostGold = upgradeCostGold;
        this.particle = particle;
        this.sound = sound;
    }

    public Material material() {return material;}
    public int breakerGold() {return breakerGold;}
    public int upgradeCostGold() {return upgradeCostGold;}
    public Particle particle() {return particle;}
    public Sound sound() {return sound;}

    public RespawnTier next()
    {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    public static RespawnTier byMaterial(Material mat)
    {
        for (RespawnTier tier : values())
        {
            if (tier.material == mat) {return tier;}
        }
        return null;
    }

    /** Ключ messages.yml для имени уровня. */
    public String nameKey()
    {
        return "respawn-block.tier." + name().toLowerCase();
    }
}
