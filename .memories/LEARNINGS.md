# LEARNINGS — грабли и неочевидное

Last updated: 2026-07-18

## Экосистема Minecraft/Paper (июль 2026)

- **Minecraft перешёл на календарные версии**: после 1.21.11 идут 26.1, 26.2
  (YY.minor). Последний стабильный Paper — **26.1.2** (26.2 — beta).
- **Paper 26.x требует Java 25** (docs.papermc.io; 1.20.5–1.21.11 было Java 21).
  Мы компилируем toolchain'ом 25, Gradle качает Temurin сам (foojay-resolver).
- **API PaperMC v2 закрыт** (`api.papermc.io/v2` → sunset). Рабочий API:
  `fill.papermc.io/v3/projects/paper` (версии/сборки/ссылки на jar).
- **Артефакты paper-api переименованы**: теперь `26.1.2.build.74-stable`
  (не `-R0.1-SNAPSHOT`). Репозиторий тот же: repo.papermc.io.
- `api-version: '1.21'` в plugin.yml валиден на 26.x (это минимум, не таргет).
- SQLite-драйвер (`org.sqlite`) по-прежнему встроен в Paper — shade не нужен.

## Кодовая база

- **Windows-консоль коверкает кириллицу в логах** (mojibake в smoke-тесте) →
  инвариант: `getLogger()` — только ASCII; игрокам — MiniMessage из messages.yml.
- **Кастомные имена предметов курсивят автоматически** → всё через
  `Items.flat()` (decoration ITALIC=false), иначе весь UI будет курсивом.
- **Бумаги-контракты стакаются**, если PDC одинаковый (прогресс 0) → в
  `ContractPapers.create` пишется случайный `PAPER_NONCE`.
- **Хитбокс ArmorStand ~2 блока**: стойка на y-1 под сундуком перекрывает блок
  сундука — клики по сундуку часто попадают в стойку. Это НЕ баг, это механика
  (см. ARCHITECTURE), не «чинить».
- **`InventoryOpenEvent`/`InventoryCloseEvent` с holder instanceof Chest не
  ловят двойные сундуки** (holder = DoubleChest) → точки сундуков на карте
  ставить НЕ вплотную (см. BUGS.md).
- **`Location` в YML сериализуется нативно** (`cfg.set(path, location)` /
  `getLocation`), но требует загруженного мира при чтении — арены грузятся в
  onEnable, когда миры уже есть; при добавлении отложенной загрузки это сломается.
- Player.getInventory().getContents() = 41 слот (storage+armor+offhand) —
  снапшот сохраняет/восстанавливает одним массивом.

## Оригинал (.old/) — если полезешь сверять

- Исходники в **смешанной кодировке**: CP1251 + отдельные строки UTF-8.
  Читать построчным декодером (try UTF-8 → fallback CP1251), iconv целиком падает.
- `escape-2025-1.21.5` — байт-в-байт копия `escape-2021-1.16.5`, порта на 1.21
  не существовало.
- Полный разбор багов оригинала (16 шт.) — `docs/05-quirks-bugs-unfinished.md`;
  все они в reborn исправлены, НЕ переноси их обратно при сверке поведения.

## Инфраструктура сессии разработки

- Тестовый сервер живёт в `E:\Servers\escape` (перенесён из scratchpad по
  просьбе владельца), запуск `start.bat`, порт 25599, offline-mode.
- JDK 25 лежит в `C:\Users\Aver\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2\`
  (авто-скачан Gradle) — start.bat ссылается на него напрямую.
- Системный Gradle не установлен; использовать `./gradlew`.
