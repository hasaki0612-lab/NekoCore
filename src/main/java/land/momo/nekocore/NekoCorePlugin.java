package land.momo.nekocore;

import land.momo.nekocore.command.CoreCommands;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.config.Settings;
import land.momo.nekocore.config.ConfigUpgrader;
import land.momo.nekocore.config.FeatureSettings;
import land.momo.nekocore.config.DailyTaskSettings;
import land.momo.nekocore.data.TitleRepository;
import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.data.DailyTaskRepository;
import land.momo.nekocore.data.WeeklyCoinRepository;
import land.momo.nekocore.integration.PrefixService;
import land.momo.nekocore.integration.GeoIpService;
import land.momo.nekocore.integration.NameTagService;
import land.momo.nekocore.integration.TabPanelService;
import land.momo.nekocore.service.EnchantmentService;
import land.momo.nekocore.service.InventoryExchangeService;
import land.momo.nekocore.gui.FeatureGui;
import land.momo.nekocore.gui.TitleMenu;
import land.momo.nekocore.gui.StoreMenu;
import land.momo.nekocore.gui.BagMenu;
import land.momo.nekocore.gui.DailyTaskMenu;
import land.momo.nekocore.service.TeleportRequestService;
import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.data.StorageException;
import land.momo.nekocore.gui.MenuService;
import land.momo.nekocore.integration.NekoExpansion;
import land.momo.nekocore.integration.LevelChatListener;
import land.momo.nekocore.model.Profile;
import land.momo.nekocore.service.CleanupService;
import land.momo.nekocore.service.PlayerDataService;
import land.momo.nekocore.service.CheckinService;
import land.momo.nekocore.service.TipsService;
import land.momo.nekocore.service.DeathLocationListener;
import land.momo.nekocore.service.DestinationService;
import land.momo.nekocore.service.BedHomeListener;
import land.momo.nekocore.service.AfkPoolService;
import land.momo.nekocore.service.DailyTaskService;
import land.momo.nekocore.service.HologramService;
import land.momo.nekocore.service.WeeklyCoinLeaderboardService;
import land.momo.nekocore.service.MascotService;
import land.momo.nekocore.service.JoinWelcomeService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class NekoCorePlugin extends JavaPlugin implements Listener {
    private record LoadedConfiguration(Settings settings, FeatureSettings features,
                                       DailyTaskSettings dailyTasks, Messages messages) {}
    private final Queue<Runnable> mailbox = new ConcurrentLinkedQueue<>();
    private volatile boolean stopping;
    private boolean ready;
    private boolean reloading;
    private volatile Settings settings;
    private volatile FeatureSettings features;
    private volatile DailyTaskSettings dailyTaskSettings;
    private TitleRepository titles;
    private CommerceRepository commerce;
    private PrefixService prefixes;
    private GeoIpService locations;
    private NameTagService nameTags;
    private TabPanelService tabPanel;
    private EnchantmentService enchantments;
    private InventoryExchangeService exchanges;
    private FeatureGui featureGui;
    private TitleMenu titleMenu;
    private StoreMenu storeMenu;
    private BagMenu bagMenu;
    private DailyTaskMenu dailyTaskMenu;
    private TeleportRequestService teleportRequests;
    private final Set<UUID> featureReady = new HashSet<>();
    private Messages messages;
    private SqliteStore store;
    private PlayerDataService data;
    private MenuService menus;
    private CleanupService cleanup;
    private CheckinService checkins;
    private TipsService tips;
    private DestinationService destinations;
    private AfkPoolService afkPool;
    private DailyTaskRepository dailyTaskRepository;
    private WeeklyCoinRepository weeklyCoinRepository;
    private DailyTaskService dailyTasks;
    private HologramService holograms;
    private WeeklyCoinLeaderboardService weeklyLeaderboard;
    private MascotService mascot;
    private JoinWelcomeService welcome;
    private CoreCommands commands;
    private NekoExpansion expansion;
    private BukkitTask saveTask;

    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
            ConfigUpgrader.upgrade(getDataFolder().toPath(), getLogger()::info);
            LoadedConfiguration loaded = loadConfiguration();
            settings = loaded.settings(); features = loaded.features(); dailyTaskSettings = loaded.dailyTasks();
            messages = loaded.messages();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "配置无效，NekoCore 未启用。", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        store = new SqliteStore(getLogger());
        titles = new TitleRepository(store);
        commerce = new CommerceRepository(store);
        dailyTaskRepository = new DailyTaskRepository(store);
        weeklyCoinRepository = new WeeklyCoinRepository(store);
        locations = new GeoIpService(this);
        locations.start();
        prefixes = new PrefixService(this);
        nameTags = new NameTagService(this);
        tabPanel = new TabPanelService(this);
        enchantments = new EnchantmentService(this);
        exchanges = new InventoryExchangeService(this);
        featureGui = new FeatureGui(this);
        titleMenu = new TitleMenu(this);
        storeMenu = new StoreMenu(this);
        bagMenu = new BagMenu(this);
        dailyTaskMenu = new DailyTaskMenu(this);
        teleportRequests = new TeleportRequestService(this);
        store.levelListener(profile -> onMain(() -> {
            nameTags.update(Bukkit.getPlayer(profile.uuid()));
            tabPanel.update(Bukkit.getPlayer(profile.uuid()));
            logFailure(commerce.unlock(profile.uuid(), features.bag().capacity(profile.level())), "Bag 容量解锁保存失败");
        }));
        data = new PlayerDataService(store);
        cleanup = new CleanupService(this);
        checkins = new CheckinService(this);
        tips = new TipsService(this);
        destinations = new DestinationService(this);
        afkPool = new AfkPoolService(this);
        dailyTasks = new DailyTaskService(this, dailyTaskRepository);
        holograms = new HologramService(this);
        weeklyLeaderboard = new WeeklyCoinLeaderboardService(this, weeklyCoinRepository);
        mascot = new MascotService(this);
        welcome = new JoinWelcomeService(this);
        menus = new MenuService(this);
        commands = new CoreCommands(this);
        for (String name : List.of("menu", "coins", "sethome", "home", "check", "checkin", "nekocore", "store", "bag", "tpn", "yes", "no")) {
            var command = Objects.requireNonNull(getCommand(name));
            command.setExecutor(commands); command.setTabCompleter(commands);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(featureGui, this);
        Bukkit.getPluginManager().registerEvents(exchanges, this);
        Bukkit.getPluginManager().registerEvents(storeMenu, this);
        Bukkit.getPluginManager().registerEvents(bagMenu, this);
        Bukkit.getPluginManager().registerEvents(teleportRequests, this);
        Bukkit.getPluginManager().registerEvents(afkPool, this);
        Bukkit.getPluginManager().registerEvents(dailyTasks, this);
        Bukkit.getPluginManager().registerEvents(mascot, this);
        Bukkit.getPluginManager().registerEvents(new DeathLocationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LevelChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BedHomeListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Worker completions contain no Bukkit calls until this main-thread mailbox runs.
            for (int i = 0; i < 256; i++) {
                Runnable callback = mailbox.poll();
                if (callback == null) break;
                try { callback.run(); }
                catch (RuntimeException e) { getLogger().log(Level.SEVERE, "主线程回调失败", e); }
            }
        }, 1L, 1L);
        store.open(getDataFolder().toPath().resolve(settings.databaseFile()), settings.curve())
                .thenCompose(ignored -> commerce.unlockAll(features.bag().level27(), features.bag().level36())).whenComplete((ignored, error) ->
                onMain(() -> {
                    if (error != null) {
                        getLogger().log(Level.SEVERE, "SQLite 初始化失败，NekoCore 已停用。", unwrap(error));
                        Bukkit.getPluginManager().disablePlugin(this);
                        return;
                    }
                    ready = true;
                    Bukkit.getOnlinePlayers().forEach(this::join);
                    holograms.start();
                    cleanup.restart(); tips.restart(); restartSaving(); nameTags.restart(); afkPool.restart(); dailyTasks.restart(); tabPanel.restart();
                    weeklyLeaderboard.restart(); mascot.restart();
                    enchantments.restart();
                    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        expansion = new NekoExpansion(this);
                        if (!expansion.register()) getLogger().warning("PlaceholderAPI 注册失败：nekocore 标识符已被占用。");
                    }
                    getLogger().info("Database schema 5 ready");
                    getLogger().info("Core modules loaded");
                    getLogger().info("Optional modules: GeoIP=" + state(settings.locationPrefix().enabled() && locations.available())
                            + ", Mascot=" + state(settings.mascot().enabled() && Bukkit.getPluginManager().isPluginEnabled("Citizens"))
                            + ", PlaceholderAPI=" + state(expansion != null));
                    getLogger().info("NekoCore " + getDescription().getVersion() + " ready");
                }));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { if (ready) join(event.getPlayer()); }
    private void join(Player player) {
        UUID playerId = player.getUniqueId();
        var address = player.getAddress();
        locations.resolve(playerId, address == null ? null : address.getAddress());
        dailyTasks.join(player);
        finish(player, data.join(playerId, player.getName(), System.currentTimeMillis())
                .thenCompose(ignored -> titles.load(playerId)), ignored -> {
            nameTags.refresh();
            tabPanel.update(player);
            welcome.show(player);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!present(player)) return;
                exchanges.recover(player, () -> {
                    featureReady.add(player.getUniqueId());
                    if (prefixes.autoCheckin(player)) checkins.claimAutomatic(player); else checkins.remind(player);
                });
            }, 3L);
        });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (data != null && ready) logFailure(data.quit(event.getPlayer().getUniqueId()), "保存离线游玩时长失败");
        if (dailyTasks != null && ready) logFailure(dailyTasks.quit(event.getPlayer()), "保存每日任务在线时长失败");
        if (commands != null) commands.cancelHome(event.getPlayer().getUniqueId());
        if (destinations != null) destinations.cancel(event.getPlayer().getUniqueId());
        if (nameTags != null) nameTags.quit(event.getPlayer().getUniqueId());
        if (titles != null) titles.forget(event.getPlayer().getUniqueId());
        if (locations != null) locations.quit(event.getPlayer().getUniqueId());
        if (tabPanel != null) tabPanel.quit(event.getPlayer().getUniqueId());
        if (mascot != null) mascot.quit(event.getPlayer().getUniqueId());
        featureReady.remove(event.getPlayer().getUniqueId());
    }

    private void restartSaving() {
        if (saveTask != null) saveTask.cancel();
        long ticks = settings.saveInterval() * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            logFailure(data.flush(), "游玩时长保存失败，将在下轮重试");
            if (dailyTasks != null) logFailure(dailyTasks.flushOnlineTime(Bukkit.getOnlinePlayers()), "每日任务在线时长保存失败，将在下轮重试");
        }, ticks, ticks);
    }

    @Override public void onDisable() {
        CompletableFuture<Void> finalDailyTime = dailyTasks == null || !ready
                ? CompletableFuture.completedFuture(null) : dailyTasks.flushOnlineTime(Bukkit.getOnlinePlayers());
        stopping = true;
        ready = false;
        Bukkit.getScheduler().cancelTasks(this);
        if (cleanup != null) cleanup.stop();
        if (tips != null) tips.stop();
        if (afkPool != null) afkPool.stop();
        if (dailyTasks != null) dailyTasks.stop();
        if (weeklyLeaderboard != null) weeklyLeaderboard.stop();
        if (mascot != null) mascot.stop();
        if (holograms != null) holograms.stop();
        if (tabPanel != null) tabPanel.stop();
        if (menus != null) menus.closeAll();
        if (nameTags != null) nameTags.stop();
        if (enchantments != null) enchantments.stop();
        if (featureGui != null) featureGui.closeAll();
        if (storeMenu != null) storeMenu.stop();
        if (teleportRequests != null) teleportRequests.stop();
        if (expansion != null) expansion.unregister();
        if (locations != null) locations.stop();
        mailbox.clear();
        if (store != null) {
            // No JDBC or Future.get/join on the server thread. Queue closes after final writes.
            CompletableFuture<Void> exchangesDrained = exchanges == null ? CompletableFuture.completedFuture(null) : exchanges.drain();
            CompletableFuture<Void> flush = CompletableFuture.allOf(exchangesDrained, finalDailyTime).handle((ignored, failure) -> {
                if (failure != null) getLogger().log(Level.SEVERE, "交易关停核对未完成，已保留恢复记录", unwrap(failure));
                return null;
            }).thenCompose(ignored -> data == null ? CompletableFuture.completedFuture(null) : data.stop());
            var logger = getLogger();
            flush.whenComplete((ignored, error) -> {
                if (error != null) logger.log(Level.SEVERE, "关停时保存游玩时长失败", unwrap(error));
                store.closeAsync();
            });
        }
    }

    public void reload(CommandSender sender) {
        if (reloading) { messages.send(sender, "reload-busy"); return; }
        if (exchanges.hasActive() || titleMenu.hasPending()) { messages.send(sender, "exchange-busy"); return; }
        Settings next;
        FeatureSettings nextFeatures;
        DailyTaskSettings nextDailyTasks;
        Messages nextMessages;
        try {
            LoadedConfiguration loaded = loadConfiguration();
            next = loaded.settings(); nextFeatures = loaded.features(); nextDailyTasks = loaded.dailyTasks();
            nextMessages = loaded.messages();
            if (!next.databaseFile().equals(settings.databaseFile()))
                throw new IllegalArgumentException("数据库文件名变更需要完整重启服务器");
        } catch (Exception e) {
            messages.send(sender, "reload-failed", Map.of("reason", Objects.toString(e.getMessage(), "配置格式无效")));
            return;
        }
        reloading = true;
        // Settle the old configuration's cumulative online task before restart;
        // otherwise a reload could discard up to one save interval.
        CompletableFuture<Void> taskTime = dailyTasks == null ? CompletableFuture.completedFuture(null)
                : dailyTasks.flushOnlineTime(Bukkit.getOnlinePlayers());
        taskTime.thenCompose(ignored -> store.changeCurveAndBags(next.curve(), nextFeatures.bag().level27(), nextFeatures.bag().level36()))
                .whenComplete((ignored, error) -> onMain(() -> {
            reloading = false;
            if (error != null) {
                getLogger().log(Level.SEVERE, "重载前数据结算或升级公式失败", unwrap(error));
                if (present(sender)) messages.send(sender, "reload-failed", Map.of("reason", "数据库更新失败，请查看控制台"));
                return;
            }
            settings = next; features = nextFeatures; dailyTaskSettings = nextDailyTasks; messages = nextMessages;
            menus.closeAll(); cleanup.restart(); tips.restart(); restartSaving(); nameTags.restart(); afkPool.restart(); dailyTasks.restart(); tabPanel.restart();
            weeklyLeaderboard.stop(); mascot.stop(); holograms.start(); weeklyLeaderboard.restart(); mascot.restart();
            featureGui.closeAll(); storeMenu.stop(); enchantments.restart();
            teleportRequests.stop();
            locations.restart(onlineAddresses());
            if (present(sender)) messages.send(sender, "reload-success");
        }));
    }

    private LoadedConfiguration loadConfiguration() throws Exception {
        File config = new File(getDataFolder(), "config.yml");
        Settings loadedSettings = Settings.load(config);
        FeatureSettings loadedFeatures = FeatureSettings.load(config);
        DailyTaskSettings loadedTasks = DailyTaskSettings.load(config);
        EnchantmentService.validatePool(loadedFeatures.books());
        Messages loadedMessages = Messages.load(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"))
                .globals(Map.of("server", loadedSettings.serverName()));
        return new LoadedConfiguration(loadedSettings, loadedFeatures, loadedTasks, loadedMessages);
    }

    public void checkConfig(CommandSender sender) {
        try {
            loadConfiguration();
            messages.send(sender, "config-check-success");
        } catch (Exception error) {
            messages.send(sender, "config-check-failed", Map.of("reason", Objects.toString(error.getMessage(), "配置格式无效")));
        }
    }

    public void status(CommandSender sender) {
        List<String> lines = new ArrayList<>();
        lines.add("&bNekoCore Public &f" + getDescription().getVersion());
        lines.add("&7Paper &f" + Bukkit.getVersion());
        lines.add("&7Java &f" + System.getProperty("java.version") + " &7· Database schema &f5");
        lines.add(statusLine("Profile", true, ""));
        lines.add(statusLine("Store", features.shop().enabled(), "config: store.enabled"));
        lines.add(statusLine("Bag", features.bag().enabled(), "config: bag.enabled"));
        lines.add(statusLine("Daily Tasks", dailyTaskSettings.enabled(), "config: daily-tasks.enabled"));
        lines.add(statusLine("GeoIP", settings.locationPrefix().enabled() && locations.available(),
                settings.locationPrefix().enabled() ? "MMDB not configured or unreadable" : "config: location-prefix.enabled"));
        boolean citizens = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        lines.add(statusLine("Mascot", settings.mascot().enabled() && citizens,
                !settings.mascot().enabled() ? "config: mascot.enabled" : !citizens ? "Citizens not installed" : "NPC unavailable"));
        lines.add(statusLine("AFK Pool", settings.afkPool().enabled(), "destination missing or disabled"));
        lines.add(statusLine("Leaderboard", settings.weeklyLeaderboard().enabled() && settings.weeklyLeaderboard().positionConfigured(),
                "position missing or disabled"));
        lines.add(statusLine("PlaceholderAPI", expansion != null, "plugin not installed"));
        lines.forEach(line -> sender.sendMessage(Messages.text(line, Map.of())));
    }

    private static String statusLine(String name, boolean enabled, String reason) {
        return String.format(Locale.ROOT, "&7%-18s %s", name,
                enabled ? "&aENABLED" : "&eDISABLED&7 · " + reason);
    }
    private static String state(boolean enabled) { return enabled ? "enabled" : "disabled"; }

    public boolean available(CommandSender sender) {
        if (!ready || reloading) { messages.send(sender, reloading ? "reload-busy" : "loading"); return false; }
        if (sender instanceof Player player && !data.loaded(player.getUniqueId())) { messages.send(sender, "loading"); return false; }
        return true;
    }

    public boolean permission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        messages.send(sender, "no-permission"); return false;
    }
    public boolean featuresAvailable(Player player) {
        if (!available(player)) return false;
        if (!exchanges.idle(player)) return false;
        if (!featureReady.contains(player.getUniqueId())) { messages.send(player, "loading"); return false; }
        return true;
    }

    public <T> void finish(CommandSender sender, CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((value, error) -> onMain(() -> {
            if (error != null) {
                Throwable cause = unwrap(error);
                if (!(cause instanceof StorageException)) getLogger().log(Level.SEVERE, "NekoCore 数据操作失败", cause);
                if (present(sender)) messages.send(sender, cause instanceof StorageException storage ? storage.messageKey() : "storage-error");
            } else if (present(sender)) success.accept(value);
        }));
    }

    public void onMain(Runnable callback) { if (!stopping) mailbox.add(callback); }
    public static boolean present(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player;
    }
    public static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
    private void logFailure(CompletableFuture<?> future, String message) {
        var logger = getLogger();
        future.whenComplete((ignored, error) -> { if (error != null) logger.log(Level.SEVERE, message, unwrap(error)); });
    }

    public Map<String, String> variables(Profile profile) {
        var progress = settings.curve().progress(profile.exp());
        var time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(CommerceRepository.ZONE);
        Map<String, String> vars = new HashMap<>();
        vars.put("player", profile.name()); vars.put("coins", "" + profile.coins()); vars.put("level", "" + progress.level());
        vars.put("exp", "" + progress.exp()); vars.put("exp_needed", "" + progress.needed()); vars.put("total_exp", "" + profile.exp());
        vars.put("playtime", formatPlaytime(profile.playtimeSeconds())); vars.put("world", settings.survivalWorld());
        vars.put("server", settings.serverName());
        vars.put("world_new", settings.features().newWorld());
        vars.put("checkin_coins", "" + settings.features().checkin().coins());
        vars.put("checkin_exp", "" + settings.features().checkin().exp());
        vars.put("checkin_status", messages.raw(checkins.status(profile.uuid())));
        vars.put("first_join", time.format(Instant.ofEpochMilli(profile.firstJoin())));
        vars.put("last_join", time.format(Instant.ofEpochMilli(profile.lastJoin())));
        vars.put("show_ip", messages.raw(profile.showIp() ? "privacy-on" : "privacy-off"));
        return vars;
    }

    public static String formatPlaytime(long seconds) { return seconds / 3600 + "小时 " + seconds % 3600 / 60 + "分钟 " + seconds % 60 + "秒"; }
    public Settings settings() { return settings; }
    public FeatureSettings features() { return features; }
    public DailyTaskSettings dailyTaskSettings() { return dailyTaskSettings; }
    public TitleRepository titles() { return titles; }
    public CommerceRepository commerce() { return commerce; }
    public PrefixService prefixes() { return prefixes; }
    public GeoIpService locations() { return locations; }
    public NameTagService nameTags() { return nameTags; }
    public TabPanelService tabPanel() { return tabPanel; }
    public EnchantmentService enchantments() { return enchantments; }
    public InventoryExchangeService exchanges() { return exchanges; }
    public FeatureGui featureGui() { return featureGui; }
    public TitleMenu titleMenu() { return titleMenu; }
    public StoreMenu storeMenu() { return storeMenu; }
    public BagMenu bagMenu() { return bagMenu; }
    public DailyTaskMenu dailyTaskMenu() { return dailyTaskMenu; }
    public TeleportRequestService teleportRequests() { return teleportRequests; }
    public Messages messages() { return messages; }
    public SqliteStore store() { return store; }
    public PlayerDataService data() { return data; }
    public MenuService menus() { return menus; }
    public CleanupService cleanup() { return cleanup; }
    public CheckinService checkins() { return checkins; }
    public DestinationService destinations() { return destinations; }
    public AfkPoolService afkPool() { return afkPool; }
    public DailyTaskService dailyTasks() { return dailyTasks; }
    public HologramService holograms() { return holograms; }
    public WeeklyCoinLeaderboardService weeklyLeaderboard() { return weeklyLeaderboard; }
    public MascotService mascot() { return mascot; }

    private Map<UUID, InetAddress> onlineAddresses() {
        Map<UUID, InetAddress> result = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var address = player.getAddress();
            if (address != null) result.put(player.getUniqueId(), address.getAddress());
        }
        return result;
    }
}
