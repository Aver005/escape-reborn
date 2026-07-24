package me.aver005.escape.game;

import me.aver005.escape.arena.EscapeArena;
import me.aver005.escape.util.EscapeKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import me.aver005.escape.EscapePlugin;
import ru.kiviuly.mg.api.arena.Arena;
import ru.kiviuly.mg.api.game.GamePhase;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import me.aver005.escape.arena.SetupMarkers;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.contract.ContractPapers;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.kit.Kit;
import me.aver005.escape.loot.LootCategory;
import me.aver005.escape.loot.LootCategoryRegistry;
import me.aver005.escape.modifier.Modifier;
import me.aver005.escape.theme.ThemeType;
import me.aver005.escape.util.DebugLog;
import me.aver005.escape.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Items;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
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

/**
 * Правила Escape для одного матча.
 *
 * <p>Каркас (фазы, лобби, отсчёт, ростер, снапшоты игроков, откат мира, HUD,
 * статистика) ведёт платформа и зовёт хуки через {@link EscapeGame}; здесь — только
 * игровая логика: контракты, темы, торговцы, события, модификаторы, респавн-блоки,
 * сундуки, зарплата, финальная битва.</p>
 *
 * <p>Объект живёт по одному на матч и хранится в самом матче.</p>
 */
public class EscapeRules
{
    private static final Random RANDOM = new Random();
    private static final long KILL_CREDIT_MILLIS = 10_000L;

    private final EscapePlugin plugin;
    /** Матч платформы: ростер, фазы, откат мира, рассылка — всё через него. */
    private final Match match;
    private final Arena arena;

    /** Escape-специфичные данные игроков матча. */
    private final Map<UUID, EscapePlayerData> matchData = new HashMap<>();

    private final Map<UUID, Double> warmupDamage = new HashMap<>();

    // выбор каста в лобби: id каста | "none" | "random"; нет записи = default арены
    private final Map<UUID, String> chosenKit = new HashMap<>();

    private final ChatChannel lobbyChat = new ChatChannel("chat.lobby-format");
    private final ChatChannel gameChat = new ChatChannel("chat.game-format");
    private final ChatChannel spectatorChat = new ChatChannel("chat.spectator-format");

    // runtime мира
    private final LinkedHashMap<Location, BlockData> editedBlocks = new LinkedHashMap<>();
    private final Set<Location> matchFires = new HashSet<>();   // огонь, зажжённый игроками (гаснет по таймеру)
    private final Set<Location> activeChests = new HashSet<>();
    private final Map<Location, UUID> chestStands = new HashMap<>();
    /** Игровой сундук -> id категорий, СОВМЕЩАЕМЫХ в нём (лут и рефилл — из всех). */
    private final Map<Location, List<String>> activeChestCategories = new HashMap<>();
    /** Категория -> сколько слотов её лута уже разложено по арене (бюджет max-per-arena). */
    private final Map<String, Integer> categoryArenaSlots = new HashMap<>();
    /** Исходное содержимое динамических сундуков (вернуть после матча). */
    private final Map<Location, ItemStack[]> dynamicChestOriginals = new HashMap<>();
    private final List<Location> traderLocations = new ArrayList<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> droppedItems = new HashSet<>();
    private final Map<Location, BukkitTask> refillTasks = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

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
    private final Map<UUID, Long> lastVoteMs = new HashMap<>();
    private Modifier activeModifier = null;

    public EscapeRules(EscapePlugin plugin, Match match)
    {
        this.plugin = plugin;
        this.match = match;
        this.arena = match.arena();
        this.respawnBlocks = new RespawnBlocks(plugin, this);
        this.offlineGuards = new OfflineGuards(plugin, this);
        this.themes = new Themes(plugin, this);
        this.candidateModifier = plugin.modifiers().random(RANDOM);
    }

    // ===== доступ =====

    /** Матч платформы, которому принадлежат эти правила. */
    public Match match() {return match;}

    public Arena getArena() {return arena;}

    /** Фаза берётся у матча: своих фаз у правил больше нет. */
    public GamePhase getPhase() {return match.phase();}

    /** В лобби = матч ещё принимает игроков и игрок в нём. */
    public boolean isLobbyMember(UUID id) {return match.acceptsPlayers() && match.hasPlayer(id);}

    /** Играет = участник матча и ещё не выбыл. */
    public boolean isPlaying(UUID id)
    {
        MatchPlayer mp = match.player(id);
        return mp != null && mp.isAlive();
    }
    /** Спектейт = участник матча, который уже выбыл. */
    public boolean isSpectating(UUID id)
    {
        MatchPlayer mp = match.player(id);
        return mp != null && !mp.isAlive();
    }

    /** Сколько игроков в лобби (до старта участники = лобби). */
    public int lobbySize() {return match.acceptsPlayers() ? match.players().size() : 0;}

    public int aliveCount() {return match.aliveCount();}

    /** UUID участников лобби (до старта участники матча и есть лобби). */
    public Set<UUID> lobbyMembers()
    {
        Set<UUID> out = new LinkedHashSet<>();
        if (!match.acceptsPlayers()) {return out;}
        for (MatchPlayer mp : match.players()) {out.add(mp.getUuid());}
        return out;
    }

    /** UUID живых участников матча. */
    public Set<UUID> playingSet()
    {
        Set<UUID> out = new LinkedHashSet<>();
        for (MatchPlayer mp : match.players()) {if (mp.isAlive()) {out.add(mp.getUuid());}}
        return out;
    }
    public ChatChannel lobbyChat() {return lobbyChat;}
    public ChatChannel gameChat() {return gameChat;}
    public ChatChannel spectatorChat() {return spectatorChat;}
    public EscapePlayerData matchData(UUID id) {return matchData.get(id);}
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

    private long countModifierVotes() {return lobbyMembers().stream().filter(modifierVotes::contains).count();}

    /** Бюллетень в лобби: описание кандидата, текущий счётчик и состояние голоса. */
    private void giveModifierBallot(Player p)
    {
        if (candidateModifier == null) {return;}
        boolean voted = modifierVotes.contains(p.getUniqueId());
        long forVotes = countModifierVotes();
        int need = lobbySize() / 3 + 1;
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
        forEachOnline(lobbyMembers(), this::giveModifierBallot);
    }

    /** Переключить голос игрока «за» кандидата и обновить бюллетени лобби. */
    public void toggleModifierVote(Player p)
    {
        if (candidateModifier == null) {return;}
        UUID id = p.getUniqueId();
        // Один ПКМ иногда даёт парный BLOCK+AIR (или дубль-пакет) на главной руке —
        // фильтр OFF_HAND их не ловит. Дебаунс: один клик = одно переключение.
        long now = System.currentTimeMillis();
        Long last = lastVoteMs.get(id);
        if (last != null && now - last < 200L) {return;}
        lastVoteMs.put(id, now);
        boolean nowFor;
        if (modifierVotes.remove(id)) {nowFor = false;}
        else {modifierVotes.add(id); nowFor = true;}
        long forVotes = countModifierVotes();
        int need = lobbySize() / 3 + 1;
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
        int starting = lobbySize();
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


    /** Добровольный выход или кик. Возвращает false, если игрок не в сессии. */

    /** Выход с сервера во время матча. */

    /** Возврат игрока на сервер, пока сессия им владеет. true — обработано. */

    /** Заочное выбывание (оффлайн-страж не дождался владельца). */
    public void eliminateOffline(UUID id, String name, boolean announce)
    {
        if (!isPlaying(id)) {return;}
        DebugLog.log(Cat.PLAYER, "eliminate-offline arena=%s player=%s announce=%b alive=%d",
            arena.getId(), name, announce, match.aliveCount());
        plugin.stats().add(id, name, "loses", 1);
        respawnBlocks.onOwnerEliminated(id);

        if (announce)
        {
            gameChat.systemKey("offline-guard.eliminated", Msg.ph("player", name));
            spectatorChat.systemKey("offline-guard.eliminated", Msg.ph("player", name));
        }

        // Ядро: alive=false + хук onEliminated (перевод чата) + checkResult. Игрок оффлайн —
        // остаётся в ростере спектатором; cleanup в конце матча вернёт снапшот при след. входе.
        match.eliminate(id);

        if (match.aliveCount() > 1)
        {
            gameChat.systemKey("game.players-left", Msg.ph("n", match.aliveCount()));
            spectatorChat.systemKey("game.players-left", Msg.ph("n", match.aliveCount()));
        }
        // конец матча определяет ядро через checkResult()
    }

    public void addWarmupDamage(Player damager, double dmg)
    {
        warmupDamage.merge(damager.getUniqueId(), dmg, Double::sum);
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


    private void giveKit(Player p)
    {
        // системные предметы — всегда, независимо от каста
        ItemStack fork = Items.special(Material.GOLDEN_PICKAXE,
            Msg.get("game.fork-name"), Msg.getList("game.fork-lore"), "fork");
        ItemMeta meta = fork.getItemMeta();
        int maxDurability = fork.getType().getMaxDurability();
        int uses = Math.max(1, Math.min(EscapeArena.forkUses(arena), maxDurability));
        ((Damageable) meta).setDamage(maxDurability - uses);
        fork.setItemMeta(meta);
        p.getInventory().addItem(fork);
        p.getInventory().addItem(Items.special(Material.COMPASS,
            Msg.get("game.assistant-name"), Msg.getList("game.assistant-lore"), "assistant"));
        p.getInventory().addItem(respawnBlocks.initFor(p));

        // выбранный каст: своё золото (или дефолт арены) + доп. предметы
        Kit kit = resolveKit(p.getUniqueId());
        int gold = (kit != null && kit.getGold() >= 0) ? kit.getGold() : EscapeArena.startGold(arena);
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
        List<Kit> kits = plugin.kitsFor(arena);
        if (kits.isEmpty()) {return null;}
        String choice = chosenKit.get(id);
        if (choice == null) {choice = plugin.arenaConfigs().of(arena).defaultKit();}
        if (choice == null || choice.equalsIgnoreCase("none")) {return null;}
        if (choice.equalsIgnoreCase("random")) {return kits.get(RANDOM.nextInt(kits.size()));}
        return plugin.kitFor(arena, choice);
    }

    /**
     * Трёхфазное распределение сундуков по категориям. Точка сундука несёт список
     * id категорий; используемые = существующие в plugin.loot(). Каждая точка
     * получает НЕ БОЛЕЕ одной категории (и только из своего списка), пустой список
     * или неудача распределения = сундука нет (точка остаётся воздухом).
     */
    private void placeChests()
    {
        activeChestCategories.clear();
        categoryArenaSlots.clear();

        LootCategoryRegistry registry = plugin.loot();
        double mult = modMult("chest-count-mult");

        // используемые категории каждой точки (фильтр по существованию, без дублей)
        Map<Location, List<String>> usable = new LinkedHashMap<>();
        for (Map.Entry<Location, List<String>> entry : EscapeArena.chestSpots(arena).entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {continue;}
            List<String> ids = new ArrayList<>();
            for (String id : entry.getValue())
            {
                if (registry.exists(id) && !ids.contains(id)) {ids.add(id);}
            }
            if (!ids.isEmpty()) {usable.put(loc, ids);}
        }

        // все категории, встречающиеся у точек, + их масштабированные лимиты сундуков
        Set<String> catIds = new LinkedHashSet<>();
        for (List<String> ids : usable.values()) {catIds.addAll(ids);}
        List<LootCategory> cats = new ArrayList<>();
        Map<String, Integer> effMin = new HashMap<>();          // отсутствует = 0
        Map<String, Integer> effMax = new HashMap<>();          // отсутствует = UNLIMITED
        for (String id : catIds)
        {
            LootCategory c = registry.get(id);
            if (c == null) {continue;}
            cats.add(c);
            if (c.getMinChests() != LootCategory.UNLIMITED)
            {
                effMin.put(id, Math.max(0, (int) Math.round(c.getMinChests() * mult)));
            }
            if (c.getMaxChests() != LootCategory.UNLIMITED)
            {
                effMax.put(id, Math.max(0, (int) Math.round(c.getMaxChests() * mult)));
            }
        }

        // СОВМЕЩЕНИЕ: каждая категория НЕЗАВИСИМО выбирает свои сундуки в пределах
        // [min-chests, max-chests] из своих точек. Точка, выбранная несколькими
        // категориями, наполняется лутом из ВСЕХ них (а не одной на выбор) — редкая
        // категория добавляет свой предмет ПОВЕРХ базовой, а не вместо.
        Map<Location, List<String>> active = new LinkedHashMap<>();
        Map<String, Integer> placedPerCat = new LinkedHashMap<>();
        List<LootCategory> byWeightDesc = new ArrayList<>(cats);
        byWeightDesc.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));
        for (LootCategory c : byWeightDesc)
        {
            List<Location> candidates = new ArrayList<>();
            for (Location loc : usable.keySet())
            {
                if (usable.get(loc).contains(c.getId())) {candidates.add(loc);}
            }
            Collections.shuffle(candidates, RANDOM);
            int lo = effMin.getOrDefault(c.getId(), 0);
            Integer maxCfg = effMax.get(c.getId());
            int target;
            if (maxCfg == null) {target = candidates.size();}   // max=UNLIMITED -> во всех своих точках
            else
            {
                int hi = Math.min(maxCfg, candidates.size());
                lo = Math.min(lo, hi);
                target = hi <= lo ? lo : lo + RANDOM.nextInt(hi - lo + 1);
            }
            target = Math.min(target, candidates.size());
            for (int i = 0; i < target; i++)
            {
                active.computeIfAbsent(candidates.get(i), k -> new ArrayList<>()).add(c.getId());
            }
            placedPerCat.put(c.getId(), target);
            if (target < effMin.getOrDefault(c.getId(), 0))
            {
                DebugLog.log(Cat.WORLD, "chests-min-short arena=%s cat=%s wanted=%d got=%d",
                    arena.getId(), c.getId(), effMin.getOrDefault(c.getId(), 0), target);
            }
        }

        // Материализация: КАЖДАЯ точка сундука сначала в воздух (cleanup вернёт),
        // затем активные точки становятся сундуками и наполняются из своих категорий
        for (Location loc : EscapeArena.chestSpots(arena).keySet())
        {
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(Material.AIR);
        }

        for (Map.Entry<Location, List<String>> entry : active.entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            block.setType(Material.CHEST);
            // сундук должен смотреть, как задумано (setType всегда даёт NORTH)
            if (block.getBlockData() instanceof Directional dir)
            {
                BlockFace facing = EscapeArena.chestFacing(arena, loc);
                if (dir.getFaces().contains(facing)) {dir.setFacing(facing); block.setBlockData(dir, false);}
            }
            Location placed = block.getLocation();
            List<String> ids = new ArrayList<>(entry.getValue());
            activeChestCategories.put(placed, ids);
            if (block.getState() instanceof Chest chest) {fillChestInitial(chest, categoriesFor(ids));}
            activeChests.add(placed);
            spawnChestStand(placed);
        }

        StringBuilder perCat = new StringBuilder();
        for (Map.Entry<String, Integer> e : placedPerCat.entrySet())
        {
            if (perCat.length() > 0) {perCat.append(',');}
            perCat.append(e.getKey()).append('=').append(e.getValue());
        }
        DebugLog.log(Cat.WORLD, "chests-placed arena=%s total=%d spots=%d per-cat=[%s]",
            arena.getId(), activeChests.size(), EscapeArena.chestSpots(arena).size(), perCat.toString());
    }

    /** Взвешенно-случайная категория из списка (по weight). null — список пуст. */
    private LootCategory weightedPickCategory(List<LootCategory> options)
    {
        if (options.isEmpty()) {return null;}
        int total = 0;
        for (LootCategory c : options) {total += c.getWeight();}
        int roll = RANDOM.nextInt(total);
        for (LootCategory c : options)
        {
            roll -= c.getWeight();
            if (roll < 0) {return c;}
        }
        return options.get(options.size() - 1);
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
        if (match.phase() != GamePhase.RUNNING || !EscapeArena.dynamicChests(arena)) {return false;}
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

        // категория динамического сундука — самая весомая с лутом (для рефилла тоже)
        LootCategory cat = plugin.loot().highestWeightWithLoot();
        chest.getInventory().clear();
        if (cat != null)
        {
            activeChestCategories.put(loc, new ArrayList<>(List.of(cat.getId())));
            fillChestRefill(chest, List.of(cat));
        }
        activeChests.add(loc);
        spawnChestStand(loc);
        DebugLog.log(Cat.CHEST, "dynamic-register arena=%s at=%s cat=%s saved-slots=%d active=%d",
            arena.getId(), DebugLog.at(loc), cat == null ? "-" : cat.getId(), originals.length, activeChests.size());
        return true;
    }

    /**
     * Стартовое наполнение сундука по его категории с бюджетом на арену.
     * Очищает сундук, катает {@code rollSlotCount} слотов. max-per-arena МЯГКИЙ:
     * per-chest минимум ({@code effMinPerChest}) кладётся всегда, а сверх него
     * добор прекращается, как только бюджет категории на арену исчерпан.
     * Категория без лута -> сундук остаётся пустым. Контракты сюда НЕ кладутся.
     */
    /** id категорий сундука -> объекты категорий, по весу DESC (весомая наполняет первой). */
    private List<LootCategory> categoriesFor(List<String> ids)
    {
        List<LootCategory> out = new ArrayList<>();
        if (ids == null) {return out;}
        for (String id : ids)
        {
            LootCategory c = plugin.loot().get(id);
            if (c != null) {out.add(c);}
        }
        out.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));
        return out;
    }

    private void fillChestInitial(Chest chest, List<LootCategory> cats)
    {
        chest.getInventory().clear();
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 27; i++) {free.add(i);}
        Collections.shuffle(free, RANDOM);

        for (LootCategory cat : cats)
        {
            if (!cat.hasLoot()) {continue;}
            int slots = cat.rollSlotCount(RANDOM);
            if (slots <= 0) {continue;}
            int minPerChest = cat.effMinPerChest();
            int maxPerArena = cat.getMaxPerArena();
            int placed = 0;
            while (placed < slots && !free.isEmpty())
            {
                int used = categoryArenaSlots.getOrDefault(cat.getId(), 0);
                if (placed >= minPerChest && maxPerArena != LootCategory.UNLIMITED && used >= maxPerArena) {break;}
                ItemStack item = cat.pickItem(RANDOM);
                if (item == null) {break;}
                chest.getInventory().setItem(free.remove(free.size() - 1), applyWear(item));
                categoryArenaSlots.merge(cat.getId(), 1, Integer::sum);
                placed++;
            }
        }
    }

    /**
     * Аддитивная догрузка сундука из ВСЕХ его категорий: только в свободные слоты,
     * БЕЗ очистки и БЕЗ учёта бюджета арены (рефилл, «ключик», динамические сундуки).
     */
    private void fillChestRefill(Chest chest, List<LootCategory> cats)
    {
        for (LootCategory cat : cats)
        {
            if (!cat.hasLoot()) {continue;}
            int slots = cat.rollSlotCount(RANDOM);
            if (slots <= 0) {continue;}
            ItemStack[] contents = chest.getInventory().getContents();
            List<Integer> freeSlots = new ArrayList<>();
            for (int i = 0; i < 27 && i < contents.length; i++)
            {
                if (contents[i] == null || contents[i].getType().isAir()) {freeSlots.add(i);}
            }
            if (freeSlots.isEmpty()) {break;}
            Collections.shuffle(freeSlots, RANDOM);
            int count = Math.min(slots, freeSlots.size());
            for (int i = 0; i < count; i++)
            {
                ItemStack item = cat.pickItem(RANDOM);
                if (item == null) {continue;}
                chest.getInventory().setItem(freeSlots.get(i), applyWear(item));
            }
        }
    }

    /** Валидные контракты арены (тип задан, amount &gt; 0). */
    private List<Contract> availableContracts()
    {
        List<Contract> out = new ArrayList<>();
        for (String cid : plugin.arenaConfigs().of(arena).contractIds())
        {
            Contract c = plugin.contracts().get(cid);
            if (c != null && c.isComplete()) {out.add(c);}
        }
        return out;
    }

    /**
     * Контракты — отдельно от лута, один раз после placeChests(). Катаем случайное
     * ЧИСЛО на матч (total в [min-per-arena, max-per-arena]; max=-1 => заполняем до
     * предела chests*max-per-chest) и раскидываем по СЛУЧАЙНЫМ сундукам в СЛУЧАЙНЫЕ
     * слоты. Сундук, набравший max-per-chest (или без свободных слотов), выбывает из
     * выборки. В categoryArenaSlots контракты не учитываются.
     */
    private void placeContracts()
    {
        List<Contract> available = availableContracts();
        if (available.isEmpty() || activeChests.isEmpty()) {return;}

        int minArena = Math.max(0, EscapeArena.contractsMinPerArena(arena));
        int maxArena = EscapeArena.contractsMaxPerArena(arena);          // -1 = без потолка
        int maxPerChest = Math.max(1, EscapeArena.contractsMaxPerChest(arena));

        int total = maxArena < 0 ? activeChests.size() * maxPerChest
            : (maxArena <= minArena ? minArena : minArena + RANDOM.nextInt(maxArena - minArena + 1));
        if (total <= 0) {return;}

        List<Location> pool = new ArrayList<>(activeChests);   // сундуки-кандидаты
        Map<Location, Integer> perChest = new HashMap<>();
        int placed = 0;
        while (placed < total && !pool.isEmpty())
        {
            int idx = RANDOM.nextInt(pool.size());
            Location loc = pool.get(idx);
            if (!placeOneContract(loc, available))
            {
                pool.remove(idx);                              // нет свободных слотов -> из выборки
                continue;
            }
            placed++;
            int n = perChest.merge(loc, 1, Integer::sum);
            if (n >= maxPerChest) {pool.remove(idx);}          // набрал max-per-chest -> из выборки
        }
        DebugLog.log(Cat.CHEST, "contracts-placed arena=%s placed=%d total=%d chests=%d in-chests=%d avail=%d",
            arena.getId(), placed, total, activeChests.size(), perChest.size(), available.size());
    }

    /** Рефилл: с шансом 1/4 добросить один контракт в этот сундук (в случайный свободный слот). */
    private void maybeRefillContract(Chest chest)
    {
        if (RANDOM.nextInt(4) != 0) {return;}
        List<Contract> available = availableContracts();
        if (!available.isEmpty()) {placeContractInChest(chest, available);}
    }

    /** Положить один контракт в СЛУЧАЙНЫЙ свободный слот сундука. false — свободных слотов нет. */
    private boolean placeOneContract(Location loc, List<Contract> available)
    {
        if (loc.getWorld() == null) {return false;}
        if (!(loc.getBlock().getState() instanceof Chest chest)) {return false;}
        return placeContractInChest(chest, available);
    }

    private boolean placeContractInChest(Chest chest, List<Contract> available)
    {
        ItemStack[] contents = chest.getInventory().getContents();
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < contents.length; i++)
        {
            if (contents[i] == null || contents[i].getType().isAir()) {free.add(i);}
        }
        if (free.isEmpty()) {return false;}
        int slot = free.get(RANDOM.nextInt(free.size()));
        Contract c = available.get(RANDOM.nextInt(available.size()));
        chest.getInventory().setItem(slot, ContractPapers.create(c));
        return true;
    }

    /**
     * Случайный износ (wear-min/max-percent арены): каждый предмет с прочностью
     * ломан по-своему — и в луте, и в покупках у торговцев. Предметы с уже
     * заданным уроном (выставлен руками через additem/addtrade) не трогаются.
     */
    public ItemStack applyWear(ItemStack item)
    {
        int maxPct = EscapeArena.wearMaxPercent(arena);
        if (maxPct <= 0) {return item;}
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0 || !(item.getItemMeta() instanceof Damageable meta)) {return item;}
        if (meta.hasDamage()) {return item;}

        int wearAdd = (int) Math.round(modAdd("wear-add"));
        int minPct = Math.max(0, Math.min(EscapeArena.wearMinPercent(arena) + wearAdd, 99));
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
        if (EscapeArena.traderQuotas(arena).isEmpty()) {spawnTradersGlobal(); return;}

        // лимиты по типам: точки группируем по типу, из каждой группы случайно
        // min(quota, точек); тип без записи в trader-quotas выставляется целиком
        Map<String, List<Location>> byType = new LinkedHashMap<>();
        for (Map.Entry<Location, String> entry : EscapeArena.traderSpots(arena).entrySet())
        {
            byType.computeIfAbsent(entry.getValue().toUpperCase(Locale.ROOT), k -> new ArrayList<>())
                .add(entry.getKey());
        }
        int total = 0;
        for (Map.Entry<String, List<Location>> entry : byType.entrySet())
        {
            String typeId = entry.getKey();
            List<Location> points = entry.getValue();
            Collections.shuffle(points, RANDOM);
            Integer quota = EscapeArena.traderQuotas(arena).get(typeId);
            int want = quota != null ? Math.max(0, quota) : points.size();
            int count = Math.min(want, points.size());
            for (int i = 0; i < count; i++)
            {
                if (spawnOneTrader(points.get(i), typeId)) {total++;}
            }
            DebugLog.log(Cat.WORLD, "traders-type arena=%s type=%s placed=%d quota=%s points=%d",
                arena.getId(), typeId, count, quota == null ? "all" : quota.toString(), points.size());
        }
        DebugLog.log(Cat.WORLD, "traders-placed arena=%s mode=quota total=%d spots=%d",
            arena.getId(), total, EscapeArena.traderSpots(arena).size());
    }

    /** Без лимитов по типам (trader-quotas пуст): случайное подмножество размера trader-count. */
    private void spawnTradersGlobal()
    {
        List<Map.Entry<Location, String>> pool = new ArrayList<>(EscapeArena.traderSpots(arena).entrySet());
        Collections.shuffle(pool, RANDOM);
        int count = Math.min(EscapeArena.traderCount(arena), pool.size());
        int placed = 0;
        for (int i = 0; i < count; i++)
        {
            if (spawnOneTrader(pool.get(i).getKey(), pool.get(i).getValue())) {placed++;}
        }
        DebugLog.log(Cat.WORLD, "traders-placed arena=%s mode=global placed=%d wanted=%d spots=%d",
            arena.getId(), placed, EscapeArena.traderCount(arena), pool.size());
    }

    /** Заспавнить одного жителя заданного типа в точке. false — тип/мир недоступны. */
    private boolean spawnOneTrader(Location loc, String typeId)
    {
        var type = plugin.traders().get(typeId);
        if (type == null || loc.getWorld() == null) {return false;}
        Villager villager = loc.getWorld().spawn(loc.clone().add(0.5, 0, 0.5), Villager.class, v ->
        {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setCanPickupItems(false);
            v.customName(type.displayName());
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(EscapeKeys.TRADER_TYPE,
                PersistentDataType.STRING, type.getId());
        });
        spawnedEntities.add(villager.getUniqueId());
        traderLocations.add(villager.getLocation());
        DebugLog.log(Cat.WORLD, "trader-spawn arena=%s type=%s at=%s", arena.getId(), typeId, DebugLog.at(loc));
        return true;
    }

    private void placeOres()
    {
        for (Location loc : EscapeArena.oreSpots(arena))
        {
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(randomOre());
        }
        DebugLog.log(Cat.WORLD, "ores-placed arena=%s count=%d", arena.getId(), EscapeArena.oreSpots(arena).size());
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
        List<Location> pool = new ArrayList<>(EscapeArena.tableSpots(arena));
        Collections.shuffle(pool, RANDOM);
        int count = Math.min(EscapeArena.tableCount(arena), pool.size());
        for (int i = 0; i < count; i++)
        {
            Location loc = pool.get(i);
            if (loc.getWorld() == null) {continue;}
            Block block = loc.getBlock();
            editedBlocks.putIfAbsent(loc, block.getBlockData().clone());
            block.setType(Material.ENCHANTING_TABLE);
        }
        DebugLog.log(Cat.WORLD, "tables-placed arena=%s placed=%d wanted=%d spots=%d",
            arena.getId(), count, EscapeArena.tableCount(arena), pool.size());
    }

    // ===== игровой цикл =====


    // ===== боссбар-таймер =====


    /** Показ идемпотентен — один и тот же инстанс бара не дублируется. */



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
            arena.getId(), event.name(), event.windowSeconds(), candidates.size(), match.aliveCount());

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
            arena.getId(), event.name(), eventFlagged.size(), match.aliveCount());
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
        forEachOnline(playingSet(), p -> eventPositions.put(p.getUniqueId(), p.getLocation().clone()));
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
        for (UUID id : playingSet())
        {
            if (respawnBlocks.isAwaitingRespawn(id)) {continue;}
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {action.accept(p);}
        }
    }

    /** BRIBE: случайный предмет из взвешенно выбранной категории лута. false — лута нет. */
    public boolean giveRandomLoot(Player p)
    {
        List<LootCategory> options = new ArrayList<>();
        for (LootCategory c : plugin.loot().all())
        {
            if (c.hasLoot()) {options.add(c);}
        }
        LootCategory cat = weightedPickCategory(options);
        if (cat == null) {return false;}
        ItemStack item = cat.pickItem(RANDOM);
        if (item == null) {return false;}
        item = applyWear(item);
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
        finalBattleStarted = true;
        DebugLog.log(Cat.SESSION, "final-battle arena=%s alive=%d respawn-blocks=%b",
            arena.getId(), match.aliveCount(), respawnBlocks.hasPlacedBlocks());
        offlineGuards.onFinalBattle();
        if (respawnBlocks.hasPlacedBlocks())
        {
            gameChat.systemKey("respawn-block.annul-broadcast");
            respawnBlocks.annulAll();
        }
        gameChat.systemKey("game.final-battle");
        List<Location> pool = EscapeArena.finalSpawns(arena).isEmpty() ? arena.getSpawns() : EscapeArena.finalSpawns(arena);
        List<Location> bag = new ArrayList<>();
        for (UUID id : playingSet())
        {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {continue;}
            Location loc = nextFromBag(bag, pool);
            p.teleport(loc.clone().add(0.5, 0, 0.5));
        }
    }

    /**
     * Взять следующую РАЗНУЮ точку из мешка: пока мешок не пуст — отдаём из него,
     * иначе пересыпаем весь пул заново вперемешку. Так двое игроков не встают на
     * одну точку, пока точек хватает, а при нехватке повторы тоже случайны.
     */
    private Location nextFromBag(List<Location> bag, List<Location> pool)
    {
        if (bag.isEmpty())
        {
            bag.addAll(pool);
            Collections.shuffle(bag, RANDOM);
        }
        return bag.remove(bag.size() - 1);
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
        EscapePlayerData data = matchData.get(p.getUniqueId());
        if (data != null) {data.quests++;}
        plugin.stats().add(p.getUniqueId(), p.getName(), "quests_completed", 1);
    }

    /** LOOT: первый раз открыт сундук. */
    public void handleChestLooted(Player p, Location chestLoc)
    {
        EscapePlayerData data = matchData.get(p.getUniqueId());
        if (data == null) {return;}
        if (!data.lootedChests.add(chestLoc)) {return;}
        DebugLog.log(Cat.CHEST, "loot-first-open arena=%s player=%s at=%s total-looted=%d",
            arena.getId(), p.getName(), DebugLog.at(chestLoc), data.lootedChests.size());
        progressContracts(p, ContractType.LOOT, c -> true, 1);
        themes.progress(p, ThemeType.LOOT, t -> true, 1);
    }

    // ===== рефилл сундуков =====

    /**
     * По закрытию сундука: если из него что-то взяли (есть свободный слот), у его
     * категории задан рефилл (refill-seconds &gt; 0) и нет запланированной задачи —
     * планируем аддитивную догрузку через max(5, refill-seconds * refill-mult).
     */
    public void scheduleRefill(Block block)
    {
        Location loc = block.getLocation();
        if (!activeChests.contains(loc) || refillTasks.containsKey(loc)) {return;}
        if (!(block.getState() instanceof Chest chest)) {return;}
        // ничего не взяли (сундук всё ещё полон) — рефилл не нужен
        if (chest.getInventory().firstEmpty() < 0) {return;}

        List<LootCategory> cats = categoriesFor(activeChestCategories.get(loc));
        int refillSec = 0;
        for (LootCategory c : cats)
        {
            if (c.getRefillSeconds() > 0)
            {
                refillSec = refillSec == 0 ? c.getRefillSeconds() : Math.min(refillSec, c.getRefillSeconds());
            }
        }
        if (refillSec <= 0) {return;}

        if (chest.getInventory().isEmpty())
        {
            ArmorStand stand = standAt(loc);
            if (stand != null)
            {
                stand.customName(Items.flat(Msg.get("chest.empty-name")));
                stand.setCustomNameVisible(true);
            }
        }

        int delay = Math.max(5, (int) Math.round(refillSec * modMult("refill-mult")));
        DebugLog.log(Cat.CHEST, "refill-scheduled arena=%s at=%s cats=%d in=%ds",
            arena.getId(), DebugLog.at(loc), cats.size(), delay);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            refillTasks.remove(loc);
            if (match.phase() != GamePhase.RUNNING) {return;}
            if (loc.getBlock().getState() instanceof Chest target)
            {
                fillChestRefill(target, categoriesFor(activeChestCategories.get(loc)));
                maybeRefillContract(target);
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
        if (match.phase() != GamePhase.RUNNING || !activeChests.contains(loc)) {return false;}
        if (!(block.getState() instanceof Chest chest)) {return false;}

        BukkitTask pending = refillTasks.remove(loc);
        if (pending != null) {pending.cancel();}
        List<LootCategory> cats = categoriesFor(activeChestCategories.get(loc));
        fillChestRefill(chest, cats);
        maybeRefillContract(chest);
        DebugLog.log(Cat.CHEST, "refill-forced arena=%s at=%s cats=%d had-pending=%b",
            arena.getId(), DebugLog.at(loc), cats.size(), pending != null);

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
        EscapePlayerData data = matchData.get(victim.getUniqueId());
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
     * Запомнить ломаемый блок и, если он часть многоблочной структуры
     * (дверь/кровать/высокое растение), её вторую половину: ваниль при сломе
     * убирает обе половины сама, поэтому обе надо запомнить ДО слома, иначе
     * оставшаяся половина не вернётся после матча.
     */
    public void rememberStructure(Block block)
    {
        rememberEditedBlock(block);
        Block partner = SetupMarkers.structurePartner(block);
        if (partner != null) {rememberEditedBlock(partner);}
    }

    // ===== огонь =====

    /**
     * Игрок поджёг огонь в матче: помним место (вернём после матча), держим огонь
     * живым до таймера (BlockFadeEvent отменяется в ProtectionListener) и гасим
     * через случайное fire.min..max секунд. false — поджиг выключен (fire.enabled)
     * или матч не идёт: вызывающий отменяет событие поджига.
     */
    public boolean registerMatchFire(Location loc)
    {
        if (match.phase() != GamePhase.RUNNING) {return false;}
        if (!plugin.getConfig().getBoolean("fire.enabled", true)) {return false;}
        Block block = loc.getBlock();
        rememberEditedBlock(block); // сейчас тут воздух/заменяемый — cleanup вернёт его
        Location fireLoc = block.getLocation();
        matchFires.add(fireLoc);

        int min = Math.max(1, plugin.getConfig().getInt("fire.min-seconds", 12));
        int max = Math.max(min, plugin.getConfig().getInt("fire.max-seconds", 30));
        int seconds = min + RANDOM.nextInt(max - min + 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> extinguishMatchFire(fireLoc), seconds * 20L);
        DebugLog.log(Cat.WORLD, "fire-lit arena=%s at=%s seconds=%d active=%d",
            arena.getId(), DebugLog.at(fireLoc), seconds, matchFires.size());
        return true;
    }

    /** Это игрок-огонь (можно тушить), а не изначальный огонь карты? */
    public boolean isMatchFire(Location loc) {return matchFires.contains(loc);}

    /** Игрок потушил огонь рукой: снять с учёта (сам блок гасит ваниль слома). */
    public void douseMatchFire(Location loc)
    {
        if (matchFires.remove(loc))
        {
            DebugLog.log(Cat.WORLD, "fire-doused arena=%s at=%s active=%d",
                arena.getId(), DebugLog.at(loc), matchFires.size());
        }
    }

    /** Погасить игрок-огонь по таймеру. */
    private void extinguishMatchFire(Location loc)
    {
        if (match.phase() != GamePhase.RUNNING) {return;}
        if (!matchFires.remove(loc)) {return;}
        if (loc.getWorld() == null) {return;}
        Block block = loc.getBlock();
        if (block.getType() == Material.FIRE) {block.setType(Material.AIR);}
        DebugLog.log(Cat.WORLD, "fire-out arena=%s at=%s active=%d",
            arena.getId(), DebugLog.at(loc), matchFires.size());
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
        if (!isPlaying(id)) {return;}

        // Смерть от урона: выронить лут и восстановить HP. Раньше это делал слушатель урона
        // Escape, но ядро гасит летальный урон и зовёт нас через хук onLethalDamage — поэтому
        // сброс/лечение переехали сюда (иначе лут не падал бы, а HP оставался низким).
        dropInventory(p, p.getLocation());
        p.setHealth(20.0);

        DebugLog.log(Cat.PLAYER, "death arena=%s player=%s at=%s alive=%d", arena.getId(), p.getName(),
            DebugLog.at(p.getLocation()), match.aliveCount());
        creditKillAndAnnounce(p);
        plugin.stats().add(id, p.getName(), "deaths", 1);

        EscapePlayerData data = matchData.get(id);
        if (data != null) {data.lastDamager = null;}

        if (respawnBlocks.tryScheduleRespawn(p)) {return;}
        finishElimination(p, false);
    }

    /** Окончательное выбывание без возрождения (leave / quit / кик). */
    public void eliminate(Player p, boolean quit)
    {
        UUID id = p.getUniqueId();
        if (!isPlaying(id)) {return;}
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
        if (!isPlaying(p.getUniqueId())) {return;}
        finishElimination(p, false);
    }

    /** Кредит убийце (счёт, контракты, прогресс изумрудного блока) + сообщение о смерти. */
    private void creditKillAndAnnounce(Player p)
    {
        EscapePlayerData data = matchData.get(p.getUniqueId());
        if (data != null && data.lastDamager != null
            && System.currentTimeMillis() - data.lastDamagerAt <= KILL_CREDIT_MILLIS
            && isPlaying(data.lastDamager))
        {
            Player killer = Bukkit.getPlayer(data.lastDamager);
            if (killer != null)
            {
                EscapePlayerData killerData = matchData.get(killer.getUniqueId());
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
        DebugLog.log(Cat.PLAYER, "eliminate arena=%s player=%s quit=%b alive=%d",
            arena.getId(), p.getName(), quit, match.aliveCount());
        plugin.stats().add(id, p.getName(), "loses", 1);
        respawnBlocks.onOwnerEliminated(id);
        // Ядро: alive=false + спектейт + хук onEliminated (перевод чата) + checkResult (конец матча).
        match.eliminate(id);

        if (match.aliveCount() > 1)
        {
            gameChat.systemKey("game.players-left", Msg.ph("n", match.aliveCount()));
            spectatorChat.systemKey("game.players-left", Msg.ph("n", match.aliveCount()));
        }
        // конец матча определяет ядро через checkResult()
    }

    private String randomDeadMessage()
    {
        List<String> pool = plugin.arenaConfigs().of(arena).deadMessages();
        if (pool.isEmpty()) {pool = Msg.rawList("death-messages");}
        if (pool.isEmpty()) {return "<yellow>Игрок <aqua><player><yellow> выбыл";}
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** Победа/конец матча (осталось <= 1 живых). */


    /** Немедленная остановка (админ, onDisable, удаление арены). */

    // ===== очистка =====



    private static final BlockFace[] HORIZONTAL =
        {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /**
     * Починить стык у уцелевших соседних решёток (панелей/заборов), примыкающих к
     * только что восстановленным. Когда игрок выбил прут, ваниль разорвала стык
     * соседнего уцелевшего прута; сам restore возвращает лишь выбитые блоки, а у
     * соседа так и остаётся «обрубок». Повторная установка того же BlockData —
     * no-op (движок пропускает неизменное состояние, соседу физика не приходит),
     * поэтому чиним точечно: если восстановленный прут тянется в сторону соседа,
     * а сосед — тоже решётка и в эту сторону не соединён, зеркалим соединение.
     * Соседа, который сам восстанавливался (есть в editedBlocks), не трогаем — у
     * него уже верная форма. Возвращает число поправленных соседей.
     */
    private int fixConnectingNeighbors(List<Block> restored)
    {
        int fixed = 0;
        for (Block block : restored)
        {
            if (!(block.getBlockData() instanceof MultipleFacing aFace)) {continue;}
            for (BlockFace side : HORIZONTAL)
            {
                if (!aFace.getAllowedFaces().contains(side) || !aFace.hasFace(side)) {continue;}
                Block neighbor = block.getRelative(side);
                if (editedBlocks.containsKey(neighbor.getLocation())) {continue;}
                if (!(neighbor.getBlockData() instanceof MultipleFacing bFace)) {continue;}
                BlockFace toward = side.getOppositeFace(); // от соседа к восстановленному пруту
                if (!bFace.getAllowedFaces().contains(toward) || bFace.hasFace(toward)) {continue;}
                bFace.setFace(toward, true);
                neighbor.setBlockData(bFace, false); // точечная правка формы, физика не нужна
                fixed++;
            }
        }
        return fixed;
    }

    // ===== отладка (escape.admin.debug) =====

    /** Принудительный запуск конкретного события (обходит canStart и таймер). */
    public boolean debugStartEvent(GameEvent event)
    {
        if (match.phase() != GamePhase.RUNNING) {return false;}
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
        if (match.phase() != GamePhase.RUNNING || glowActive) {return false;}
        glowActive = true;
        gameChat.systemKey("game.glow-warning");
        forEachPlaying(p ->
        {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
            giveGold(p, EscapeArena.glowBonusGold(arena));
        });
        return true;
    }

    public boolean debugFinalBattle()
    {
        if (match.phase() != GamePhase.RUNNING || finalBattleStarted) {return false;}
        finalBattle();
        return true;
    }

    /** Досрочно завершить матч: выбиваем всех живых, дальше решает ядро (checkResult). */
    public boolean debugFinish()
    {
        if (match.phase() != GamePhase.RUNNING) {return false;}
        for (Player p : new ArrayList<>(match.alivePlayers())) {match.eliminate(p, false);}
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

    /** Остановка админом с отсчётом. */


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

    // ===================== хуки платформы (их зовёт EscapeGame) =====================

    /** Игрок вошёл в лобби: канал чата и бюллетень голосования за модификатор. */
    public void onLobbyJoin(Player p)
    {
        lobbyChat.add(p.getUniqueId());
        giveModifierBallot(p);
    }

    /** Старт матча: модификатор решается ДО генерации (он крутит числа), затем мир арены. */
    public void onStart()
    {
        World world = arena.getWorld();
        if (world == null)
        {
            DebugLog.log(Cat.SESSION, "start-abort arena=%s reason=world-not-loaded world=%s",
                arena.getId(), arena.getWorldName());
            return;
        }
        decideModifier(world);
        // подсказки настройки убираем ДО генерации: их лут в игре не участвует
        SetupMarkers.clearForMatch(arena);

        placeChests();
        placeContracts();
        spawnTraders();
        placeOres();
        placeTables();

        // «Молния прозрения»: 1 на матч + 1 за каждую пару игроков
        respawnBlocks.distributeInsight(activeChests, match.aliveCount());
        warmupDamage.clear();

        DebugLog.log(Cat.SESSION, "start-done arena=%s playing=%d chests=%d traders=%d contracts=%d",
            arena.getId(), match.aliveCount(), activeChests.size(), traderLocations.size(),
            plugin.arenaConfigs().of(arena).contractIds().size());
    }

    /** Стартовый набор игрока: данные матча, чат, статистика, XP-валюта, эффекты, каст. */
    public void giveLoadout(Player p)
    {
        UUID id = p.getUniqueId();
        lobbyChat.remove(id);
        matchData.put(id, new EscapePlayerData(id, p.getName()));
        gameChat.add(id);
        plugin.stats().add(id, p.getName(), "games_played", 1);

        // XP — валюта зачарования: стартовый запас, пополняется убийствами
        p.setLevel(plugin.getConfig().getInt("match.start-xp-levels", 50));
        p.setExp(0f);
        p.addPotionEffects(List.of(
            new PotionEffect(PotionEffectType.RESISTANCE, 20 * 20, 1, false, false),
            new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, false),
            new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 0, false, false)));

        giveKit(p);
        Msg.send(p, "game.start-effects-hint");
    }

    /** Секунда матча: события, зарплата, молнии, глоу, объявления, финальная битва. */
    public void onTick()
    {
        int remaining = match.remainingSeconds();
        if (remaining < 0) {return;}                       // матч без лимита времени
        if (remaining == 0)
        {
            if (!finalBattleStarted) {finalBattle();}      // дальше играем без таймера
            return;
        }

        int interval = Math.max(30, (int) Math.round(EscapeArena.eventIntervalSeconds(arena) * modMult("event-interval-mult")));
        if (currentEvent == null && remaining % interval == 0 && remaining != arena.getMatchDurationSeconds())
        {
            startRandomEvent();
        }
        else if (currentEvent != null)
        {
            currentEvent.onTick(this);
            eventTicksLeft--;
            if (eventTicksLeft <= 0) {endCurrentEvent();}
        }

        int salaryInterval = Math.max(60, EscapeArena.salaryIntervalSeconds(arena));
        if (remaining % salaryInterval == 0)
        {
            int salary = Math.max(0, (int) Math.round(EscapeArena.salaryGold(arena) * modMult("salary-mult")));
            DebugLog.log(Cat.SESSION, "salary arena=%s gold=%d alive=%d remaining=%ds",
                arena.getId(), salary, match.aliveCount(), remaining);
            gameChat.systemKey("game.salary", Msg.ph("n", salary));
            forEachPlaying(p ->
            {
                p.getWorld().strikeLightningEffect(p.getLocation());
                giveGold(p, salary);
            });
        }

        int strikeInterval = Math.max(60, plugin.getConfig().getInt("respawn-block.lightning-interval-seconds", 300));
        if (remaining % strikeInterval == 0 && remaining != arena.getMatchDurationSeconds())
        {
            respawnBlocks.strikeAll();
        }

        if (remaining == EscapeArena.glowSecondsBeforeEnd(arena))
        {
            glowActive = true;
            DebugLog.log(Cat.SESSION, "glow-start arena=%s bonus=%d alive=%d",
                arena.getId(), EscapeArena.glowBonusGold(arena), match.aliveCount());
            gameChat.systemKey("game.glow-warning");
            forEachPlaying(p ->
            {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
                giveGold(p, EscapeArena.glowBonusGold(arena));
            });
        }

        if (remaining % 600 == 0) {gameChat.systemKey("game.time-left-minutes", Msg.ph("n", remaining / 60));}
        if (remaining < 15) {gameChat.systemKey("game.time-left-seconds", Msg.ph("n", remaining));}
    }

    /**
     * Смертельный урон: Escape решает сам (блок возрождения может вернуть игрока),
     * поэтому ядру всегда отвечаем «обработано».
     */
    public boolean onLethalDamage(Player p)
    {
        handleDeath(p);
        return false;
    }

    /** Игрок выбыл: перевести в канал спектейта. */
    public void onEliminated(MatchPlayer mp)
    {
        gameChat.remove(mp.getUuid());
        spectatorChat.add(mp.getUuid());
    }

    /** Игрок покинул матч: убрать из всех каналов чата. */
    public void onPlayerRemoved(UUID id)
    {
        lobbyChat.remove(id);
        gameChat.remove(id);
        spectatorChat.remove(id);
    }

    /** Условие победы — последний выживший (дефолт платформы). */
    public MatchResult checkResult() {return match.defaultResult();}

    /** Итоги: статистика всем участникам, MVP по убийствам, объявление победителя. */
    public void onEnd(MatchResult result)
    {
        EscapePlayerData mvp = null;
        for (EscapePlayerData data : matchData.values())
        {
            plugin.stats().set(data.uuid, data.name, "last_game_kills", data.kills);
            plugin.stats().max(data.uuid, data.name, "best_game_kills", data.kills);
            if (data.kills > 0 && (mvp == null || data.kills > mvp.kills)) {mvp = data;}
        }
        if (mvp != null) {plugin.stats().add(mvp.uuid, mvp.name, "mvp_games", 1);}
        DebugLog.log(Cat.SESSION, "finish arena=%s alive=%d participants=%d mvp=%s",
            arena.getId(), match.aliveCount(), matchData.size(), mvp == null ? "-" : mvp.name);

        if (!result.hasWinner()) {return;}
        UUID winnerId = result.winners().get(0);
        EscapePlayerData data = matchData.get(winnerId);
        if (data == null) {return;}
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

    /**
     * Уборка после матча. Свой откат блоков Escape ведёт сам: ядровой одношаговый
     * не чинит стыки решёток и заборов, из-за чего между ними остаются щели.
     */
    public void onCleanup()
    {
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
            if (loc.getBlock().getState() instanceof Chest chest) {chest.getInventory().setContents(entry.getValue());}
        }
        dynamicChestOriginals.clear();

        int restored = 0;
        int skipped = 0;
        List<Block> restoredBlocks = new ArrayList<>();
        for (Map.Entry<Location, BlockData> entry : editedBlocks.entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {skipped++; continue;}
            Block block = loc.getBlock();
            block.setBlockData(entry.getValue(), false); // точная форма без физики
            restoredBlocks.add(block);
            restored++;
        }
        // второй проход С физикой: соседи пересчитывают стыки (иначе остаются щели)
        for (Block block : restoredBlocks) {block.setBlockData(block.getBlockData(), true);}
        // третий проход: уцелевшие соседи, которых не ломали, тоже пересчитывают форму
        int neighborsFixed = fixConnectingNeighbors(restoredBlocks);
        DebugLog.log(Cat.WORLD, "blocks-restored arena=%s restored=%d skipped-no-world=%d neighbors-fixed=%d",
            arena.getId(), restored, skipped, neighborsFixed);

        editedBlocks.clear();
        matchFires.clear();
        activeChests.clear();
        activeChestCategories.clear();
        categoryArenaSlots.clear();
        chestStands.clear();
        traderLocations.clear();

        // мир вернулся в исходное — возвращаем и подсказки настройки
        SetupMarkers.placeAll(arena);

        // removed < tracked — часть сущностей исчезла раньше (смерть, деспавн), это норма
        int entitiesRemoved = 0;
        for (UUID id : spawnedEntities)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove(); entitiesRemoved++;}
        }
        int dropsRemoved = 0;
        for (UUID id : droppedItems)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove(); dropsRemoved++;}
        }
        DebugLog.log(Cat.WORLD, "entities-removed arena=%s spawned=%d drops=%d",
            arena.getId(), entitiesRemoved, dropsRemoved);
        spawnedEntities.clear();
        droppedItems.clear();

        lobbyChat.clear();
        gameChat.clear();
        spectatorChat.clear();
        matchData.clear();
        chosenKit.clear();
        modifierVotes.clear();
        lastVoteMs.clear();
        activeModifier = null;
        cooldowns.clear();
        currentEvent = null;
        eventPositions.clear();
        eventFlagged.clear();
        bloodMoon = false;
        glowActive = false;
        finalBattleStarted = false;
        respawnBlocks.clear();
        offlineGuards.clear();
    }

    /** Доп. строки сайдбара (стандартные рисует ядро). */
    public List<Component> scoreboardLines(Player viewer) {return List.of();}

    /** Рассылка всем онлайн-участникам матча. */
    private void broadcastAll(Component message)
    {
        for (Player p : match.onlinePlayers()) {p.sendMessage(message);}
    }
}
