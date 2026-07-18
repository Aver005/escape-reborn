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
import me.aver005.escape.arena.WeightedItem;
import me.aver005.escape.contract.Contract;
import me.aver005.escape.contract.ContractPapers;
import me.aver005.escape.contract.ContractType;
import me.aver005.escape.player.PlayerSnapshot;
import me.aver005.escape.util.Items;
import me.aver005.escape.util.Msg;
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

    private final ChatChannel lobbyChat = new ChatChannel("chat.lobby-format");
    private final ChatChannel gameChat = new ChatChannel("chat.game-format");
    private final ChatChannel spectatorChat = new ChatChannel("chat.spectator-format");

    // runtime мира
    private final LinkedHashMap<Location, BlockData> editedBlocks = new LinkedHashMap<>();
    private final Set<Location> activeChests = new HashSet<>();
    private final Map<Location, UUID> chestStands = new HashMap<>();
    private final List<Location> traderLocations = new ArrayList<>();
    private final Set<UUID> spawnedEntities = new HashSet<>();
    private final Set<UUID> droppedItems = new HashSet<>();
    private final Map<Location, BukkitTask> refillTasks = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask mainTask;
    private BukkitTask stopTask;

    private int countdownRemaining = 0;
    private int remaining = 0;
    private GameEvent currentEvent = null;
    private int eventTicksLeft = 0;
    private final Map<UUID, Location> eventPositions = new HashMap<>();
    private final Set<UUID> eventFlagged = new HashSet<>();
    private boolean bloodMoon = false;

    public GameSession(EscapePlugin plugin, Arena arena)
    {
        this.plugin = plugin;
        this.arena = arena;
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

        p.teleport(arena.getLobby());
        p.getInventory().setItem(8, Items.special(Material.MAGMA_CREAM,
            Msg.get("lobby.leave-item-name"), List.of(Msg.get("lobby.leave-item-lore")), "leave"));

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
            if (phase == Phase.WAITING && lobby.isEmpty()) {dispose();}
            return true;
        }
        if (playing.contains(id))
        {
            dropInventory(p, p.getLocation());
            eliminate(p, false);
            plugin.arenas().unbind(id);
            PlayerSnapshot.restore(plugin, p);
            return true;
        }
        if (spectators.remove(id))
        {
            spectatorChat.remove(id);
            plugin.arenas().unbind(id);
            PlayerSnapshot.restore(plugin, p);
            return true;
        }
        return false;
    }

    /** Выход с сервера во время матча. */
    public void handleQuit(Player p)
    {
        UUID id = p.getUniqueId();
        if (lobby.contains(id)) {leave(p); return;}
        if (playing.contains(id))
        {
            dropInventory(p, p.getLocation());
            eliminate(p, true);
            // снапшот не восстанавливаем сейчас — восстановится при заходе (файл остаётся)
            plugin.arenas().unbind(id);
            spectators.remove(id);
            spectatorChat.remove(id);
            return;
        }
        if (spectators.remove(id))
        {
            spectatorChat.remove(id);
            plugin.arenas().unbind(id);
        }
    }

    public void addWarmupDamage(Player damager, double dmg)
    {
        warmupDamage.merge(damager.getUniqueId(), dmg, Double::sum);
    }

    private void startCountdown(int seconds)
    {
        phase = Phase.COUNTDOWN;
        countdownRemaining = seconds;
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
        {
            if (lobby.size() < arena.getMinPlayers())
            {
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
        if (world == null) {forceStop(); return;}

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

            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setLevel(0);
            p.teleport(spawn.clone().add(0.5, 0, 0.5));
            p.addPotionEffects(startEffects);

            giveKit(p);
            Msg.send(p, "game.start-effects-hint");
        }
        lobby.clear();
        warmupDamage.clear();

        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void giveKit(Player p)
    {
        ItemStack fork = Items.special(Material.GOLDEN_PICKAXE,
            Msg.get("game.fork-name"), Msg.getList("game.fork-lore"), "fork");
        ItemMeta meta = fork.getItemMeta();
        int maxDurability = fork.getType().getMaxDurability();
        int uses = Math.max(1, Math.min(arena.getForkUses(), maxDurability));
        ((Damageable) meta).setDamage(maxDurability - uses);
        fork.setItemMeta(meta);
        p.getInventory().addItem(fork);
        p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, arena.getStartGold()));
        p.getInventory().addItem(Items.special(Material.PAPER,
            Msg.get("game.assistant-name"), Msg.getList("game.assistant-lore"), "assistant"));
    }

    private void placeChests()
    {
        List<Location> pool = new ArrayList<>(arena.getChestSpots());
        Collections.shuffle(pool, RANDOM);
        int count = Math.min(arena.getChestCount(), pool.size());
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

            ArmorStand stand = loc.getWorld().spawn(loc.clone().add(0.5, -1, 0.5), ArmorStand.class, as ->
            {
                as.setVisible(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setCanPickupItems(false);
                as.setCustomNameVisible(false);
                as.setPersistent(true);
            });
            spawnedEntities.add(stand.getUniqueId());
            chestStands.put(block.getLocation(), stand.getUniqueId());
        }
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
            if (roll < 0) {return entry.item().clone();}
        }
        return loot.get(loot.size() - 1).item().clone();
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
                v.getPersistentDataContainer().set(me.aver005.escape.util.Keys.TRADER_TYPE,
                    org.bukkit.persistence.PersistentDataType.STRING, type.getId());
            });
            spawnedEntities.add(villager.getUniqueId());
            traderLocations.add(villager.getLocation());
        }
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
    }

    // ===== игровой цикл =====

    private void tick()
    {
        if (phase != Phase.RUNNING) {return;}
        if (remaining <= 0) {return;} // финальная битва без таймера
        remaining--;

        forEachOnline(playing, p -> p.setLevel(remaining));

        int interval = Math.max(30, arena.getEventIntervalSeconds());
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
            gameChat.systemKey("game.salary", Msg.ph("n", arena.getSalaryGold()));
            forEachOnline(playing, p ->
            {
                p.getWorld().strikeLightningEffect(p.getLocation());
                giveGold(p, arena.getSalaryGold());
            });
        }

        if (remaining == arena.getGlowSecondsBeforeEnd())
        {
            gameChat.systemKey("game.glow-warning");
            forEachOnline(playing, p ->
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

    private void startRandomEvent()
    {
        List<GameEvent> candidates = new ArrayList<>();
        for (GameEvent event : GameEvent.values())
        {
            if (event.canStart(this)) {candidates.add(event);}
        }
        if (candidates.isEmpty()) {return;}
        GameEvent event = candidates.get(RANDOM.nextInt(candidates.size()));

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
        forEachOnline(playing, p -> event.resolvePlayer(this, p));
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
    public void flagEventAction(Player p) {eventFlagged.add(p.getUniqueId());}
    public void setBloodMoon(boolean v) {bloodMoon = v;}
    public boolean isBloodMoon() {return bloodMoon;}

    public void forEachPlaying(java.util.function.Consumer<Player> action)
    {
        forEachOnline(playing, action);
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
        gameChat.systemKey("game.final-battle");
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
        progressContracts(p, ContractType.LOOT, c -> true, 1);
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

        int delay = plugin.getConfig().getInt("chest-refill-seconds", 180);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
        {
            refillTasks.remove(loc);
            if (phase != Phase.RUNNING) {return;}
            if (loc.getBlock().getState() instanceof Chest target)
            {
                generateChestLoot(target);
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
    }

    /** Запомнить блок для восстановления после матча (решётки и т.п.). */
    public void rememberEditedBlock(Block block)
    {
        editedBlocks.putIfAbsent(block.getLocation(), block.getBlockData().clone());
    }

    public void dropInventory(Player p, Location where)
    {
        for (ItemStack item : p.getInventory().getContents())
        {
            if (item == null || item.getType().isAir()) {continue;}
            Item drop = p.getWorld().dropItem(where.clone().add(0, 1.2, 0), item);
            droppedItems.add(drop.getUniqueId());
        }
        p.getInventory().clear();
    }

    public void trackDrop(Item item)
    {
        droppedItems.add(item.getUniqueId());
    }

    /** Фейковая смерть: игрок становится наблюдателем. quit = вышел с сервера. */
    public void eliminate(Player p, boolean quit)
    {
        UUID id = p.getUniqueId();
        if (!playing.remove(id)) {return;}

        MatchPlayer data = matchData.get(id);

        // кредит убийства
        if (data != null && data.lastDamager != null
            && System.currentTimeMillis() - data.lastDamagerAt <= KILL_CREDIT_MILLIS
            && playing.contains(data.lastDamager))
        {
            Player killer = Bukkit.getPlayer(data.lastDamager);
            if (killer != null)
            {
                MatchPlayer killerData = matchData.get(killer.getUniqueId());
                if (killerData != null) {killerData.kills++;}
                plugin.stats().add(killer.getUniqueId(), killer.getName(), "kills", 1);
                progressContracts(killer, ContractType.KILLS, c -> true, 1);
            }
        }

        plugin.stats().add(id, p.getName(), "deaths", 1);
        plugin.stats().add(id, p.getName(), "loses", 1);

        String deadMsgRaw = randomDeadMessage();
        gameChat.system(Msg.get("game.death-broadcast",
            Msg.phC("message", Msg.mm(deadMsgRaw, Msg.ph("player", p.getName())))));
        spectatorChat.system(Msg.get("game.death-broadcast",
            Msg.phC("message", Msg.mm(deadMsgRaw, Msg.ph("player", p.getName())))));
        gameChat.remove(id);

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

        int stopDelay = 3;
        if (playing.size() == 1)
        {
            UUID winnerId = playing.iterator().next();
            Player winner = Bukkit.getPlayer(winnerId);
            MatchPlayer data = matchData.get(winnerId);
            if (winner != null && data != null)
            {
                plugin.stats().add(winnerId, winner.getName(), "wins", 1);
                broadcastAll(Msg.get("game.win-header"));
                broadcastAll(Component.empty());
                broadcastAll(Msg.get("game.win-player", Msg.ph("player", winner.getName())));
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
                forEachOnline(playing, pl -> pl.setLevel(sec));
                forEachOnline(spectators, pl -> pl.setLevel(sec));
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
        cancelTask(countdownTask); countdownTask = null;
        cancelTask(mainTask); mainTask = null;
        cancelTask(stopTask); stopTask = null;
        phase = Phase.ENDING;
        cleanup();
    }

    // ===== очистка =====

    private void cleanup()
    {
        for (BukkitTask task : refillTasks.values()) {task.cancel();}
        refillTasks.clear();

        // сундуки: очистить содержимое перед восстановлением
        for (Location loc : activeChests)
        {
            if (loc.getWorld() == null) {continue;}
            if (loc.getBlock().getState() instanceof Chest chest) {chest.getInventory().clear();}
        }

        // вернуть все изменённые блоки (решётки, сундуки, руды, столы)
        for (Map.Entry<Location, BlockData> entry : editedBlocks.entrySet())
        {
            Location loc = entry.getKey();
            if (loc.getWorld() == null) {continue;}
            loc.getBlock().setBlockData(entry.getValue(), false);
        }
        editedBlocks.clear();
        activeChests.clear();
        chestStands.clear();
        traderLocations.clear();

        for (UUID id : spawnedEntities)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove();}
        }
        spawnedEntities.clear();

        for (UUID id : droppedItems)
        {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {entity.remove();}
        }
        droppedItems.clear();

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
            }
        }
        lobby.clear();
        playing.clear();
        spectators.clear();
        lobbyChat.clear();
        gameChat.clear();
        spectatorChat.clear();
        matchData.clear();
        cooldowns.clear();
        currentEvent = null;
        eventPositions.clear();
        eventFlagged.clear();
        bloodMoon = false;

        dispose();
    }

    private void dispose()
    {
        arena.setSession(null);
    }

    /** Принудительный старт админом (нужно >= 2 в лобби). */
    public boolean forceStart()
    {
        if (phase == Phase.RUNNING || phase == Phase.ENDING) {return false;}
        if (lobby.size() < 2) {return false;}
        cancelTask(countdownTask);
        countdownTask = null;
        startCountdown(Math.min(5, arena.getStartDelayFullSeconds()));
        return true;
    }

    /** Остановка админом с отсчётом. */
    public void adminStop()
    {
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
