package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.LongSupplier;

/** A single scheduled task owns the configured TAB surface; it never performs JDBC work. */
public final class TabPanelService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private record Ownership(Component previousName, Component previousHeader, Component previousFooter,
                             Component lastName, Component lastHeader, Component lastFooter) {}
    private final NekoCorePlugin plugin;
    private final Clock clock;
    private final LongSupplier nanoClock;
    private final Map<UUID,Ownership> owned = new HashMap<>();
    private long startedNanos;
    private BukkitTask timer;

    public TabPanelService(NekoCorePlugin plugin) { this(plugin, Clock.systemUTC(), System::nanoTime); }
    TabPanelService(NekoCorePlugin plugin, Clock clock, LongSupplier nanoClock) {
        this.plugin = plugin; this.clock = clock; this.nanoClock = nanoClock; this.startedNanos = nanoClock.getAsLong();
    }
    public void restart() {
        // A config reload restarts rendering, not the server. Keep the original
        // monotonic origin so the advertised uptime does not jump back to zero.
        stop();
        if (!plugin.settings().tab().enabled()) return;
        update();
        long ticks = plugin.settings().tab().refreshSeconds() * 20L;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::update, ticks, ticks);
    }
    public void update() {
        if (!plugin.settings().tab().enabled()) return;
        double[] tps = Bukkit.getTPS(); int online = Bukkit.getOnlinePlayers().size();
        String time = TIME.format(Instant.now(clock).atZone(BEIJING)); String uptime = uptime(nanoClock.getAsLong() - startedNanos);
        for (Player player : Bukkit.getOnlinePlayers()) update(player, tps, online, time, uptime);
    }
    public void update(Player player) {
        if (!plugin.settings().tab().enabled() || player == null) return;
        update(player, Bukkit.getTPS(), Bukkit.getOnlinePlayers().size(), TIME.format(Instant.now(clock).atZone(BEIJING)),
                uptime(nanoClock.getAsLong() - startedNanos));
    }
    private void update(Player player, double[] tps, int online, String time, String uptime) {
        var profile = plugin.data().view(player.getUniqueId()); if (profile == null) return;
        var location = player.getLocation();
        Map<String,String> vars = new HashMap<>();
        vars.put("x", "" + location.getBlockX()); vars.put("y", "" + location.getBlockY()); vars.put("z", "" + location.getBlockZ());
        vars.put("tps_1m", tps(tps, 0)); vars.put("tps_5m", tps(tps, 1)); vars.put("tps_15m", tps(tps, 2));
        vars.put("online", "" + online); vars.put("max_players", "" + Bukkit.getMaxPlayers());
        vars.put("coins", String.format(Locale.ROOT, "%,d", profile.coins())); vars.put("time", time); vars.put("uptime", uptime);
        Component header = Messages.text(plugin.messages().raw("tab.header"), vars);
        Component footer = Messages.text(plugin.messages().raw("tab.footer"), vars);
        // TAB and chat deliberately compose the same two independent identity
        // components. NameTagService still consumes primary() only.
        Component locationPrefix = plugin.settings().tab().showLocationPrefix()
                ? plugin.prefixes().chatLocation(player.getUniqueId()) : Component.empty();
        Component name = locationPrefix.append(plugin.prefixes().primary(player.getUniqueId())).append(Component.text(player.getName()));
        Ownership first = owned.get(player.getUniqueId());
        Component previousName = first == null ? player.playerListName() : first.previousName();
        Component previousHeader = first == null ? player.playerListHeader() : first.previousHeader();
        Component previousFooter = first == null ? player.playerListFooter() : first.previousFooter();
        player.playerListName(name); player.sendPlayerListHeaderAndFooter(header, footer);
        owned.put(player.getUniqueId(), new Ownership(previousName, previousHeader, previousFooter, name, header, footer));
    }
    public void quit(UUID player) { owned.remove(player); }
    public void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
        for (var entry : new ArrayList<>(owned.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey()); Ownership state = entry.getValue();
            if (player == null) continue;
            if (Objects.equals(player.playerListName(), state.lastName())) player.playerListName(state.previousName());
            if (Objects.equals(player.playerListHeader(), state.lastHeader()) && Objects.equals(player.playerListFooter(), state.lastFooter()))
                player.sendPlayerListHeaderAndFooter(orEmpty(state.previousHeader()), orEmpty(state.previousFooter()));
        }
        owned.clear();
    }
    private static Component orEmpty(Component component) { return component == null ? Component.empty() : component; }
    private static String tps(double[] values, int index) {
        double value = values.length > index ? Math.min(20, values[index]) : 20;
        String color = value >= 18 ? "&#B6E3D0" : value >= 15 ? "&#FFE49A" : "&#EF9A9A";
        return color + String.format(Locale.ROOT, "%.1f", value);
    }
    static String uptime(long nanos) {
        long seconds = Math.max(0, nanos / 1_000_000_000L), days = seconds / 86400;
        long hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        return (days > 0 ? days + "d " : "") + hours + "h " + minutes + "m";
    }
}
