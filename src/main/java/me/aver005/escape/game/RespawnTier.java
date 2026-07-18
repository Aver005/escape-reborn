package me.aver005.escape.game;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Husk;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;

/**
 * Уровни блока возрождения (docs/07-decisions.md §7).
 * У каждого уровня — своя анимация исчезновения (частицы + звук)
 * и свой вид оффлайн-стража (все — подтипы зомби).
 */
public enum RespawnTier
{
    COPPER(Material.COPPER_BLOCK, 8, 0, Particle.ELECTRIC_SPARK, Sound.BLOCK_COPPER_BREAK, Zombie.class),
    IRON(Material.IRON_BLOCK, 12, 32, Particle.CLOUD, Sound.BLOCK_ANVIL_LAND, Husk.class),
    GOLD(Material.GOLD_BLOCK, 16, 56, Particle.WAX_ON, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, PigZombie.class),
    DIAMOND(Material.DIAMOND_BLOCK, 24, 128, Particle.ENCHANT, Sound.BLOCK_AMETHYST_BLOCK_BREAK, Drowned.class),
    EMERALD(Material.EMERALD_BLOCK, 32, 0, Particle.TOTEM_OF_UNDYING, Sound.ENTITY_EVOKER_CAST_SPELL, ZombieVillager.class);

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
    private final Class<? extends Zombie> guardType;

    RespawnTier(Material material, int breakerGold, int upgradeCostGold, Particle particle, Sound sound,
                Class<? extends Zombie> guardType)
    {
        this.material = material;
        this.breakerGold = breakerGold;
        this.upgradeCostGold = upgradeCostGold;
        this.particle = particle;
        this.sound = sound;
        this.guardType = guardType;
    }

    public Material material() {return material;}
    public int breakerGold() {return breakerGold;}
    public int upgradeCostGold() {return upgradeCostGold;}
    public Particle particle() {return particle;}
    public Sound sound() {return sound;}
    public Class<? extends Zombie> guardType() {return guardType;}

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
