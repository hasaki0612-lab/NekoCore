package land.momo.nekocore.service;

import io.papermc.paper.event.player.PlayerTradeEvent;
import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.DailyTaskSettings;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.DailyTaskRepository;
import land.momo.nekocore.data.StorageException;
import land.momo.nekocore.task.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.logging.Level;

/** Global Beijing-date rotation plus event-driven per-player progress. */
public final class DailyTaskService implements Listener {
    public record View(DailyTaskRotation rotation, Map<String, DailyTaskProgress> progress) {}
    private final NekoCorePlugin plugin;
    private final DailyTaskRepository repository;
    private final Clock clock;
    private final RandomGenerator random;
    private final java.util.function.LongSupplier nanoClock;
    private final Predicate<Material> edible;
    private final Predicate<ItemStack> silkTouch;
    private final MlgTracker mlg = new MlgTracker();
    private final Map<UUID,Long> onlineNanos = new HashMap<>();
    private final Map<UUID,Long> retryOnlineSeconds = new HashMap<>();
    private volatile DailyTaskRotation rotation;
    private CompletableFuture<DailyTaskRotation> loading;
    private BukkitTask midnightTask;
    private int generation;

    public DailyTaskService(NekoCorePlugin plugin, DailyTaskRepository repository) {
        this(plugin, repository, Clock.systemUTC(), java.util.concurrent.ThreadLocalRandom.current(), System::nanoTime);
    }
    DailyTaskService(NekoCorePlugin plugin, DailyTaskRepository repository, Clock clock,
                     RandomGenerator random, java.util.function.LongSupplier nanoClock) {
        this(plugin, repository, clock, random, nanoClock, Material::isEdible,
                item -> item.containsEnchantment(Enchantment.SILK_TOUCH));
    }
    DailyTaskService(NekoCorePlugin plugin, DailyTaskRepository repository, Clock clock,
                     RandomGenerator random, java.util.function.LongSupplier nanoClock,
                     Predicate<Material> edible, Predicate<ItemStack> silkTouch) {
        this.plugin = plugin; this.repository = repository; this.clock = clock; this.random = random;
        this.nanoClock = nanoClock; this.edible = edible; this.silkTouch = silkTouch;
    }

    public void restart() {
        stop(); generation++;
        if (!settings().enabled()) return;
        Bukkit.getOnlinePlayers().forEach(this::join);
        ensureToday(); scheduleMidnight();
    }
    public void stop() {
        if (midnightTask != null) { midnightTask.cancel(); midnightTask = null; }
        rotation = null; loading = null; onlineNanos.clear(); retryOnlineSeconds.clear(); mlg.clear(); generation++;
    }
    public void join(Player player) { onlineNanos.put(player.getUniqueId(), nanoClock.getAsLong()); }
    public CompletableFuture<Void> quit(Player player) {
        CompletableFuture<Void> result = flushOnline(player.getUniqueId(), nanoClock.getAsLong());
        onlineNanos.remove(player.getUniqueId()); mlg.cancel(player.getUniqueId()); return result;
    }
    public CompletableFuture<Void> flushOnlineTime(Collection<? extends Player> online) {
        if (!settings().enabled()) return CompletableFuture.completedFuture(null);
        long now = nanoClock.getAsLong(); List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Player player : online) writes.add(flushOnline(player.getUniqueId(), now));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    private CompletableFuture<Void> flushOnline(UUID player, long now) {
        Long previous = onlineNanos.put(player, now);
        long seconds = previous == null ? 0 : Math.max(0, (now - previous) / 1_000_000_000L);
        Long retry = retryOnlineSeconds.remove(player); if (retry != null) seconds = saturating(seconds, retry);
        DailyTaskRotation current = rotation;
        String taskId = DailyTaskDefinition.SIMPLE_GOOD_MORNING.id();
        // Keep the captured rotation here instead of asking ensureToday(): a flush begun at
        // Beijing midnight belongs to the day whose stopwatch just ended.
        if (seconds <= 0 || current == null || !current.contains(taskId)) return CompletableFuture.completedFuture(null);
        long delta = seconds;
        CompletableFuture<DailyTaskRepository.Advance> write = advanceFuture(player, current, taskId, delta, null);
        return write.handle((result, error) -> {
            if (error != null) {
                plugin.onMain(() -> retryOnlineSeconds.merge(player, delta, DailyTaskService::saturating));
                throw new CompletionException(error);
            }
            else if (result.completedNow()) plugin.onMain(() -> complete(player, taskId, result));
            return null;
        });
    }

    public synchronized CompletableFuture<DailyTaskRotation> ensureToday() {
        if (!settings().enabled()) return CompletableFuture.failedFuture(new IllegalStateException("Daily tasks disabled"));
        LocalDate today = today();
        if (rotation != null && rotation.date().equals(today)) return CompletableFuture.completedFuture(rotation);
        if (loading != null && !loading.isDone()) return loading;
        int token = generation; List<DailyTaskRotation.Entry> candidates = DailyTaskSelector.draw(settings(), random);
        loading = repository.ensure(today, candidates).thenApply(value -> {
            if (token == generation) rotation = value; return value;
        });
        return loading;
    }
    public CompletableFuture<DailyTaskRotation> reroll() {
        LocalDate today = today(); int token = generation;
        return repository.reroll(today, DailyTaskSelector.draw(settings(), random)).thenApply(value -> {
            if (token == generation) rotation = value; return value;
        });
    }
    public CompletableFuture<View> view(UUID player) {
        return ensureToday().thenCompose(value -> repository.progress(player, value).thenApply(progress -> new View(value, progress)));
    }
    public CompletableFuture<Void> reset(UUID player) { return ensureToday().thenCompose(value -> repository.reset(player, value)); }
    public CompletableFuture<DailyTaskRepository.Advance> adminProgress(UUID player, String taskId, long amount) {
        return ensureToday().thenCompose(value -> {
            if (!value.contains(taskId)) throw new StorageException("tasks-task-not-selected");
            var definition = DailyTaskDefinition.byId(taskId);
            return repository.advance(player, rotation, taskId, amount, null, settings().target(taskId),
                    settings().reward(definition.difficulty()), land.momo.nekocore.data.CoinChangeReason.ADMIN);
        });
    }
    public DailyTaskRotation current() { return rotation; }
    public LocalDate today() { return LocalDate.now(clock.withZone(settings().zone())); }
    public ZonedDateTime nextRefresh() { return today().plusDays(1).atStartOfDay(settings().zone()); }

    private void scheduleMidnight() {
        if (midnightTask != null) midnightTask.cancel();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(settings().zone()));
        long millis = Math.max(50, Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(settings().zone())).toMillis());
        int token = generation;
        midnightTask = Bukkit.getScheduler().runTaskLater(plugin, () -> rotateAtMidnight(token), Math.max(1, (millis + 49) / 50));
    }

    private void rotateAtMidnight(int token) {
        if (token != generation) return;
        flushOnlineTime(List.copyOf(Bukkit.getOnlinePlayers())).whenComplete((ignored, flushError) -> plugin.onMain(() -> {
            if (token != generation) return;
            if (flushError != null) {
                plugin.getLogger().log(Level.SEVERE, "每日任务换日前的在线时长保存失败，20 秒后重试", NekoCorePlugin.unwrap(flushError));
                midnightTask = Bukkit.getScheduler().runTaskLater(plugin, () -> rotateAtMidnight(token), 20L * 20L);
                return;
            }
            rotation = null; loading = null;
            ensureToday().whenComplete((value, error) -> {
                if (error != null) plugin.getLogger().log(Level.SEVERE, "每日任务换日失败，将在重载或下次启动重试", NekoCorePlugin.unwrap(error));
            });
            scheduleMidnight();
        }));
    }

    private DailyTaskSettings settings() { return plugin.dailyTaskSettings(); }
    private boolean active(String id) {
        DailyTaskRotation current = rotation;
        if (current != null && current.date().equals(today())) return current.contains(id);
        ensureToday(); return false;
    }
    private void advance(Player player, String taskId, long amount) { advance(player, taskId, amount, null); }
    private void advance(Player player, String taskId, long amount, String unique) {
        if (!active(taskId) || amount <= 0) return;
        var future = advanceFuture(player.getUniqueId(), taskId, amount, unique);
        plugin.finish(player, future, result -> { if (result.completedNow()) complete(player.getUniqueId(), taskId, result); });
    }
    private CompletableFuture<DailyTaskRepository.Advance> advanceFuture(UUID player, String taskId, long amount, String unique) {
        return advanceFuture(player, rotation, taskId, amount, unique);
    }
    private CompletableFuture<DailyTaskRepository.Advance> advanceFuture(UUID player, DailyTaskRotation current,
                                                                          String taskId, long amount, String unique) {
        DailyTaskDefinition definition = DailyTaskDefinition.byId(taskId);
        if (current == null || definition == null || !current.contains(taskId)) return CompletableFuture.completedFuture(
                new DailyTaskRepository.Advance(new DailyTaskProgress(taskId,0,false,false,""),false,false,0,0));
        return repository.advance(player, current, taskId, amount, unique, settings().target(taskId), settings().reward(definition.difficulty()));
    }
    private void complete(UUID playerId, String taskId, DailyTaskRepository.Advance result) {
        Player player = Bukkit.getPlayer(playerId); if (player == null || !NekoCorePlugin.present(player)) return;
        DailyTaskDefinition definition = DailyTaskDefinition.byId(taskId);
        List<String> templates = plugin.messages().lines("daily-tasks.completion-messages");
        String task = plugin.messages().raw("daily-tasks.tasks." + taskId + ".name");
        String difficulty = plugin.messages().raw("daily-tasks.difficulty." + definition.difficulty().key());
        Map<String,String> vars = Map.of("task", task, "difficulty", difficulty,
                "coins", "" + result.coins(), "exp", "" + result.exp());
        if (!templates.isEmpty()) player.sendMessage(Messages.text(templates.get(random.nextInt(templates.size())), vars));
        var feedback = settings().feedback();
        player.playSound(player.getLocation(), feedback.sound(), feedback.volume(), feedback.pitch());
        if (feedback.particles() > 0) player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                feedback.particles(), 0.35, 0.45, 0.35, 0.01);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer(); Material material = event.getBlock().getType(); ItemStack tool = player.getInventory().getItemInMainHand();
        if (active("simple_gardener") && settings().gardenerBlocks().contains(material)
                && (tool.getType() == Material.SHEARS || silkTouch.test(tool)) && event.isDropItems())
            advance(player, "simple_gardener", 1);
        if (active("normal_harvest") && settings().matureCrops().contains(material)
                && event.getBlock().getBlockData() instanceof Ageable crop && crop.getAge() == crop.getMaximumAge())
            advance(player, "normal_harvest", 1);
        if (active("normal_deepslate_worker") && settings().deepslateOres().contains(material)) advance(player, "normal_deepslate_worker", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        Material material = event.getItem().getType();
        if (material == Material.APPLE) advance(event.getPlayer(), "simple_healthy", 1);
        if (edible.test(material)) advance(event.getPlayer(), "simple_eat_food", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shear(PlayerShearEntityEvent event) {
        if (event.getEntity().getType() == EntityType.SHEEP) advance(event.getPlayer(), "simple_shear_sheep", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (settings().plantItems().contains(event.getItemInHand().getType())) advance(event.getPlayer(), "simple_pastoral", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getInventory().getMatrix().length != 9) return;
        Material result = event.getRecipe().getResult().getType(); int amount = CraftingAmounts.output(event); if (amount <= 0) return;
        advance(player, "simple_crafting", 1, result.name());
        if (result == Material.CAKE || result == Material.PUMPKIN_PIE) advance(player, "normal_dessert", settings().target("normal_dessert"));
        else if (result == Material.COOKIE) advance(player, "normal_dessert", amount);
        if (settings().ironTools().contains(result)) advance(player, "normal_blacksmith", amount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trade(PlayerTradeEvent event) {
        advance(event.getPlayer(), "normal_tax_collector", 1); advance(event.getPlayer(), "hard_tycoon", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Enemy) advance(killer, "normal_cleanup", 1);
        if (killer != null && event.getEntityType() == EntityType.IRON_GOLEM) advance(killer, "hard_iron_golem", 1);
        if (!active("hard_marksman") || event.getEntityType() == EntityType.HAPPY_GHAST
                || !settings().flyingTargets().contains(event.getEntityType())) return;
        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damage)
                || !(damage.getDamager() instanceof AbstractArrow arrow)
                || !Set.of(EntityType.ARROW, EntityType.SPECTRAL_ARROW).contains(arrow.getType())) return;
        ProjectileSource shooter = arrow.getShooter();
        if (shooter instanceof Player player && player.equals(killer)) advance(player, "hard_marksman", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void furnace(FurnaceExtractEvent event) { advance(event.getPlayer(), "normal_smelt", event.getItemAmount()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item
                && settings().fish().contains(item.getItemStack().getType())) advance(event.getPlayer(), "normal_fishing", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) { advance(event.getEnchanter(), "hard_enchant", 1); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bucket(PlayerBucketEmptyEvent event) {
        if (!active("hard_mlg_water") || event.getBucket() != Material.WATER_BUCKET) return;
        Block block = event.getBlock();
        mlg.placedWater(event.getPlayer().getUniqueId(), key(block), nanoClock.getAsLong());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        Player player = event.getPlayer();
        if (!active("hard_mlg_water")) { mlg.cancel(player.getUniqueId()); return; }
        Block feet = event.getTo().getBlock();
        if (mlg.move(player.getUniqueId(), player.getWorld().getUID(), event.getFrom().getY(), event.getTo().getY(),
                player.isOnGround(), feet.getType() == Material.WATER, key(feet), nanoClock.getAsLong(), settings().mlgHeight()))
            advance(player, "hard_mlg_water", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR) public void teleport(PlayerTeleportEvent event) { mlg.cancel(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void changedWorld(PlayerChangedWorldEvent event) { mlg.cancel(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void playerDeath(PlayerDeathEvent event) { mlg.cancel(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void playerQuit(PlayerQuitEvent event) { mlg.cancel(event.getPlayer().getUniqueId()); }

    private static MlgTracker.BlockKey key(Block block) {
        return new MlgTracker.BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
    private static long saturating(long left, long right) { return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }
}
