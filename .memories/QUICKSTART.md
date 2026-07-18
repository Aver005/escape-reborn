# QUICKSTART

Last updated: 2026-07-18

## Что это

**Escape** — Paper-плагин мини-игры «побег из тюрьмы» (last man standing):
лобби с разминкой → 20-минутный матч (лут, контракты, торговцы, случайные
события) → финальная битва → последний выживший побеждает. Возрождение
авторской мини-игры 2020/2021 (`.old/`) на современном стеке. Полное описание
геймплея: `docs/01-game-design.md`, `docs/02-mechanics.md`.

## Как собрать / запустить

```bash
./gradlew build     # jar → build/libs/EscapeReborn-1.0.0.jar (JDK 25 скачается сам)
./gradlew deploy    # build + копия jar в E:/Servers/escape/plugins
```

Тестовый сервер: `E:\Servers\escape` (Paper 26.1.2, порт 25599, offline-mode) —
запуск `start.bat` (внутри прописан путь к Temurin JDK 25 из `~/.gradle/jdks/`).
Проверка «плагин жив»: в логе `[Escape] Escape enabled, arenas loaded: N` и
отсутствие стектрейсов.

## Горячие файлы

| Файл | Роль |
|---|---|
| `src/main/java/me/aver005/escape/game/GameSession.java` | ЯДРО: весь жизненный цикл матча |
| `src/main/java/me/aver005/escape/listener/GameListener.java` | Вся игровая событийная логика |
| `src/main/java/me/aver005/escape/command/EscapeCommand.java` | Все команды + tab |
| `src/main/resources/messages.yml` | ВСЕ тексты (MiniMessage, русский) |
| `src/main/java/me/aver005/escape/arena/Arena.java` | Модель арены + YML load/save |
| `docs/07-decisions.md` | Что утверждено владельцем (источник требований) |

## Быстрая ориентация

- Игровые данные в рантайме: `plugins/Escape/` (arenas/<ID>/, traders.yml,
  contracts.yml, snapshots/, stats.db) — схема в `docs/08-reborn-guide.md`.
- Настройка арены админом — маркеры-предметы, цепочка команд:
  админ-квикстарт в `docs/08-reborn-guide.md`.
