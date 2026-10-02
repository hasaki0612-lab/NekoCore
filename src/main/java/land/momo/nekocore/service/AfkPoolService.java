package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.*;
import java.util.logging.Level;

/** One lightweight five-tick water scan; rewards and title refreshes are gated to once per second. */
public final class AfkPoolService implements Listener {
    static final class Session {
        final UUID token = UUID.randomUUID();
        final long startedAt;
        long nextReward;
        long outsideSince = -1;
        long nextUpdate;
        double expRemainder;
        boolean inventoryOpen;
        long inventoryGeneration;
        boolean rewardPending;
        long rewardTitleUntil;
        long shownExp;
        long shownCoins;
        Session(long startedAt, long nextReward) { this.startedAt = startedAt; this.nextReward = nextReward; }
    }

    private final NekoCorePlugin plugin;
    private final LongSupplier nanoClock;
    private final DoubleSupplier chance;
    private final IntBinaryOperator randomInclusive;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask timer;
    private final Map<UUID, Long> titleSuppressedUntil = new HashMap<>();

    public AfkPoolService(NekoCorePlugin plugin) {
        this(plugin, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble(),
                (minimum, maximum) -> ThreadLocalRandom.current().nextInt(minimum, maximum + 1));
    }

    AfkPoolService(NekoCorePlugin plugin, LongSupplier nanoClock, DoubleSupplier chance, IntBinaryOperator randomInclusive) {
        this.plugin = plugin; this.nanoClock = nanoClock; this.chance = chance; this.randomInclusive = randomInclusive;
    }

    public void restart() {
        stop();
        if (plugin.settings().afkPool().enabled())
            timer = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 1L, 5L);
    }

    void tick() { tick(Bukkit.getOnlinePlayers()); }

    void tick(Collection<? extends Player> online) {
        long now = nanoClock.getAsLong();
        Set<UUID> seen = new HashSet<>();
        for (Player player : online) {
            UUID id = player.getUniqueId(); seen.add(id);
            if (!baseEligible(player)) { finish(player, now); continue; }
            Session session = sessions.get(id);
            if (!player.isInWater()) {
                if (session == null) continue;
                if (session.outsideSince < 0) session.outsideSince = now;
                if (now - session.outsideSince >= seconds(plugin.settings().afkPool().exitGrace())) finish(player, now);
                else if (now >= session.nextUpdate) { session.nextUpdate = now + seconds(1); refreshTitle(player, session, now); }
                continue;
            }
            if (session == null) {
                session = new Session(now, now + seconds(plugin.settings().afkPool().reward().interval()));
                sessions.put(id, session);
            }
            session.outsideSince = -1;
            if (now < session.nextUpdate) continue;
            session.nextUpdate = now + seconds(1);
            if (now >= session.nextReward && !session.rewardPending) reward(player, session, now);
            refreshTitle(player, session, now);
        }
        sessions.keySet().removeIf(id -> !seen.contains(id));
    }

    private boolean baseEligible(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && player.getWorld().getName().equals(plugin.settings().afkPool().teleport().world());
    }

    private void reward(Player player, Session session, long now) {
        var reward = plugin.settings().afkPool().reward();
        double exact = reward.baseExp() * plugin.prefixes().afkExpMultiplier(player.getUniqueId()) + session.expRemainder;
        long exp = (long) Math.floor(exact + 1.0e-9);
        session.expRemainder = Math.max(0, exact - exp);
        long coins = chance.getAsDouble() < reward.coinChance()
                ? randomInclusive.applyAsInt(reward.coinMin(), reward.coinMax()) : 0;
        session.nextReward = now + seconds(reward.interval());
        session.rewardPending = true;
        UUID id = player.getUniqueId(), token = session.token;
        plugin.store().afkReward(id, exp, coins).whenComplete((result, error) -> plugin.onMain(() -> {
            Session current = sessions.get(id);
            if (current != null && current.token.equals(token)) current.rewardPending = false;
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "挂机池奖励提交失败：" + id, NekoCorePlugin.unwrap(error));
                if (NekoCorePlugin.present(player)) plugin.messages().send(player, "storage-error");
                return;
            }
            if (current == null || !current.token.equals(token) || !NekoCorePlugin.present(player)) return;
            current.shownExp = result.exp(); current.shownCoins = result.coins();
            current.rewardTitleUntil = nanoClock.getAsLong() + 1_250_000_000L;
            if (result.coins() > 0) coinMessage(player, result.coins(), result.profile().coins());
            refreshTitle(player, current, nanoClock.getAsLong());
        }));
    }

    private void coinMessage(Player player, long coins, long balance) {
        List<String> templates = plugin.messages().lines("afk-pool.coin-messages");
        if (templates.isEmpty()) return;
        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        player.sendMessage(Messages.text(template, Map.of("coins", "" + coins, "balance", String.format(Locale.ROOT, "%,d", balance))));
    }

    private void refreshTitle(Player player, Session session, long now) {
        if (!plugin.settings().afkPool().title().enabled() || session.inventoryOpen
                || now < titleSuppressedUntil.getOrDefault(player.getUniqueId(), Long.MIN_VALUE)) return;
        Map<String, String> vars;
        String subtitle;
        if (now < session.rewardTitleUntil) {
            vars = Map.of("exp", "" + session.shownExp, "coins", "" + session.shownCoins);
            subtitle = plugin.messages().raw(session.shownCoins > 0 ? "afk-pool.reward-exp-coins" : "afk-pool.reward-exp");
        } else {
            long remaining = Math.max(0, (session.nextReward - now + 999_999_999L) / 1_000_000_000L);
            vars = Map.of("time", "%02d:%02d".formatted(remaining / 60, remaining % 60));
            subtitle = plugin.messages().raw("afk-pool.subtitle");
        }
        var titleConfig = plugin.settings().afkPool().title();
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofMillis(titleConfig.stayTicks() * 50L),
                Duration.ofMillis(titleConfig.fadeOutTicks() * 50L));
        player.showTitle(Title.title(Messages.text(plugin.messages().raw("afk-pool.title"), Map.of()),
                Messages.text(subtitle, vars), times));
    }

    @EventHandler public void inventoryOpen(InventoryOpenEvent event) {
        if (!plugin.settings().afkPool().title().hideWhileInventoryOpen()) return;
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) { session.inventoryOpen = true; session.inventoryGeneration++; }
    }

    @EventHandler public void inventoryClose(InventoryCloseEvent event) {
        if (!plugin.settings().afkPool().title().hideWhileInventoryOpen()) return;
        UUID id = event.getPlayer().getUniqueId();
        Session atClose = sessions.get(id); long generation = atClose == null ? -1 : atClose.inventoryGeneration;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Session session = sessions.get(id);
            if (session != null && session.inventoryGeneration == generation) session.inventoryOpen = false;
        });
    }

    public void suppressTitle(UUID player, long ticks) { titleSuppressedUntil.put(player, nanoClock.getAsLong() + ticks * 50_000_000L); }
    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); titleSuppressedUntil.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { finish(event.getPlayer(), nanoClock.getAsLong()); }
    @EventHandler public void death(PlayerDeathEvent event) { finish(event.getEntity(), nanoClock.getAsLong()); }

    private void finish(Player player, long now) {
        Session ended = sessions.remove(player.getUniqueId());
        if (ended == null || !player.isOnline() || !plugin.settings().afkPool().endMessageEnabled()) return;
        plugin.messages().send(player, "afk-pool.end-message",
                Map.of("duration", duration(Math.max(0, now - ended.startedAt) / 1_000_000_000L)));
    }

    static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        if (seconds < 60) return seconds + "秒";
        if (seconds < 3600) return seconds / 60 + "分" + seconds % 60 + "秒";
        return "%d小时%02d分%02d秒".formatted(seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    public boolean active(UUID player) { return sessions.containsKey(player); }
    public int sessionCount() { return sessions.size(); }
    public void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
        sessions.clear(); // Deliberately no clearTitle(): the short client title fades naturally.
        titleSuppressedUntil.clear();
    }
    private static long seconds(long value) { return Math.multiplyExact(value, 1_000_000_000L); }
}
