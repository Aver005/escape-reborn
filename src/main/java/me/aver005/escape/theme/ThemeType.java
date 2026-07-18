package me.aver005.escape.theme;

/** Типы темок — заданий смотрящих (docs/07-decisions.md §10). */
public enum ThemeType
{
    KILLS,      // убить игроков
    ACTIVATE,   // активировать именованный рычаг
    MINE,       // добыть руду (материал = idle)
    FIND,       // собрать N предметов (материал = idle), не изымаются
    BREAK,      // сломать блоки (материал = idle)
    LOOT,       // облутать (впервые открыть) N сундуков
    DELIVERY,   // принести смотрящему N предметов (материал = idle), изымаются при сдаче
    COURIER;    // «Передачка»: отнести пакет конкретному смотрящему

    public static ThemeType parse(String s)
    {
        try {return valueOf(s.toUpperCase());}
        catch (IllegalArgumentException e) {return null;}
    }
}
