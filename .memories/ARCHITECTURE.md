# ARCHITECTURE

Last updated: 2026-07-18

Геймдизайн (правила игры) — в `docs/01-game-design.md` и `docs/02-mechanics.md`.
Здесь — как это устроено в коде и почему.

## Ядро модели

```
EscapePlugin ── wiring всего, joinArena()
   │
   ├─ ArenaManager ── реестр Arena (arenas/<ID>/) + Map<UUID, GameSession> (кто где играет)
   │     └─ Arena ── конфиг-модель (настройки, точки, лут) + ссылка на живую GameSession
   │
   ├─ ContractRegistry / TraderRegistry ── глобальные contracts.yml / traders.yml
   ├─ StatsRepository ── SQLite stats.db (запись async, чтение с callback в main thread)
   └─ Msg / Keys / Items ── статические утилиты (MiniMessage-каталог, PDC-ключи, предметы)
```

**GameSession** — один матч, создаётся при первом входе в лобби, умирает в
`cleanup()` (`arena.setSession(null)`). Фазы: `WAITING → COUNTDOWN → RUNNING →
ENDING`. Владеет: составами (lobby/playing/spectators), тремя ChatChannel,
таймерами (countdown / main tick 1 Гц / stop), заспавненным миром (сундуки,
стойки, торговцы, руды, столы), `editedBlocks` для отката, кулдаунами
помощника, per-match статистикой (`MatchPlayer`).

## Ключевые механизмы

- **Откат мира**: перед ЛЮБЫМ изменением блока его прежний `BlockData` кладётся
  в `editedBlocks` (LinkedHashMap, putIfAbsent) → `cleanup()` восстанавливает
  всё: решётки, сундуки, руды, столы. Сущности (стойки/торговцы) и дропы
  трекаются по UUID и удаляются.
- **Состояние игрока**: `PlayerSnapshot.save()` пишет ПОЛНЫЙ снапшот в
  `snapshots/<uuid>.yml` перед входом в лобби; `restore()` применяет и удаляет
  файл. Файл на диске = страховка от краша: `GameListener.onJoin` восстанавливает
  игрока, если файл существует, а сессии нет.
- **Данные на предметах — только PDC** (`Keys`): контракты (id+progress+nonce
  против стакания), спец-предметы (fork/assistant/leave), маркеры настройки
  (type/arena/extra), вес лута в редакторе. Лор — только отображение,
  парсинга лора нет нигде.
- **Смерть фейковая**: `GameListener.onDamage` ловит летальный `getFinalDamage()`,
  отменяет, дропает инвентарь, Spectator. Кредит убийства — `lastDamager` в
  `MatchPlayer` с окном 10 сек (пишется в onDamageByEntity, включая снаряды).
- **GUI — кастомные InventoryHolder** (`menu/Menu` + `MenuListener`): никакого
  сравнения заголовков; `allowsInteraction()` разделяет меню-кнопки и
  меню-редакторы (редакторы сохраняются в `onClose`).
- **Клик по сундуку через стойку**: невидимая ArmorStand спавнится на y-1 под
  сундуком, её хитбокс перекрывает блок сундука — `PlayerInteractAtEntityEvent`
  редиректит на открытие сундука. Она же — табличка «Пустой/Заполнен» рефилла.
- **Торговцы**: Villager без AI; тип хранится в PDC сущности (`TRADER_TYPE`),
  fallback — поиск по customName.
- **Тик матча**: один runTaskTimer 1 Гц в GameSession.tick(): XP-уровень =
  оставшиеся секунды, расписание событий (announce → через 8 сек check),
  зарплата, свечение, объявления, финальная битва на нуле (таймер гасится,
  матч продолжается до последнего живого).

## Решения и их причины

| Решение | Почему |
|---|---|
| Paper 26.1.2 + Java 25 | Последняя стабильная; Paper 26.x требует Java 25 (см. LEARNINGS) |
| YML per-arena папка + SQLite для статистики | Требование владельца (07-decisions §2); человекочитаемо + готово к топам |
| Прогресс контракта живёт В БУМАГЕ | Фишка оригинала: контракт можно отобрать при убийстве (07-decisions §3) |
| XP-уровень как таймер | Фишка оригинала; побочно делает уровни «бесплатными» для столов зачарований — валюта зачарования = лазурит у торговцев |
| Смерть без ванильной смерти | Как в оригинале: нет экрана смерти, нет respawn-логики |
| Один GameSession-класс, а не менеджеры-на-всё | Матч — единица владения состоянием; всё, что живёт и умирает с матчем, лежит в нём |
| Логи сервера — ASCII | Windows-консоль коверкает кириллицу (mojibake в первом смоук-тесте); игрокам — MiniMessage из messages.yml |

## Потоки и многопоточность

Всё игровое — main thread. Исключения: чат (`AsyncChatEvent` — Adventure
sendMessage потокобезопасен), SQLite (запись в async-задачах c synchronized,
чтение async → callback в main). Никакого Folia-шедулинга нет и не планируется.
