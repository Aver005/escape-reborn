# MAP — карта репозитория

Last updated: 2026-07-22 — «Правка 5»: ориентация сундуков. Новое поле
`Arena.chestFacings` (Map<Location, BlockFace>) → опциональный `facing:` в записи
точки `locations.yml`. Команда `/escape chestface <ID>` (EscapeCommand) —
разворот сундука-точки лицом к смотрящему. Захват стороны — `SetupListener`
(addchest/chesttag), применение — `SetupMarkers.placeChest` +
`GameSession.placeChests`. Починка стыков решёток — `GameSession.fixConnectingNeighbors`
(третий проход cleanup). Без новых файлов/PDC-ключей. Ранее «Правка 4»:

Last updated: 2026-07-22 — «Правка 4»: наполнение/создание содержимого из 3
источников (лут + жители). Новое: `menu/LootFillMenu`, `menu/LootFillSourceMenu`,
`menu/TradeCreate` (helper), `menu/TraderListMenu`, `menu/TraderCreateMenu`,
`menu/TraderCopySourceMenu`, `menu/TradeFillMenu`, `menu/TradeFillSourceMenu`.
`TraderType.copyAs`, `TraderRegistry.all()/isEmpty()`. `/escape trades` без VID →
список жителей. Без новых PDC-ключей. Ранее (2026-07-21) — пакет админ-GUI и
геймплей-правок (см. JOURNAL/2026-07-21 разделы 3-4): `menu/ScavengerEditorMenu`,
`menu/TradeListEditorMenu`, `menu/VillagerPointsMenu`; команды `scrapedit/trades/
villagers/chesttag/traderquota/breakable`. Новые данные арены: `arena.yml` →
`trader-quotas` (лимит жителей по типу), `locations.yml` → `breakables`
(отмеченные ломаемые блоки). `config.yml` → `fire:` (управляемый огонь).
`SetupMarkers.structurePartner` (вторая половина дверей/кроватей).
Отметчик категории и метчик ломаемых блоков — на PDC `SPECIAL_ITEM` (chesttag/
breakwand) + `MARKER_ARENA` + `CATEGORY_ID`, без новых ключей.

Ранее (2026-07-20): касты, Мусорщик, модификаторы сессии и
**категории сундуков + мастер** (category/ChestCategory, category/CategoryRegistry,
arena/ChestSetupManager, arena/WizardState, menu/ChestSetupMenu,
resources/chest-categories.yml). Точки сундуков в locations.yml теперь
`{location, category}` (обратно-совместимо со старым плоским списком); у арены —
`chest-categories.yml` (копии глоб. шаблонов, как kits.yml).

```
escape-reborn/
├── README.md                  # витрина проекта: что за игра, фичи, история, быстрый старт
├── CLAUDE.md                  # ГЛАВНЫЙ бридж: порядок работы, команды, правила, definition of done
├── AGENTS.md                  # тонкий указатель на CLAUDE.md (для не-Claude агентов)
├── .memories/                 # эта база знаний
├── docs/                      # проектная документация (русский)
│   ├── README.md              #   оглавление + история версий оригинала
│   ├── 01-game-design.md      #   концепция и игровой цикл (по оригиналу 2021)
│   ├── 02-mechanics.md        #   все механики детально
│   ├── 03-admin-and-arena-setup.md  # команды/настройка оригинала
│   ├── 04-version-diff.md     #   2020 vs 2021 vs 2025
│   ├── 05-quirks-bugs-unfinished.md # фишки/16 багов/недоделки оригинала
│   ├── 06-reborn-questions.md #   вопросы владельцу (закрыты ответами)
│   ├── 07-decisions.md        #   УТВЕРЖДЁННЫЕ решения = требования MVP
│   └── 08-reborn-guide.md     #   сборка/структура/админ-квикстарт новой версии
├── .old/                      # оригиналы (референс, НЕ трогаем)
│   ├── escape-2020/           #   Spigot 1.12.2
│   ├── escape-2021-1.16.5/    #   Spigot 1.16.4 — база для reborn
│   └── escape-2025-1.21.5/    #   байт-копия 2021 (порт не был начат)
├── build.gradle.kts           # Java 25 toolchain, paper-api, задача deploy
├── settings.gradle.kts        # foojay-resolver (автоскачивание JDK)
├── gradlew(.bat), gradle/     # wrapper 9.6.1
└── src/main/
    ├── resources/
    │   ├── plugin.yml         # main, команда escape [es, esc], права
    │   ├── config.yml         # дефолты арен, кулдауны помощника, рефилл
    │   └── messages.yml       # ВСЕ тексты (MiniMessage)
    └── java/me/aver005/escape/
        ├── EscapePlugin.java          # onEnable/onDisable, wiring, joinArena
        ├── arena/
        │   ├── Arena.java             # модель + load/save arenas/<ID>/{arena,locations,loot}.yml
        │   ├── ArenaCheck.java        # валидатор арены: CRITICAL/WARNING/GOOD + подсказки
        │   ├── ArenaManager.java      # реестр арен + Map игрок→сессия + delete/stopAll
        │   ├── ChestSetupManager.java # мастер категорий сундуков: телепорт по точкам + жезлы
        │   ├── SetupMarkers.java      # блоки-подсказки точек: place/clearForMatch/removeAt
        │   ├── WeightedItem.java      # record: предмет + вес лута
        │   └── WizardState.java       # состояние админа в мастере (арена+снимок точек+индекс)
        ├── game/
        │   ├── GameSession.java       # ЯДРО (см. ARCHITECTURE)
        │   ├── GameEvent.java         # enum 12 случайных событий (окно/canStart/onTick/resolve)
        │   ├── MatchPlayer.java       # per-match: kills/quests/trades/ores, lastDamager, lootedChests
        │   ├── ChatChannel.java       # канал чата (формат из messages.yml, system/chat)
        │   ├── RespawnTier.java       # 5 уровней блока возрождения (цены/награды/анимации)
        │   ├── RespawnBlock.java      # данные блока одного игрока (уровень/заряды/позиция)
        │   ├── RespawnBlocks.java     # менеджер: установка/перенос/прокачка/респаун/молнии/прозрение
        │   ├── OfflineGuards.java     # AFK-страж при выходе живого игрока + окна возврата
        │   └── Themes.java            # менеджер темок: взять/бросить/прогресс/сдача/ключик
        ├── contract/
        │   ├── Contract.java          # модель; isComplete() = готов к выдаче
        │   ├── ContractType.java      # KILLS/ACTIVATE/MINE/FIND/BREAK/LOOT
        │   ├── ContractRegistry.java  # contracts.yml
        │   └── ContractPapers.java    # бумага «Задание»: PDC id/progress/nonce + лор
        ├── kit/
        │   ├── Kit.java               # каст: name/icon/gold/items; load(spec|serialized)/copy/apply
        │   └── KitRegistry.java       # глобальная библиотека kits.yml (read-only, копируется на арену)
        ├── modifier/
        │   ├── Modifier.java          # модификатор сессии: name/icon/desc + Map эффектов (mult/add/flag)
        │   └── ModifierRegistry.java  # глобальный пул modifiers.yml (read-only, random-кандидат в лобби)
        ├── category/
        │   ├── ChestCategory.java     # категория сундука: loot-min/max, quota, refill-seconds (0=без рефилла)
        │   └── CategoryRegistry.java  # глобальный chest-categories.yml (read-only, копии на арену)
        ├── theme/
        │   ├── Theme.java             # темка смотрящего: тип/цель/золото/turn-in (SELF|ANY|NPC)
        │   ├── ThemeType.java         # 6 контрактных + DELIVERY + COURIER
        │   └── ThemeRegistry.java     # themes.yml
        ├── trader/
        │   ├── TraderType.java        # тип торговца: имя (MiniMessage) + трейды
        │   ├── Trade.java             # record: предмет + цена
        │   └── TraderRegistry.java    # traders.yml + byDisplayName fallback
        ├── player/
        │   └── PlayerSnapshot.java    # save/clear/restore/exists (snapshots/<uuid>.yml)
        ├── stats/
        │   └── StatsRepository.java   # SQLite: колонки-счётчики, recordGameKills, findByName
        ├── menu/
        │   ├── Menu.java              # база: InventoryHolder + allowsInteraction + onClose
        │   ├── ArenaSelectMenu.java   # главное меню (лаймовая шерсть)
        │   ├── AssistantMenu.java     # «Личный помощник»: 4 способности + кулдауны
        │   ├── PlacesMenu.java        # «Отмеченные локации» (рычаги)
        │   ├── ShopMenu.java          # магазин торговца (покупка за золото)
        │   ├── TradeEditorMenu.java   # админ: добавить трейд (цена кнопками)
        │   ├── LootEditorMenu.java    # админ: список глоб. категорий лута (+«Создать»)
        │   ├── LootFillMenu.java      # админ: дополнить/заменить лут категории из 3 источников
        │   ├── LootFillSourceMenu.java# админ: выбор категории-источника для наполнения
        │   ├── KitSelectMenu.java     # выбор каста в лобби (касты + «Без касты»/«Случайная»)
        │   ├── KitEditorMenu.java     # админ: предметы каста (пересборка на onClose)
        │   ├── ChestSetupMenu.java    # мастер сундуков: пагинированный выбор точки (клик = прыжок)
        │   ├── ScavengerMenu.java     # Мусорщик: click-to-sell сломанного (цена x износ)
        │   ├── ScavengerEditorMenu.java # админ: GUI прайса Мусорщика (клик=цена, порог, «из руки»)
        │   ├── TradeListEditorMenu.java # админ: GUI торгов жителя (клик=цена, Q=убрать, «добавить», «Наполнение»)
        │   ├── TradeFillMenu.java     # админ: дополнить/заменить товары жителя из 3 источников
        │   ├── TradeFillSourceMenu.java # админ: выбор жителя-источника для наполнения
        │   ├── TraderListMenu.java    # админ: глоб. список жителей + «Создать» (/escape trades без VID)
        │   ├── TraderCreateMenu.java  # админ: создать жителя из инвентаря/сундука/копии
        │   ├── TraderCopySourceMenu.java # админ: выбор жителя-источника для копии
        │   ├── TradeCreate.java       # helper: сборка товаров жителя из инв/сундука (цена 1) + freshId
        │   ├── VillagerPointsMenu.java# админ: точки жителей — ЛКМ телепорт, ПКМ редактор торгов
        │   ├── RespawnUpgradeMenu.java# прокачка блока возрождения (ПКМ по блоку)
        │   ├── NpcMenu.java           # совмещённый NPC: выбор роли «Магазин / Темки / Мусорщик» (до 3)
        │   └── ThemesMenu.java        # темки смотрящего: взять/бросить
        ├── listener/
        │   ├── GameListener.java      # блоки/бой/смерть/предметы/сундуки/join-quit
        │   ├── MechanicsListener.java # снежки/яйца бьют, удочка-крюк тянет игрока
        │   ├── SetupListener.java     # маркеры → точки арены; слом подсказки → удаление точки
        │   ├── ChatListener.java      # каналы чата + блокировка команд в матче
        │   ├── ProtectionListener.java# огонь/листва/висячие/куры-из-яиц в мирах арен
        │   └── MenuListener.java      # роутинг кликов в Menu
        ├── command/
        │   └── EscapeCommand.java     # все подкоманды + tab-completion
        └── util/
            ├── Msg.java               # messages.yml: get/send/list, ph/phMm/phC
            ├── Items.java             # named/special/filler, countMaterial/takeMaterial
            ├── DebugLog.java          # /escape debuglog: консоль + кольцевой буфер + save в файл
            └── Keys.java              # все NamespacedKey (PDC)
```

Вне репозитория: тестовый сервер `E:\Servers\escape` (paper.jar 26.1.2,
start.bat, plugins/EscapeReborn-1.0.0.jar; деплой — `./gradlew deploy`).
