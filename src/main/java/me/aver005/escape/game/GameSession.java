package me.aver005.escape.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import me.aver005.escape.EscapePlugin;
import me.aver005.escape.arena.Arena;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.contract.ContractPapers;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.kit.Kit;
import me.aver005.escape.modifier.Modifier;
import me.aver005.escape.player.PlayerSnapshot;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Keys;
import me.aver005.escape.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Один матч на арене: лобби -> отсчёт -> игра -> финальная битва -> очистка. */
public class GameSession
{
    public enum Phase {WAITING, COUNTDOWN, RUNNING, ENDING}

    private static final Random RANDOM = new Random();
    private static final long KILL_CREDIT_MILLIS = 10_000L;

    private final EscapePlugin plugin;
    private final Arena arena;

    private Phase phase = Phase.WAITING;

    private final Set<UUID> lobby = new LinkedHashSet<>();
    private final Set<UUID> playing = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, MatchPlayer> matchData = new HashMap<>();

    private final Map<UUID, Double> warmupDamage = new HashMap<>();

    // выбор каста в лобби: id каста | "none" | "random"; нет записи = default арены
    private final Map<UUID, String> chosenKit = new HashMap<>();

    private final ChatChannel lobbyChat = new ChatChannel("chat.lobby-format");
    private final ChatChannel gameChat = new ChatChannel("chat.game-format");
    private final ChatChannel spectatorChat = new ChatChannel("chat.spectator-format");

    // runtime мира
    private final LinkedHashMap<Location, BlockData> editedBlocks = new LinkedHashMap<>();
    private final Set<Location> activeChests = new HashSet<>();
    private final Map<Location, UUID> chestStands = new HashMap<>();
    /** Исходное содержимое динамических сундуков (вернуть после матча). */
    private final Map<Location, ItemStack[]> dynamicChestOriginals = new HashMap<>();
    private final List<Location> traderLocations = new ArrayList<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> droppedItems = new HashSet<>();
    private final Map<Location, BukkitTask> refillTasks = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask mainTask;
    private BukkitTask stopTask;

    private int countdownRemaining = 0;
    private boolean forcedStart = false; // старт админом: min-players не проверяется
    private int remaining = 0;
    private BossBar timerBar; // таймер матча (XP — валюта зачарования)
    private GameEvent currentEvent = null;
    private int eventTicksLeft = 0;
    private final Map<UUID, Location> eventPositions = new HashMap<>();
    private final Set<UUID> eventFlagged = new HashSet<>();
    private boolean bloodMoon = false;
    private boolean glowActive = false;
    private boolean finalBattleStarted = false;
    private final RespawnBlocks respawnBlocks;
    private final OfflineGuards offlineGuards;
    private final Themes themes;

    // модификатор сессии (§15): кандидат на голосование в лобби и принятый на матч
    private final Modifier candidateModifier;
    private final Set<UUID> modifierVotes = new HashSet<>();
    private Modifier activeModifier = null;

    public GameSession(EscapePlugin plugin, Arena arena)
    {
        this.plugin = plugin;
        this.arena = arena;
        this.respawnBlocks = new RespawnBlocks(plugin, this);
        this.offlineGuards = new OfflineGuards(plugin, this);
        this.themes = new Themes(plugin, this);
        this.candidateModifier = plugin.modifiers().random(RANDOM);
    }

    // ===== доступ =====

    public Arena getArena() {return arena;}
    public Phase getPhase() {return phase;}
    public boolean isLobbyMember(UUID id) {return lobby.contains(id);}
    public boolean isPlaying(UUID id) {return playing.contains(id);}
    public boolean isSpectating(UUID id) {return spectators.contains(id);}
    public int lobbySize() {return lobby.size();}
    public int aliveCount() {return playing.size();}
    public Set<UUID> playingSet() {return playing;}
    public ChatChannel lobbyChat() {return lobbyChat;}
    public ChatChannel gameChat() {return gameChat;}
    public ChatChannel spectatorChat() {return spectatorChat;}
    public MatchPlayer matchData(UUID id) {return matchData.get(id);}
    public Set<Location> getActiveChests() {return activeChests;}
    public List<Location> getTraderLocations() {return traderLocations;}
    public Map<Location, UUID> getChestStands() {return chestStands;}
    public GameEvent getCurrentEvent() {return currentEvent;}
    public RespawnBlocks respawnBlocks() {return respawnBlocks;}
    public OfflineGuards offlineGuards() {return offlineGuards;}
    public Themes themes() {return themes;}
    public boolean isFinalBattle() {return finalBattleStarted;}
    public boolean isGlowActive() {return glowActive;}

    /** Текущий выбор каста игрока: id | "none" | "random"; null — ещё не выбирал. */
    public String getChosenKit(UUID id) {return chosenKit.get(id);}
    public void setChosenKit(UUID id, String choice) {chosenKit.put(id, choice);}

    // ===== модификатор сессии (§15) =====

    /** Множитель принятого модификатора (1.0, если модификатора нет). */
    public double modMult(String key) {return activeModifier == null ? 1.0 : activeModifier.mult(key);}
    /** Аддитивная добавка принятого модификатора (0.0, если модификатора нет). */
    public double modAdd(String key) {return activeModifier == null ? 0.0 : activeModifier.add(key);}
    /** Флаг принятого модификатора. */
    public boolean modFlag(String key) {return activeModifier != null && activeModifier.flag(key);}

    private long countModifierVotes() {return lobby.stream().filter(modifierVotes::contains).count();}

    /** Бюллетень в лобби: описание кандидата, текущий счётчик и состояние голоса. */
    private void giveModifierBallot(Player p)
    {
        if (candidateModifier == null) {return;}
        boolean voted = modifierVotes.contains(p.getUniqueId());
        long forVotes = countModifierVotes();
        int need = lobby.size() / 3 + 1;
        List<Component> lore = new ArrayList<>();
        for (String line : candidateModifier.getDescRaw()) {lore.add(Msg.mm(line));}
        lore.add(Component.empty());
        lore.add(Msg.get("modifier.ballot-tally", Msg.ph("for", forVotes), Msg.ph("need", need)));
        lore.add(Msg.get(voted ? "modifier.ballot-voted" : "modifier.ballot-not"));
        lore.add(Msg.get("modifier.ballot-hint"));
        p.getInventory().setItem(4, Items.special(candidateModifier.getIcon(),
            Msg.get("modifier.ballot-name", Msg.phMm("name", candidateModifier.getNameRaw())), lore, "modifier-vote"));
    }

    private void refreshBallots()
    {
        forEachOnline(lobby, this::giveModifierBallot);
    }

    /** Переключить голос игрока «за» кандидата и обновить бюллетени лобби. */
    public void toggleModifierVote(Player p)
    {
        if (candidateModifier == null) {return;}
        UUID id = p.getUniqueId();
        boolean nowFor;
        if (modifierVotes.remove(id)) {nowFor = false;}
        else {modifierVotes.add(id); nowFor = true;}
        long forVotes = countModifierVotes();
        int need = lobby.size() / 3 + 1;
        Msg.send(p, nowFor ? "modifier.voted-for" : "modifier.voted-cancel",
            Msg.phMm("name", candidateModifier.getNameRaw()),
            Msg.ph("for", forVotes), Msg.ph("need", need));
        refreshBallots();
    }

    /** Решение по модификатору: применяем, если «за» больше 1/3 стартующих. */
    private void decideModifier(World world)
    {
        activeModifier = null;
        if (candidateModifier == null) {return;}
        int starting = lobby.size();
        long forVotes = countModifierVotes();
        boolean accepted = forVotes * 3 > starting;
        DebugLog.log(Cat.SESSION, "modifier arena=%s candidate=%s votes=%d/%d accepted=%b",
            arena.getId(), candidateModifier.getId(), forVotes, starting, accepted);
        if (accepted)
        {
            activeModifier = candidateModifier;
            lobbyChat.systemKey("modifier.accepted", Msg.phMm("name", candidateModifier.getNameRaw()));
            if (activeModifier.flag("night-start") && world != null) {world.setTime(18000L);}
        }
        else
        {
            lobbyChat.systemKey("modifier.rejected", Msg.phMm("name", candidateModifier.getNameRaw()));
        }
    }

    public long cooldownLeft(String key)
    {
        long until = cooldowns.getOrDefault(key, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void setCooldown(String key, long millis)
    {
        cooldowns.put(key, System.currentTimeMillis() + millis);
    }

    // ===== лобби =====

    public boolean join(Player p)
    {
        if (plugin.arenas().inSession(p)) {Msg.send(p, "errors.already-in-game"); return false;}
        if (!arena.isEnabled()) {Msg.send(p, "errors.arena-disabled"); return false;}
        if (phase == Phase.RUNNING || phase == Phase.ENDING) {Msg.send(p, "errors.arena-started"); return false;}
        if (lobby.size() >= arena.getMaxPlayers()) {Msg.send(p, "menu.arena-full"); return false;}
        if (arena.getLobby() == null || arena.getLobby().getWorld() == null) {Msg.send(p, "errors.world-not-found"); return false;}
        if (arena.getSpawns().isEmpty()) {Msg.send(p, "errors.no-spawns"); return false;}

        PlayerSnapshot.save(plugin, p);
        PlayerSnapshot.clear(p);

        lobby.add(p.getUniqueId());
        plugin.arenas().bind(p.getUniqueId(), this);
        lobbyChat.add(p.getUniqueId());
        lobbyChat.systemKey("lobby.join", Msg.ph("player", p.getName()), Msg.phMm("arena", arena.getDisplayNameRaw()));
        DebugLog.log(Cat.SESSION, "join arena=%s player=%s lobby=%d/%d phase=%s",
            arena.getId(), p.getName(), lobby.size(), arena.getMaxPlayers(), phase);

        p.teleport(arena.getLobby());
        p.getInventory().setItem(8, Items.special(Material.MAGMA_CREAM,
            Msg.get("lobby.leave-item-name"), List.of(Msg.get("lobby.leave-item-lore")), "leave"));
        if (!arena.getKits().isEmpty())
        {
            p.getInventory().setItem(0, Items.special(Material.CHEST,
                Msg.get("kit.select-item-name"), Msg.getList("kit.select-item-lore"), "kit-select"));
        }
        if (candidateModifier != null)
        {
            refreshBallots(); // счётчик «нужно N» зависит от размера лобби — обновляем всем
            Msg.send(p, "modifier.candidate", Msg.phMm("name", candidateModifier.getNameRaw()));
        }

        if (phase == Phase.WAITING && lobby.size() >= arena.getMinPlayers())
        {
            startCountdown(arena.getStartDelaySeconds());
        }
        else if (phase == Phase.COUNTDOWN && lobby.size() >= arena.getMaxPlayers()
            && countdownRemaining > arena.getStartDelayFullSeconds())
        {
            countdownRemaining = arena.getStartDelayFullSeconds();
        }
        return true;
    }

    /** Добровольный выход или кик. Возвращает false, если игрок не в сессии. */
    public boolean leave(Player p)
    {
        UUID id = p.getUniqueId();
        if (lobby.remove(id))
        {
            lobbyChat.remove(id);
            plugin.arenas().unbind(id);
            PlayerSnapshot.restore(plugin, p);
            lobbyChat.systemKey("lobby.leave", Msg.ph("player", p.getName()));
            DebugLog.log(Cat.SESSION, "leave-lobby arena=%s player=%s lobby=%d", arena.getId(), p.getName(), lobby.size());
            if (phase == Phase.WAITING && lobby.isEmpty()) {dispose();}
            return true;
        }
        if (playing.contains(id))
        {
            DebugLog.log(Cat.SESSION, "leave-match arena=%s player=%s alive=%d", arena.getId(), p.getName(), playing.size());
            dropInventory(p, p.getLocation());
            eliminate(p, false);
            hideTimerBar(p);
            plugin.arenas().unbind(id);
            PlayerSnapshot.restore(plugin, p);
            return true;
        }
        if (spectators.remove(id))
        {
            spectatorChat.remove(id);
            hideTimerBar(p);
            plugin.arenas().unbind(id);
            PlayerSnapshot.restore(plugin, p);
            DebugLog.log(Cat.SESSION, "leave-spectator arena=%s player=%s", arena.getId(), p.getName());
            return true;
        }
        return false;
    }

    /** Выход с сервера во время матча. */
    public void handleQuit(Player p)
    {
        UUID id = p.getUniqueId();
        DebugLog.log(Cat.SESSION, "quit arena=%s player=%s phase=%s role=%s", arena.getId(), p.getName(), phase,
            lobby.contains(id) ? "lobby" : playing.contains(id) ? "playing" : "spectator");
        if (lobby.contains(id)) {leave(p); return;}
        if (playing.contains(id))
        {
            // ожидание возрождения / финал / завершение — выбытие сразу, без стража
            if (respawnBlocks.isAwaitingRespawn(id) || phase != Phase.RUNNING || finalBattleStarted)
            {
                DebugLog.log(Cat.SESSION, "quit-no-guard player=%s awaiting-respawn=%b final=%b",
                    p.getName(), respawnBlocks.isAwaitingRespawn(id), finalBattleStarted);
                dropInventory(p, p.getLocation());
                eliminate(p, true);
                // снапшот не восстанавливаем сейчас — восстановится при заходе (файл остаётся)
                plugin.arenas().unbind(id);
                spectators.remove(id);
                spectatorChat.remove(id);
                return;
            }
            // живой игрок в идущем матче: оффлайн-страж, игрок остаётся участником
            offlineGuards.beginGuard(p);
            return;
        }
        if (spectators.remove(id))
        {
            spectatorChat.remove(id);
            plugin.arenas().unbind(id);
        }
    }

    /** Возврат игрока на сервер, пока сессия им владеет. true — обработано. */
    public boolean handleRejoin(Player p)
    {
        if (!playing.contains(p.getUniqueId())) {return false;}
        DebugLog.log(Cat.SESSION, "rejoin arena=%s player=%s guarded=%b",
            arena.getId(), p.getName(), offlineGuards.isGuarded(p.getUniqueId()));
        return offlineGuards.handleRejoin(p);
    }

    /** Заочное выбывание (оффлайн-страж не дождался владельца). */
    public void eliminateOffline(UUID id, String name, boolean announce)
    {
        if (!playing.remove(id)) {return;}
        DebugLog.log(Cat.PLAYER, "eliminate-offline arena=%s player=%s announce=%b alive=%d",
            arena.getId(), name, announce, playing.size());
        gameChat.remove(id);
        plugin.stats().add(id, name, "loses", 1);
        respawnBlocks.onOwnerEliminated(id);
        plugin.arenas().unbind(id);

        if (announce)
        {
            gameChat.systemKey("offline-guard.eliminated", Msg.ph("player", name));
            spectatorChat.systemKey("offline-guard.eliminated", Msg.ph("player", name));
        }

        if (playing.size() > 1)
        {
            gameChat.systemKey("game.players-left", Msg.ph("n", playing.size()));
            spectatorChat.systemKey("game.players-left", Msg.ph("n", playing.size()));
            return;
        }
        finish();
    }

    public void addWarmupDamage(Player damager, double dmg)
    {
        warmupDamage.merge(damager.getUniqueId(), dmg, Double::sum);
    }

    private void startCountdown(int seconds)
    {
        phase = Phase.COUNTDOWN;
        countdownRemaining = seconds;
        DebugLog.log(Cat.SESSION, "countdown-start arena=%s seconds=%d lobby=%d forced=%b",
            arena.getId(), seconds, lobby.size(), forcedStart);
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
        {
            if (lobby.size() < (forcedStart ? 1 : arena.getMinPlayers()))
            {
                DebugLog.log(Cat.SESSION, "countdown-cancel arena=%s lobby=%d min=%d",
                    arena.getId(), lobby.size(), arena.getMinPlayers());
                forcedStart = false;
                lobbyChat.systemKey("lobby.countdown-cancelled");
                phase = Phase.WAITING;
                forEachOnline(lobby, pl -> pl.setLevel(60));
                cancelTask(countdownTask);
                countdownTask = null;
                return;
            }

            int sec = countdownRemaining;
            if (sec % 10 == 0 || sec < 10)
            {
                lobbyChat.systemKey("lobby.countdown", Msg.ph("seconds", sec));
            }
            forEachOnline(lobby, pl ->
            {
                pl.setLevel(sec);
                if (sec % 10 == 0 || sec <= 5)
                {
                    pl.showTitle(Title.title(
                        Msg.get("lobby.countdown-title"),
                        Msg.get("lobby.countdown-subtitle", Msg.ph("seconds", sec))));
                }
            });

            if (sec == 5) {announceWarmup();}
            if (sec <= 0)
            {
                cancelTask(countdownTask);
                countdownTask = null;
                startMatch();
                return;
            }
            countdownRemaining--;
        }, 20L, 20L);
    }

    private void announceWarmup()
    {
        lobbyChat.systemKey("lobby.warmup-header");
        lobbyChat.system(Component.empty());
        lobbyChat.systemKey("lobby.warmup-title");
        lobbyChat.system(Component.empty());
        List<Map.Entry<UUID, Double>> top = warmupDamage.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(3).toList();
        if (top.isEmpty())
        {
            lobbyChat.systemKey("lobby.warmup-empty");
        }
        else
        {
            int place = 1;
            for (Map.Entry<UUID, Double> entry : top)
            {
                Player pl = Bukkit.getPlayer(entry.getKey());
                String name = pl != null ? pl.getName() : "?";
                lobbyChat.systemKey("lobby.warmup-entry",
                    Msg.ph("place", place++),
                    Msg.ph("player", name),
                    Msg.ph("hearts", Math.round(entry.getValue() / 2.0)));
            }
        }
        lobbyChat.system(Component.empty());
        lobbyChat.systemKey("lobby.warmup-footer");
    }

    // ===== старт матча =====

    private void startMatch()
    {
        phase = Phase.RUNNING;
        remaining = arena.getDurationSeconds();
        World world = arena.getWorld();
        if (world == null)
        {
            DebugLog.log(Cat.SESSION, "start-abort arena=%s reason=world-not-loaded world=%s",
                arena.getId(), arena.getWorldName());
            forceStop();
            return;
        }
        DebugLog.log(Cat.SESSION, "start arena=%s world=%s players=%d duration=%ds dynamic-chests=%b",
            arena.getId(), world.getName(), lobby.size(), remaining, arena.isDynamicChests());

        // модификатор сессии решаем ДО генерации и раздачи: он крутит числа
        // (кол-во сундуков, стартовое золото, износ, ночь)
        decideModifier(world);

        // подсказки настройки убираем ДО генерации и телепорта: игрок не должен
        // появиться внутри стекла, а лут сундуков-маркеров в игре не участвует
        SetupMarkers.clearForMatch(arena);

        placeChests();
        spawnTraders();
        placeOres();
        placeTables();

        List<PotionEffect> startEffects = List.of(
            new PotionEffect(PotionEffectType.RESISTANCE, 20 * 20, 1, false, false),
            new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, false),
            new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 0, false, false));

        List<Location> spawnPool = new ArrayList<>(arena.getSpawns());
        Collections.shuffle(spawnPool, RANDOM);
        int spawnIndex = 0;

        for (UUID id : new ArrayList<>(lobby))
        {
            Player p = Bukkit.getPlayer(id);
            lobbyChat.remove(id);
            if (p == null) {plugin.arenas().unbind(id); continue;}

            playing.add(id);
            matchData.put(id, new MatchPlayer(id, p.getName()));
            gameChat.add(id);
            plugin.stats().add(id, p.getName(), "games_played", 1);

            Location spawn = spawnPool.get(spawnIndex % spawnPool.size());
            spawnIndex++;
            spawn.getChunk().load();
            DebugLog.log(Cat.PLAYER, "spawn arena=%s player=%s at=%s", arena.getId(), p.getName(), DebugLog.at(spawn));

            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            // XP — валюта зачарования: стартовый запас, пополняется убийствами
            p.setLevel(plugin.getConfig().getInt("match.start-xp-levels", 50));
            p.setExp(0f);
            p.teleport(spawn.clone().add(0.5, 0, 0.5));
            p.addPotionEffects(startEffects);

            giveKit(p);
            Msg.send(p, "game.start-effects-hint");
        }
        lobby.clear();
        warmupDamage.clear();

        // «Молния прозрения»: 1 на матч + 1 за каждую пару игроков
        respawnBlocks.distributeInsight(activeChests, playing.size());
        DebugLog.log(Cat.SESSION, "start-done arena=%s playing=%d chests=%d traders=%d loot-pool=%d contracts=%d",
            arena.getId(), playing.size(), activeChests.size(), traderLocations.size(),
            arena.getLoot().size(), arena.getContractIds().size());

        timerBar = BossBar.bossBar(
            Component.empty(), 1f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS);
        updateTimerBar();

        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void giveKit(Player p)
    {
        // системные предметы — всегда, независимо от каста
        ItemStack fork = Items.special(Material.GOLDEN_PICKAXE,
            Msg.get("game.fork-name"), Msg.getList("game.fork-lore"), "fork");
        ItemMeta meta = fork.getItemMeta();
        int maxDurability = fork.getType().getMaxDurability();
        int uses = Math.max(1, Math.min(arena.getForkUses(), maxDurability));
        ((Damageable) meta).setDamage(maxDurability - uses);
        fork.setItemMeta(meta);
        p.getInventory().addItem(fork);
        p.getInventory().addItem(Items.special(Material.COMPASS,
            Msg.get("game.assistant-name"), Msg.getList("game.assistant-lore"), "assistant"));
        p.getInventory().addItem(respawnBlocks.initFor(p));

        // выбранный каст: своё золото (или дефолт арены) + доп. предметы
        Kit kit = resolveKit(p.getUniqueId());
        int gold = (kit != null && kit.getGold() >= 0) ? kit.getGold() : arena.getStartGold();
        gold = (int) Math.round(gold * modMult("start-gold-mult"));
        if (gold > 0) {p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, gold));}
        if (kit != null)
        {
            kit.apply(p);
            DebugLog.log(Cat.PLAYER, "kit arena=%s player=%s kit=%s gold=%d items=%d",
                arena.getId(), p.getName(), kit.getId(), gold, kit.getItems().size());
        }
    }

    /** Разрешить выбор игрока в конкретный каст: учитывает none/random и дефолт арены. */
    private Kit resolveKit(UUID id)
    {
        List<Kit> kits = arena.getKits();
        if (kits.isEmpty()) {return null;}
        String choice = chosenKit.get(id);
        if (choice == null) {choice = arena.getDefaultKit();}
        if (choice == null || choice.equalsIgnoreCase("none")) {return null;}
        if (choice.equalsIgnoreCase("random")) {return kits.get(RANDOM.nextInt(kits.size()));}
        return arena.getKit(choice);
    }

    private void placeChests()
    {
        List<Location> pool = new ArrayList<>(arena.getChestSpots());
        Collections.shuffle(pool, RANDOM);
        int wanted = Math.max(1, (int) Math.round(arena.getChestCount() * modMult("chest-count-mult")));
        int count = Math.min(wanted, pool.size());
        for (int i = 0; i < count; i++)
        {
            Location loc = pool.get(i);
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(Material.CHEST);
            if (block.getState() instanceof Chest chest)
            {
                generateChestLoot(chest);
            }
            activeChests.add(block.getLocation());
            spawnChestStand(block.getLocation());
        }
        DebugLog.log(Cat.WORLD, "chests-placed arena=%s placed=%d wanted=%d spots=%d",
            arena.getId(), activeChests.size(), arena.getChestCount(), pool.size());
    }

    private void spawnChestStand(Location chestLoc)
    {
        ArmorStand stand = chestLoc.getWorld().spawn(chestLoc.clone().add(0.5, -1, 0.5), ArmorStand.class, as ->
        {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setCanPickupItems(false);
            as.setCustomNameVisible(false);
            as.setPersistent(true);
        });
        spawnedEntities.add(stand.getUniqueId());
        chestStands.put(chestLoc, stand.getUniqueId());
    }

    /**
     * Динамические сундуки (dynamic-chests): немаркированный сундук при первом
     * открытии получает лут и становится игровым (рефилл/LOOT/ключик).
     * Исходное содержимое запоминается и возвращается после матча.
     */
    public boolean registerDynamicChest(Chest chest)
    {
        if (phase != Phase.RUNNING || !arena.isDynamicChests()) {return false;}
        Block block = chest.getBlock();
        if (!block.getWorld().getName().equals(arena.getWorldName())) {return false;}
        Location loc = block.getLocation();
        if (activeChests.contains(loc)) {return false;}

        ItemStack[] contents = chest.getInventory().getContents();
        ItemStack[] originals = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++)
        {
            originals[i] = contents[i] == null ? null : contents[i].clone();
        }
        dynamicChestOriginals.put(loc, originals);

        generateChestLoot(chest);
        activeChests.add(loc);
        spawnChestStand(loc);
        DebugLog.log(Cat.CHEST, "dynamic-register arena=%s at=%s saved-slots=%d active=%d",
            arena.getId(), DebugLog.at(loc), originals.length, activeChests.size());
        return true;
    }

    public void generateChestLoot(Chest chest)
    {
        chest.getInventory().clear();
        List<Contract> available = new ArrayList<>();
        for (String cid : arena.getContractIds())
        {
            Contract c = plugin.contracts().get(cid);
            if (c != null && c.isComplete()) {available.add(c);}
        }

        int items = 2 + RANDOM.nextInt(3); // 2-4
        Set<Integer> usedSlots = new HashSet<>();
        for (int i = 0; i < items; i++)
        {
            int slot;
            do {slot = RANDOM.nextInt(27);} while (usedSlots.contains(slot));
            usedSlots.add(slot);

            ItemStack item;
            if (!available.isEmpty() && RANDOM.nextInt(4) == 0)
            {
                item = ContractPapers.create(available.get(RANDOM.nextInt(available.size())));
            }
            else
            {
                item = pickLoot();
                if (item == null) {continue;}
            }
            chest.getInventory().setItem(slot, item);
        }
    }

    private ItemStack pickLoot()
    {
        List<WeightedItem> loot = arena.getLoot();
        if (loot.isEmpty()) {return null;}
        int total = 0;
        for (WeightedItem entry : loot) {total += entry.weight();}
        int roll = RANDOM.nextInt(total);
        for (WeightedItem entry : loot)
        {
            roll -= entry.weight();
            if (roll < 0) {return applyWear(entry.item().clone());}
        }
        return applyWear(loot.get(loot.size() - 1).item().clone());
    }

    /**
     * Случайный износ (wear-min/max-percent арены): каждый предмет с прочностью
     * ломан по-своему — и в луте, и в покупках у торговцев. Предметы с уже
     * заданным уроном (выставлен руками через additem/addtrade) не трогаются.
     */
    public ItemStack applyWear(ItemStack item)
    {
        int maxPct = arena.getWearMaxPercent();
        if (maxPct <= 0) {return item;}
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0 || !(item.getItemMeta() instanceof Damageable meta)) {return item;}
        if (meta.hasDamage()) {return item;}

        int wearAdd = (int) Math.round(modAdd("wear-add"));
        int minPct = Math.max(0, Math.min(arena.getWearMinPercent() + wearAdd, 99));
        maxPct = Math.max(minPct, Math.min(99, maxPct + wearAdd));
        int pct = minPct + RANDOM.nextInt(maxPct - minPct + 1);
        int damage = Math.min(maxDurability - 1, maxDurability * pct / 100);
        if (damage <= 0) {return item;}
        meta.setDamage(damage);
        item.setItemMeta(meta);
        return item;
    }

    private void spawnTraders()
    {
        List<Map.Entry<Location, String>> pool = new ArrayList<>(arena.getTraderSpots().entrySet());
        Collections.shuffle(pool, RANDOM);
        int count = Math.min(arena.getTraderCount(), pool.size());
        for (int i = 0; i < count; i++)
        {
            Location loc = pool.get(i).getKey();
            String typeId = pool.get(i).getValue();
            var type = plugin.traders().get(typeId);
            if (type == null || loc.getWorld() == null) {continue;}
            Villager villager = loc.getWorld().spawn(loc.clone().add(0.5, 0, 0.5), Villager.class, v ->
            {
                v.setAI(false);
                v.setInvulnerable(true);
                v.setPersistent(true);
                v.setCanPickupItems(false);
                v.customName(type.displayName());
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(Keys.TRADER_TYPE,
                    PersistentDataType.STRING, type.getId());
            });
            spawnedEntities.add(villager.getUniqueId());
            traderLocations.add(villager.getLocation());
            DebugLog.log(Cat.WORLD, "trader-spawn arena=%s type=%s at=%s", arena.getId(), typeId, DebugLog.at(loc));
        }
        DebugLog.log(Cat.WORLD, "traders-placed arena=%s placed=%d wanted=%d spots=%d",
            arena.getId(), traderLocations.size(), arena.getTraderCount(), pool.size());
    }

    private void placeOres()
    {
        for (Location loc : arena.getOreSpots())
        {
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(randomOre());
        }
        DebugLog.log(Cat.WORLD, "ores-placed arena=%s count=%d", arena.getId(), arena.getOreSpots().size());
    }

    private Material randomOre()
    {
        // 5 уголь, 4 железо, 3 золото, 2 алмаз, 1 изумруд, 4 заражённый камень (из 19)
        int roll = RANDOM.nextInt(19);
        if (roll < 5) {return Material.COAL_ORE;}
        if (roll < 9) {return Material.IRON_ORE;}
        if (roll < 12) {return Material.GOLD_ORE;}
        if (roll < 14) {return Material.DIAMOND_ORE;}
        if (roll < 15) {return Material.EMERALD_ORE;}
        return Material.INFESTED_STONE;
    }

    private void placeTables()
    {
        List<Location> pool = new ArrayList<>(arena.getTableSpots());
        Collections.shuffle(pool, RANDOM);
        int count = Math.min(arena.getTableCount(), pool.size());
        for (int i = 0; i < count; i++)
        {
            Location loc = pool.get(i);
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(Material.ENCHANTING_TABLE);
        }
        DebugLog.log(Cat.WORLD, "tables-placed arena=%s placed=%d wanted=%d spots=%d",
            arena.getId(), count, arena.getTableCount(), pool.size());
    }

    // ===== игровой цикл =====

    private void tick()
    {
        if (phase != Phase.RUNNING) {return;}
        if (remaining <= 0) {return;} // финальная битва без таймера
        remaining--;

        updateTimerBar();

        int interval = Math.max(30, (int) Math.round(arena.getEventIntervalSeconds() * modMult("event-interval-mult")));
        if (currentEvent == null && remaining > 0 && remaining % interval == 0 && remaining != arena.getDurationSeconds())
        {
            startRandomEvent();
        }
        else if (currentEvent != null)
        {
            currentEvent.onTick(this);
            eventTicksLeft--;
            if (eventTicksLeft <= 0) {endCurrentEvent();}
        }

        int salaryInterval = Math.max(60, arena.getSalaryIntervalSeconds());
        if (remaining > 0 && remaining % salaryInterval == 0)
        {
            int salary = Math.max(0, (int) Math.round(arena.getSalaryGold() * modMult("salary-mult")));
            DebugLog.log(Cat.SESSION, "salary arena=%s gold=%d alive=%d remaining=%ds",
                arena.getId(), salary, playing.size(), remaining);
            gameChat.systemKey("game.salary", Msg.ph("n", salary));
            forEachPlaying(p ->
            {
                p.getWorld().strikeLightningEffect(p.getLocation());
                giveGold(p, salary);
            });
        }

        int strikeInterval = Math.max(60, plugin.getConfig().getInt("respawn-block.lightning-interval-seconds", 300));
        if (remaining > 0 && remaining % strikeInterval == 0 && remaining != arena.getDurationSeconds())
        {
            respawnBlocks.strikeAll();
        }

        if (remaining == arena.getGlowSecondsBeforeEnd())
        {
            glowActive = true;
            DebugLog.log(Cat.SESSION, "glow-start arena=%s bonus=%d alive=%d",
                arena.getId(), arena.getGlowBonusGold(), playing.size());
            gameChat.systemKey("game.glow-warning");
            forEachPlaying(p ->
            {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
                giveGold(p, arena.getGlowBonusGold());
            });
        }

        if (remaining > 0 && remaining % 600 == 0)
        {
            gameChat.systemKey("game.time-left-minutes", Msg.ph("n", remaining / 60));
        }
        if (remaining > 0 && remaining < 15)
        {
            gameChat.systemKey("game.time-left-seconds", Msg.ph("n", remaining));
        }

        if (remaining == 0)
        {
            finalBattle();
        }
    }

    // ===== боссбар-таймер =====

    private void updateTimerBar()
    {
        if (timerBar == null) {return;}
        int duration = Math.max(1, arena.getDurationSeconds());
        timerBar.progress(Math.max(0f, Math.min(1f, remaining / (float) duration)));
        timerBar.name(Msg.get("game.timer-bar", Msg.ph("time", formatTime(remaining))));
        timerBar.color(remaining <= 60 ? BossBar.Color.RED
            : remaining <= arena.getGlowSecondsBeforeEnd() ? BossBar.Color.YELLOW
            : BossBar.Color.GREEN);
        showTimerBarToAll();
    }

    /** Показ идемпотентен — один и тот же инстанс бара не дублируется. */
    private void showTimerBarToAll()
    {
        if (timerBar == null) {return;}
        forEachOnline(playing, p -> p.showBossBar(timerBar));
        forEachOnline(spectators, p -> p.showBossBar(timerBar));
    }

    public void hideTimerBar(Player p)
    {
        if (timerBar != null) {p.hideBossBar(timerBar);}
    }

    private void removeTimerBar()
    {
        if (timerBar == null) {return;}
        BossBar bar = timerBar;
        timerBar = null;
        forEachOnline(playing, p -> p.hideBossBar(bar));
        forEachOnline(spectators, p -> p.hideBossBar(bar));
        forEachOnline(lobby, p -> p.hideBossBar(bar));
    }

    private String formatTime(int seconds)
    {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void startRandomEvent()
    {
        List<GameEvent> candidates = new ArrayList<>();
        for (GameEvent event : GameEvent.values())
        {
            if (event.canStart(this)) {candidates.add(event);}
        }
        if (candidates.isEmpty()) {DebugLog.log(Cat.EVENT, "no-candidates arena=%s", arena.getId()); return;}
        GameEvent event = candidates.get(RANDOM.nextInt(candidates.size()));
        DebugLog.log(Cat.EVENT, "start arena=%s event=%s window=%ds candidates=%d alive=%d",
            arena.getId(), event.name(), event.windowSeconds(), candidates.size(), playing.size());

        gameChat.systemKey("events.announce-header");
        for (String key : event.announceKeys()) {gameChat.systemKey(key);}

        eventPositions.clear();
        eventFlagged.clear();
        event.onAnnounce(this);

        if (event.windowSeconds() <= 0) {event.onEnd(this); return;}
        currentEvent = event;
        eventTicksLeft = event.windowSeconds();
    }

    private void endCurrentEvent()
    {
        GameEvent event = currentEvent;
        currentEvent = null;
        DebugLog.log(Cat.EVENT, "end arena=%s event=%s flagged=%d alive=%d",
            arena.getId(), event.name(), eventFlagged.size(), playing.size());
        forEachPlaying(p -> event.resolvePlayer(this, p));
        event.onEnd(this);
        eventPositions.clear();
        eventFlagged.clear();
    }

    // ===== хелперы для GameEvent =====

    /** LOCKDOWN: зафиксировать позиции всех живых на момент объявления. */
    public void captureEventPositions()
    {
        eventPositions.clear();
        forEachOnline(playing, p -> eventPositions.put(p.getUniqueId(), p.getLocation().clone()));
    }

    public Map<UUID, Location> getEventPositions() {return eventPositions;}
    public boolean isEventFlagged(Player p) {return eventFlagged.contains(p.getUniqueId());}

    public void flagEventAction(Player p)
    {
        if (eventFlagged.add(p.getUniqueId()))
        {
            DebugLog.log(Cat.EVENT, "flag arena=%s player=%s event=%s",
                arena.getId(), p.getName(), currentEvent == null ? "-" : currentEvent.name());
        }
    }

    public void setBloodMoon(boolean v)
    {
        DebugLog.log(Cat.EVENT, "blood-moon arena=%s value=%b", arena.getId(), v);
        bloodMoon = v;
    }
    public boolean isBloodMoon() {return bloodMoon;}

    /** Живые игроки, кроме ожидающих возрождения (те 5 сек «мертвы»). */
    public void forEachPlaying(java.util.function.Consumer<Player> action)
    {
        for (UUID id : new ArrayList<>(playing))
        {
            if (respawnBlocks.isAwaitingRespawn(id)) {continue;}
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {action.accept(p);}
        }
    }

    /** BRIBE: случайный предмет из пула лута арены. false — пул пуст. */
    public boolean giveRandomLoot(Player p)
    {
        ItemStack item = pickLoot();
        if (item == null) {return false;}
        var leftovers = p.getInventory().addItem(item);
        for (ItemStack rest : leftovers.values())
        {
            Item drop = p.getWorld().dropItem(p.getLocation().add(0, 0.5, 0), rest);
            droppedItems.add(drop.getUniqueId());
        }
        return true;
    }

    private void finalBattle()
    {
        cancelTask(mainTask);
        mainTask = null;
        finalBattleStarted = true;
        DebugLog.log(Cat.SESSION, "final-battle arena=%s alive=%d respawn-blocks=%b",
            arena.getId(), playing.size(), respawnBlocks.hasPlacedBlocks());
        offlineGuards.onFinalBattle();
        if (respawnBlocks.hasPlacedBlocks())
        {
            gameChat.systemKey("respawn-block.annul-broadcast");
            respawnBlocks.annulAll();
        }
        gameChat.systemKey("game.final-battle");
        if (timerBar != null)
        {
            timerBar.name(Msg.get("game.timer-bar-final"));
            timerBar.color(BossBar.Color.RED);
            timerBar.progress(1f);
            showTimerBarToAll();
        }
        List<Location> pool = arena.getFinalSpawns().isEmpty() ? arena.getSpawns() : arena.getFinalSpawns();
        List<Location> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, RANDOM);
        int i = 0;
        for (UUID id : playing)
        {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {continue;}
            Location loc = shuffled.get(i % shuffled.size());
            i++;
            p.teleport(loc.clone().add(0.5, 0, 0.5));
        }
    }

    // ===== золото =====

    public void giveGold(Player p, int amount)
    {
        var leftovers = p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, amount));
        for (ItemStack rest : leftovers.values())
        {
            Item item = p.getWorld().dropItem(p.getLocation().add(0, 0.5, 0), rest);
            droppedItems.add(item.getUniqueId());
        }
        if (DebugLog.on())
        {
            DebugLog.log(Cat.PLAYER, "gold arena=%s player=%s amount=%d dropped=%d total=%d",
                arena.getId(), p.getName(), amount, leftovers.size(),
                Items.countMaterial(p, Material.GOLD_INGOT));
        }
    }

    // ===== контракты =====

    /** Продвинуть контракты типа type у игрока (delta шагов), matcher — фильтр по контракту. */
    public void progressContracts(Player p, ContractType type, Predicate<Contract> matcher, int delta)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            String cid = ContractPapers.contractIdOf(item);
            if (cid == null) {continue;}
            Contract contract = plugin.contracts().get(cid);
            if (contract == null || contract.getType() != type || !matcher.test(contract)) {continue;}

            int progress = Math.min(contract.getAmount(), ContractPapers.progressOf(item) + delta);
            ContractPapers.write(item, contract, progress);
            DebugLog.log(Cat.CONTRACT, "progress arena=%s player=%s contract=%s type=%s %d/%d",
                arena.getId(), p.getName(), contract.getId(), type, progress, contract.getAmount());
            Msg.send(p, "contract.progress",
                Msg.ph("name", contract.getId()),
                Msg.ph("progress", progress),
                Msg.ph("amount", contract.getAmount()));

            if (progress >= contract.getAmount())
            {
                completeContract(p, item, contract);
            }
            return; // одна бумага за одно действие (как в оригинале)
        }
    }

    /** FIND: прогресс = сколько предметов-целей у игрока сейчас (максимум запоминается). */
    public void refreshFindContracts(Player p)
    {
        themes.refreshFind(p);
        for (ItemStack item : p.getInventory().getContents())
        {
            String cid = ContractPapers.contractIdOf(item);
            if (cid == null) {continue;}
            Contract contract = plugin.contracts().get(cid);
            if (contract == null || contract.getType() != ContractType.FIND) {continue;}
            Material target = Material.matchMaterial(contract.getIdle());
            if (target == null) {continue;}

            int count = Items.countMaterial(p, target);
            int old = ContractPapers.progressOf(item);
            int progress = Math.max(old, Math.min(contract.getAmount(), count));
            if (progress == old && count < contract.getAmount()) {continue;}

            ContractPapers.write(item, contract, progress);
            if (progress != old)
            {
                Msg.send(p, "contract.progress",
                    Msg.ph("name", contract.getId()),
                    Msg.ph("progress", progress),
                    Msg.ph("amount", contract.getAmount()));
            }
            if (count >= contract.getAmount())
            {
                completeContract(p, item, contract);
            }
        }
    }

    private void completeContract(Player p, ItemStack paper, Contract contract)
    {
        DebugLog.log(Cat.CONTRACT, "complete arena=%s player=%s contract=%s type=%s reward=%d",
            arena.getId(), p.getName(), contract.getId(), contract.getType(), contract.getPrice());
        paper.setAmount(paper.getAmount() - 1);
        Msg.send(p, "contract.complete-header");
        Msg.send(p, "contract.complete-body");
        Msg.send(p, "contract.complete-footer");
        giveGold(p, contract.getPrice());
        MatchPlayer data = matchData.get(p.getUniqueId());
        if (data != null) {data.quests++;}
        plugin.stats().add(p.getUniqueId(), p.getName(), "quests_completed", 1);
    }

    /** LOOT: первый раз открыт сундук. */
    public void handleChestLooted(Player p, Location chestLoc)
    {
        MatchPlayer data = matchData.get(p.getUniqueId());
        if (data == null) {return;}
        if (!data.lootedChests.add(chestLoc)) {return;}
        DebugLog.log(Cat.CHEST, "loot-first-open arena=%s player=%s at=%s total-looted=%d",
            arena.getId(), p.getName(), DebugLog.at(chestLoc), data.lootedChests.size());
        progressContracts(p, ContractType.LOOT, c -> true, 1);
        themes.progress(p, ThemeType.LOOT, t -> true, 1);
    }

    // ===== рефилл сундуков =====

    public void scheduleRefillIfEmpty(Block block)
    {
        Location loc = block.getLocation();
        if (!activeChests.contains(loc) || refillTasks.containsKey(loc)) {return;}
        if (!(block.getState() instanceof Chest chest)) {return;}
        if (!chest.getInventory().isEmpty()) {return;}

        ArmorStand stand = standAt(loc);
        if (stand != null)
        {
            stand.customName(Items.flat(Msg.get("chest.empty-name")));
            stand.setCustomNameVisible(true);
        }

        int delay = Math.max(5, (int) Math.round(
            plugin.getConfig().getInt("chest-refill-seconds", 180) * modMult("refill-mult")));
        DebugLog.log(Cat.CHEST, "refill-scheduled arena=%s at=%s in=%ds", arena.getId(), DebugLog.at(loc), delay);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            refillTasks.remove(loc);
            if (phase != Phase.RUNNING) {return;}
            if (loc.getBlock().getState() instanceof Chest target)
            {
                generateChestLoot(target);
                DebugLog.log(Cat.CHEST, "refill-done arena=%s at=%s", arena.getId(), DebugLog.at(loc));
                ArmorStand as = standAt(loc);
                if (as != null)
                {
                    as.customName(Items.flat(Msg.get("chest.refilled-name")));
                    as.setCustomNameVisible(true);
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                    {
                        if (as.isValid()) {as.setCustomNameVisible(false);}
                    }, 20L * 10);
                }
            }
        }, delay * 20L);
        refillTasks.put(loc, task);
    }

    /** Принудительный рефилл игрового сундука («Волшебный ключик»). false — сундук не игровой. */
    public boolean forceRefillChest(Block block)
    {
        Location loc = block.getLocation();
        if (phase != Phase.RUNNING || !activeChests.contains(loc)) {return false;}
        if (!(block.getState() instanceof Chest chest)) {return false;}

        BukkitTask pending = refillTasks.remove(loc);
        if (pending != null) {pending.cancel();}
        generateChestLoot(chest);
        DebugLog.log(Cat.CHEST, "refill-forced arena=%s at=%s had-pending=%b",
            arena.getId(), DebugLog.at(loc), pending != null);

        ArmorStand stand = standAt(loc);
        if (stand != null)
        {
            stand.customName(Items.flat(Msg.get("chest.refilled-name")));
            stand.setCustomNameVisible(true);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
            {
                if (stand.isValid()) {stand.setCustomNameVisible(false);}
            }, 20L * 10);
        }
        return true;
    }

    private ArmorStand standAt(Location chestLoc)
    {
        UUID id = chestStands.get(chestLoc);
        if (id == null) {return null;}
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    // ===== бой и смерть =====

    public void recordDamager(Player victim, Player damager)
    {
        MatchPlayer data = matchData.get(victim.getUniqueId());
        if (data == null) {return;}
        data.lastDamager = damager.getUniqueId();
        data.lastDamagerAt = System.currentTimeMillis();
        DebugLog.log(Cat.COMBAT, "damager arena=%s victim=%s damager=%s victim-hp=%.1f blood-moon=%b",
            arena.getId(), victim.getName(), damager.getName(), victim.getHealth(), bloodMoon);
    }

    /** Запомнить блок для восстановления после матча (решётки и т.п.). */
    public void rememberEditedBlock(Block block)
    {
        if (editedBlocks.putIfAbsent(block.getLocation(), block.getBlockData().clone()) == null)
        {
            DebugLog.log(Cat.WORLD, "block-remember arena=%s at=%s was=%s total=%d",
                arena.getId(), DebugLog.at(block.getLocation()), block.getType(), editedBlocks.size());
        }
    }

    /**
     * То же, но с явным «прежним» состоянием — для BlockPlaceEvent, где блок
     * в мире уже заменён и читать его поздно (нужен getBlockReplacedState).
     */
    public void rememberEditedBlock(Location loc, BlockData original)
    {
        if (editedBlocks.putIfAbsent(loc, original.clone()) == null)
        {
            DebugLog.log(Cat.WORLD, "block-remember arena=%s at=%s was=%s total=%d",
                arena.getId(), DebugLog.at(loc), original.getMaterial(), editedBlocks.size());
        }
    }

    public void dropInventory(Player p, Location where)
    {
        int dropped = 0;
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            // блок возрождения не выпадает — он гибнет вместе с владельцем
            if (Items.isSpecial(item, "respawn_block")) {continue;}
            Item drop = p.getWorld().dropItem(where.clone().add(0, 1.2, 0), item);
            droppedItems.add(drop.getUniqueId());
            dropped++;
        }
        p.getInventory().clear();
        DebugLog.log(Cat.PLAYER, "drop-inventory arena=%s player=%s stacks=%d at=%s",
            arena.getId(), p.getName(), dropped, DebugLog.at(where));
    }

    public void trackDrop(Item item)
    {
        droppedItems.add(item.getUniqueId());
    }

    /** Смерть от урона: сначала попытка возрождения на блоке, иначе выбывание. */
    public void handleDeath(Player p)
    {
        UUID id = p.getUniqueId();
        if (!playing.contains(id)) {return;}

        DebugLog.log(Cat.PLAYER, "death arena=%s player=%s at=%s alive=%d", arena.getId(), p.getName(),
            DebugLog.at(p.getLocation()), playing.size());
        creditKillAndAnnounce(p);
        plugin.stats().add(id, p.getName(), "deaths", 1);

        MatchPlayer data = matchData.get(id);
        if (data != null) {data.lastDamager = null;}

        if (respawnBlocks.tryScheduleRespawn(p)) {return;}
        finishElimination(p, false);
    }

    /** Окончательное выбывание без возрождения (leave / quit / кик). */
    public void eliminate(Player p, boolean quit)
    {
        UUID id = p.getUniqueId();
        if (!playing.contains(id)) {return;}
        if (respawnBlocks.cancelPendingRespawn(id))
        {
            // игрок вышел, ожидая возрождения: смерть уже объявлена
            finishElimination(p, quit);
            return;
        }
        creditKillAndAnnounce(p);
        plugin.stats().add(id, p.getName(), "deaths", 1);
        finishElimination(p, quit);
    }

    /** Выбывание игрока, чья смерть уже объявлена (сорвавшееся отложенное возрождение). */
    public void eliminateAfterFailedRespawn(Player p)
    {
        if (!playing.contains(p.getUniqueId())) {return;}
        finishElimination(p, false);
    }

    /** Кредит убийце (счёт, контракты, прогресс изумрудного блока) + сообщение о смерти. */
    private void creditKillAndAnnounce(Player p)
    {
        MatchPlayer data = matchData.get(p.getUniqueId());
        if (data != null && data.lastDamager != null
            && System.currentTimeMillis() - data.lastDamagerAt <= KILL_CREDIT_MILLIS
            && playing.contains(data.lastDamager))
        {
            Player killer = Bukkit.getPlayer(data.lastDamager);
            if (killer != null)
            {
                MatchPlayer killerData = matchData.get(killer.getUniqueId());
                if (killerData != null) {killerData.kills++;}
                DebugLog.log(Cat.COMBAT, "kill-credit arena=%s killer=%s victim=%s kills=%d",
                    arena.getId(), killer.getName(), p.getName(), killerData == null ? -1 : killerData.kills);
                plugin.stats().add(killer.getUniqueId(), killer.getName(), "kills", 1);
                killer.giveExpLevels(plugin.getConfig().getInt("match.kill-xp-levels", 10));
                progressContracts(killer, ContractType.KILLS, c -> true, 1);
                themes.progress(killer, ThemeType.KILLS, t -> true, 1);
                respawnBlocks.onOwnerKill(killer);
            }
        }

        announceDeath(p.getName());
    }

    /** Сообщение о смерти в оба канала (используется и оффлайн-стражами). */
    public void announceDeath(String playerName)
    {
        String deadMsgRaw = randomDeadMessage();
        gameChat.system(Msg.get("game.death-broadcast",
            Msg.phC("message", Msg.mm(deadMsgRaw, Msg.ph("player", playerName)))));
        spectatorChat.system(Msg.get("game.death-broadcast",
            Msg.phC("message", Msg.mm(deadMsgRaw, Msg.ph("player", playerName)))));
    }

    private void finishElimination(Player p, boolean quit)
    {
        UUID id = p.getUniqueId();
        playing.remove(id);
        DebugLog.log(Cat.PLAYER, "eliminate arena=%s player=%s quit=%b alive=%d",
            arena.getId(), p.getName(), quit, playing.size());
        gameChat.remove(id);
        plugin.stats().add(id, p.getName(), "loses", 1);
        respawnBlocks.onOwnerEliminated(id);

        if (!quit)
        {
            p.setGameMode(GameMode.SPECTATOR);
            spectators.add(id);
            spectatorChat.add(id);
        }

        if (playing.size() > 1)
        {
            gameChat.systemKey("game.players-left", Msg.ph("n", playing.size()));
            spectatorChat.systemKey("game.players-left", Msg.ph("n", playing.size()));
            return;
        }
        finish();
    }

    private String randomDeadMessage()
    {
        List<String> pool = arena.getDeadMessages();
        if (pool.isEmpty()) {pool = Msg.rawList("death-messages");}
        if (pool.isEmpty()) {return "<yellow>Игрок <aqua><player><yellow> выбыл";}
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** Победа/конец матча (осталось <= 1 живых). */
    private void finish()
    {
        if (phase == Phase.ENDING) {return;}
        phase = Phase.ENDING;
        cancelTask(mainTask);
        mainTask = null;

        // MVP: лучший по убийствам среди всех участников
        MatchPlayer mvp = null;
        for (MatchPlayer data : matchData.values())
        {
            plugin.stats().recordGameKills(data.uuid, data.name, data.kills);
            if (data.kills > 0 && (mvp == null || data.kills > mvp.kills)) {mvp = data;}
        }
        if (mvp != null) {plugin.stats().add(mvp.uuid, mvp.name, "mvp_games", 1);}
        DebugLog.log(Cat.SESSION, "finish arena=%s alive=%d participants=%d mvp=%s",
            arena.getId(), playing.size(), matchData.size(), mvp == null ? "-" : mvp.name);

        int stopDelay = 3;
        if (playing.size() == 1)
        {
            UUID winnerId = playing.iterator().next();
            MatchPlayer data = matchData.get(winnerId);
            if (data != null)
            {
                // победитель может быть и оффлайн (пережил всех под стражем) — статистика всё равно его
                plugin.stats().add(winnerId, data.name, "wins", 1);
                DebugLog.log(Cat.SESSION, "winner arena=%s player=%s kills=%d quests=%d trades=%d ores=%d",
                    arena.getId(), data.name, data.kills, data.quests, data.trades, data.ores);
                broadcastAll(Msg.get("game.win-header"));
                broadcastAll(Component.empty());
                broadcastAll(Msg.get("game.win-player", Msg.ph("player", data.name)));
                broadcastAll(Msg.get("game.win-quests", Msg.ph("n", data.quests)));
                broadcastAll(Msg.get("game.win-kills", Msg.ph("n", data.kills)));
                broadcastAll(Msg.get("game.win-trades", Msg.ph("n", data.trades)));
                broadcastAll(Component.empty());
                broadcastAll(Msg.get("game.win-footer"));
            }
            stopDelay = 10;
        }

        int delay = stopDelay;
        stopTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable()
        {
            int sec = delay;

            @Override
            public void run()
            {
                if (sec % 10 == 0 || sec < 6)
                {
                    broadcastAll(Msg.get("game.stop-countdown", Msg.ph("seconds", sec)));
                }
                if (timerBar != null)
                {
                    timerBar.name(Msg.get("game.timer-bar-stop", Msg.ph("seconds", sec)));
                    timerBar.color(BossBar.Color.YELLOW);
                    timerBar.progress(delay <= 0 ? 0f : Math.max(0f, Math.min(1f, sec / (float) delay)));
                    showTimerBarToAll();
                }
                if (sec <= 0)
                {
                    cancelTask(stopTask);
                    stopTask = null;
                    cleanup();
                    return;
                }
                sec--;
            }
        }, 20L, 20L);
    }

    private void broadcastAll(Component message)
    {
        gameChat.system(message);
        spectatorChat.system(message);
    }

    /** Немедленная остановка (админ, onDisable, удаление арены). */
    public void forceStop()
    {
        DebugLog.log(Cat.SESSION, "force-stop arena=%s phase=%s lobby=%d playing=%d",
            arena.getId(), phase, lobby.size(), playing.size());
        cancelTask(countdownTask); countdownTask = null;
        cancelTask(mainTask); mainTask = null;
        cancelTask(stopTask); stopTask = null;
        phase = Phase.ENDING;
        cleanup();
    }

    // ===== очистка =====

    private void cleanup()
    {
        DebugLog.log(Cat.SESSION, "cleanup-start arena=%s blocks=%d chests=%d dynamic=%d entities=%d drops=%d refills=%d",
            arena.getId(), editedBlocks.size(), activeChests.size(), dynamicChestOriginals.size(),
            spawnedEntities.size(), droppedItems.size(), refillTasks.size());
        removeTimerBar();
        for (BukkitTask task : refillTasks.values()) {task.cancel();}
        refillTasks.clear();

        // сундуки: очистить содержимое перед восстановлением
        for (Location loc : activeChests)
        {
            if (loc.getWorld() == null) {continue;}
            if (loc.getBlock().getState() instanceof Chest chest) {chest.getInventory().clear();}
        }

        // динамические сундуки — это блоки карты: вернуть их исходное содержимое
        for (Map.Entry<Location, ItemStack[]> entry : dynamicChestOriginals.entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {continue;}
            if (loc.getBlock().getState() instanceof Chest chest)
            {
                chest.getInventory().setContents(entry.getValue());
            }
        }
        dynamicChestOriginals.clear();

        // вернуть все изменённые блоки (решётки, сундуки, руды, столы)
        int restored = 0;
        int skipped = 0;
        for (Map.Entry<Location, BlockData> entry : editedBlocks.entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {skipped++; continue;}
            loc.getBlock().setBlockData(entry.getValue(), false);
            restored++;
        }
        DebugLog.log(Cat.WORLD, "blocks-restored arena=%s restored=%d skipped-no-world=%d",
            arena.getId(), restored, skipped);
        editedBlocks.clear();
        activeChests.clear();
        chestStands.clear();
        traderLocations.clear();

        // мир вернулся в исходное — возвращаем и подсказки настройки
        SetupMarkers.placeAll(arena);

        // removed < tracked — часть сущностей исчезла раньше (смерть, деспавн), это норма
        int entitiesRemoved = 0;
        int entitiesTracked = spawnedEntities.size();
        for (UUID id : spawnedEntities)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove(); entitiesRemoved++;}
        }
        spawnedEntities.clear();

        int dropsRemoved = 0;
        int dropsTracked = droppedItems.size();
        for (UUID id : droppedItems)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove(); dropsRemoved++;}
        }
        droppedItems.clear();
        DebugLog.log(Cat.WORLD, "entities-removed arena=%s spawned=%d/%d drops=%d/%d",
            arena.getId(), entitiesRemoved, entitiesTracked, dropsRemoved, dropsTracked);

        Set<UUID> everyone = new LinkedHashSet<>();
        everyone.addAll(lobby);
        everyone.addAll(playing);
        everyone.addAll(spectators);
        for (UUID id : everyone)
        {
            plugin.arenas().unbind(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null)
            {
                PlayerSnapshot.restore(plugin, p);
                DebugLog.log(Cat.PLAYER, "snapshot-restore arena=%s player=%s", arena.getId(), p.getName());
            }
        }
        DebugLog.log(Cat.SESSION, "cleanup-done arena=%s players-restored=%d", arena.getId(), everyone.size());
        lobby.clear();
        playing.clear();
        spectators.clear();
        lobbyChat.clear();
        gameChat.clear();
        spectatorChat.clear();
        matchData.clear();
        chosenKit.clear();
        modifierVotes.clear();
        activeModifier = null;
        cooldowns.clear();
        currentEvent = null;
        eventPositions.clear();
        eventFlagged.clear();
        bloodMoon = false;
        glowActive = false;
        finalBattleStarted = false;
        forcedStart = false;
        respawnBlocks.clear();
        offlineGuards.clear();

        dispose();
    }

    private void dispose()
    {
        arena.setSession(null);
    }

    // ===== отладка (escape.admin.debug) =====

    /** Принудительный запуск конкретного события (обходит canStart и таймер). */
    public boolean debugStartEvent(GameEvent event)
    {
        if (phase != Phase.RUNNING) {return false;}
        if (currentEvent != null) {endCurrentEvent();}
        eventPositions.clear();
        eventFlagged.clear();
        event.onAnnounce(this);
        if (event.windowSeconds() <= 0) {event.onEnd(this); return true;}
        currentEvent = event;
        eventTicksLeft = event.windowSeconds();
        return true;
    }

    /** Досрочное включение фазы свечения. */
    public boolean debugStartGlow()
    {
        if (phase != Phase.RUNNING || glowActive) {return false;}
        glowActive = true;
        gameChat.systemKey("game.glow-warning");
        forEachPlaying(p ->
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
            giveGold(p, arena.getGlowBonusGold());
        });
        return true;
    }

    public boolean debugFinalBattle()
    {
        if (phase != Phase.RUNNING || finalBattleStarted) {return false;}
        finalBattle();
        return true;
    }

    public boolean debugFinish()
    {
        if (phase != Phase.RUNNING) {return false;}
        finish();
        return true;
    }

    /** Завершить первую бумагу-контракт в инвентаре (полная выдача награды). */
    public boolean debugCompleteContract(Player p)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            String cid = ContractPapers.contractIdOf(item);
            if (cid == null) {continue;}
            Contract contract = plugin.contracts().get(cid);
            if (contract == null) {continue;}
            completeContract(p, item, contract);
            return true;
        }
        return false;
    }

    /** Принудительный рефилл всех активных сундуков. */
    public int debugRefillAll()
    {
        int count = 0;
        for (Location loc : List.copyOf(activeChests))
        {
            if (forceRefillChest(loc.getBlock())) {count++;}
        }
        return count;
    }

    /** Принудительный старт админом (достаточно 1 игрока — соло-режим для отладки). */
    public boolean forceStart()
    {
        if (phase == Phase.RUNNING || phase == Phase.ENDING) {return false;}
        if (lobby.isEmpty()) {return false;}
        cancelTask(countdownTask);
        countdownTask = null;
        forcedStart = true;
        DebugLog.log(Cat.ADMIN, "force-start arena=%s lobby=%d", arena.getId(), lobby.size());
        startCountdown(Math.min(5, arena.getStartDelayFullSeconds()));
        return true;
    }

    /** Остановка админом с отсчётом. */
    public void adminStop()
    {
        DebugLog.log(Cat.ADMIN, "admin-stop arena=%s phase=%s", arena.getId(), phase);
        if (phase == Phase.RUNNING)
        {
            finishForced();
            return;
        }
        forceStop();
    }

    private void finishForced()
    {
        phase = Phase.ENDING;
        cancelTask(mainTask);
        mainTask = null;
        int delay = 5;
        stopTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable()
        {
            int sec = delay;

            @Override
            public void run()
            {
                broadcastAll(Msg.get("game.stop-countdown", Msg.ph("seconds", sec)));
                if (sec <= 0)
                {
                    cancelTask(stopTask);
                    stopTask = null;
                    cleanup();
                    return;
                }
                sec--;
            }
        }, 1L, 20L);
    }

    // ===== утилиты =====

    private void forEachOnline(Set<UUID> ids, java.util.function.Consumer<Player> action)
    {
        for (UUID id : new ArrayList<>(ids))
        {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {action.accept(p);}
        }
    }

    private void cancelTask(BukkitTask task)
    {
        if (task != null && !task.isCancelled()) {task.cancel();}
    }
}
