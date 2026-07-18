package me.aver005.escape.contract;

/** Типы контрактов-заданий. */
public enum ContractType
{
    KILLS,      // убить игроков
    ACTIVATE,   // активировать именованный рычаг
    MINE,       // добыть руду (материал = idle)
    FIND,       // собрать N предметов (материал = idle), не изымаются
    BREAK,      // сломать блоки (материал = idle)
    LOOT;       // облутать (впервые открыть) N сундуков

    public static ContractType parse(String s)
    {
        try {return valueOf(s.toUpperCase());}
        catch (IllegalArgumentException e) {return null;}
    }
}
