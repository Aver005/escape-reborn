# Escape Reborn — сборка, структура, быстрый старт

Новая реализация лежит в корне репозитория (`src/`, `build.gradle.kts`). Оригиналы — в `.old/`, они больше не нужны для работы, только как референс.

## Стек

- **Paper 26.1.2** (`io.papermc.paper:paper-api:26.1.2.build.74-stable`), `api-version: 1.21`
- **Java 25** (toolchain, Gradle сам скачивает JDK через foojay-resolver)
- **Gradle 9.6.1** (wrapper в репозитории), Kotlin DSL
- Adventure + MiniMessage, все тексты в `messages.yml`
- SQLite (драйвер встроен в Paper) для статистики

## Сборка

```bash
./gradlew build        # jar появится в build/libs/EscapeReborn-1.0.0.jar
```

Ничего ставить не нужно: wrapper скачает Gradle, toolchain скачает JDK 25.

## Структура кода (`src/main/java/me/aver005/escape/`)

| Пакет | Что там |
|---|---|
| `EscapePlugin` | Главный класс: wiring, reload, join-логика |
| `arena/` | `Arena` (модель + YML в `arenas/<id>/`), `ArenaManager` (реестр + сессии по игрокам), `WeightedItem` |
| `game/` | `GameSession` — весь матч (лобби, отсчёт, генерация, тик, события, финал, очистка); `GameEvent` — 5 случайных событий; `MatchPlayer`, `ChatChannel` |
| `contract/` | `Contract`, `ContractType`, `ContractRegistry` (contracts.yml), `ContractPapers` (бумага с PDC) |
| `trader/` | `TraderType`, `Trade`, `TraderRegistry` (traders.yml) |
| `player/` | `PlayerSnapshot` — сохранение/восстановление игрока (snapshots/<uuid>.yml) |
| `stats/` | `StatsRepository` — SQLite, асинхронная запись |
| `menu/` | GUI на InventoryHolder: выбор арены, помощник, локации, магазин, редактор трейдов, редактор лута |
| `listener/` | `GameListener` (геймплей), `SetupListener` (маркеры), `ChatListener` (каналы+команды), `ProtectionListener` (огонь/листва), `MenuListener` |
| `command/` | `EscapeCommand` — все подкоманды + tab-completion |
| `util/` | `Msg` (MiniMessage-каталог), `Items`, `Keys` (все PDC-ключи) |

## Данные в `plugins/Escape/`

```
config.yml          — дефолты новых арен, кулдауны помощника, время рефилла
messages.yml        — ВСЕ тексты (MiniMessage)
arenas/<ID>/
  arena.yml         — настройки арены
  locations.yml     — точки (спавны, финальные спавны, сундуки, столы, руды, рычаги, торговцы)
  loot.yml          — пул лута (предмет + вес)
traders.yml         — типы торговцев (глобально)
contracts.yml       — контракты (глобально)
snapshots/<uuid>.yml — снапшоты игроков на время матча (crash-safe)
stats.db            — статистика (SQLite)
```

## Быстрый старт админа

```
/escape create ТЮРЬМА 12          — создать арену (мир и лобби = где стоишь)
/escape setname ТЮРЬМА <green>Тюрьма Аркхем
/escape setdesc ТЮРЬМА Классическая карта
/escape addspawn ТЮРЬМА           — получить маяк-маркер, ставить в точках спавна
/escape addchest ТЮРЬМА           — сундук-маркер (точек ставь больше, чем chest-count!)
/escape addore ТЮРЬМА             — камень-маркер (ставится реально)
/escape addlever ТЮРЬМА Столовая  — рычаг-маркер (ставится реально; имя = локация для проводника и цель ACTIVATE)
/escape addfinalspawn ТЮРЬМА      — лодстоун-маркер финальной битвы (опционально)
/escape addtable ТЮРЬМА           — стол зачарований

/escape createvillager БАРЫГА
/escape villagername БАРЫГА <gold>Барыга
/escape addtrade БАРЫГА           — GUI: предмет в правый слот, цену кнопками, СОХРАНИТЬ
/escape addvillager ТЮРЬМА БАРЫГА — верстак-маркер точки торговца

/escape createcontract ШАХТЁР
/escape contracttype ШАХТЁР MINE
/escape contractidle ШАХТЁР DIAMOND_ORE
/escape contractdesc ШАХТЁР Добудьте 3 алмазные руды
/escape contractamount ШАХТЁР 3
/escape contractprice ШАХТЁР 20
/escape addcontract ТЮРЬМА ШАХТЁР

/escape additem ТЮРЬМА 50         — предмет из руки в лут с весом 50 (1–250)
/escape edititems ТЮРЬМА          — GUI-редактор пула

/escape set ТЮРЬМА duration 1200  — числовые параметры (duration/eventinterval/salaryinterval/
                                    salarygold/glowtime/glowgold/chests/traders/tables/
                                    forkuses/startgold/startdelay/startdelayfull)
/escape enable ТЮРЬМА             — открыть арену игрокам
```

Игроки: `/escape` (меню), `/escape join [ID]`, `/escape leave`, `/escape stats [ник]`, `/escape info`.

## Отличия поведения от оригинала (сознательные)

Все баги из `05-quirks-bugs-unfinished.md` исправлены, решения из `07-decisions.md` применены. Ключевое:

- Награда KILLS-контракта идёт **убийце** (учитываются и снаряды, окно кредита 10 сек).
- Событие «крыша» — честная проверка колонны до неба; молния теперь наносит 2 сердца урона (визуальная молния без урона в оригинале ничего не делала).
- Событие «трава» — `GRASS_BLOCK`; слепота 10 сек; свечение 18 сек.
- FIND — «собери N одновременно», предметы не изымаются; прогресс живёт в бумаге (PDC), бумаги не стакаются.
- LOOT реализован: +1 за каждый впервые открытый сундук.
- Вилка одноразовая (`fork-uses: 1`, настраивается).
- Столы зачарований спавнятся; зачарование разрешено (уровни всегда «полные» из-за XP-таймера — валюта зачарований лазурит, продавайте его у торговцев).
- Автостарт: минимум набран → 60 сек, арена полная → 10 сек.
- Разминка в лобби — топ-3 вместо одного победителя.
- Прогресс контракта и все служебные данные — в PDC, не в лоре (лор только отображение).
- Данные хранятся в YML/SQLite; сундуки/руды/столы/решётки восстанавливаются после матча по сохранённому BlockData.

## Случайные события (12 штук)

5 классических (крыша, трава, присядь, 10 золота, снять броню — все исправлены) +
7 новых, согласованных владельцем: **Отбой** (замри на 8 сек), **Обыск** (выбрось
предмет или потеряешь случайный), **Взятка** (5 золота → случайный предмет из лута),
**Подкоп** (сломай руду за 20 сек → 6 золота), **Кровавая луна** (только ночью:
120 сек урон ×1.5 + всем ночное зрение), **Туман** (50 сек слепота, на корточках —
видно), **Голодный паёк** (еда всех → 3 полоски). Реализация: `game/GameEvent.java`;
события с окнами разной длины, условиями старта и непрерывными эффектами.

## Ограничения текущей версии

- Сундуки на точках должны быть **одиночными** (двойной сундук LOOT/рефилл не отследит).
- Арене нужен свой мир (защита от огня/листвы действует на весь мир арены).
- Точек спавна должно быть ≥ максимума игроков (иначе спавны будут переиспользоваться).
