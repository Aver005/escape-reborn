# MAP — карта репозитория

Last updated: 2026-07-20 — добавлены касты (kit/Kit, kit/KitRegistry,
menu/KitSelectMenu, menu/KitEditorMenu, resources/kits.yml) и Мусорщик
(menu/ScavengerMenu, роль scrap в TraderType).

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
        │   ├── SetupMarkers.java      # блоки-подсказки точек: place/clearForMatch/removeAt
        │   └── WeightedItem.java      # record: предмет + вес лута
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
        │   ├── LootEditorMenu.java    # админ: пул лута (пересборка на onClose)
        │   ├── KitSelectMenu.java     # выбор каста в лобби (касты + «Без касты»/«Случайная»)
        │   ├── KitEditorMenu.java     # админ: предметы каста (пересборка на onClose)
        │   ├── ScavengerMenu.java     # Мусорщик: click-to-sell сломанного (цена x износ)
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
