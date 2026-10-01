package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One request involving each player at a time, with a bounded task, never a per-tick player scan. */
public final class TeleportRequestService implements Listener {
    private static final class Request {
        final Player from, to;
        BukkitTask timeout;
        boolean accepting;
        Request(Player from, Player to) { this.from = from; this.to = to; }
    }
    private final NekoCorePlugin plugin;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    public TeleportRequestService(NekoCorePlugin plugin) { this.plugin = plugin; }
    private boolean allowed(Player player) {
        if (!plugin.featuresAvailable(player) || !plugin.permission(player, "nekocore.tpn")) return false;
        if (!plugin.features().tpn().enabled()) { plugin.messages().send(player, "feature-disabled"); return false; }
        return plugin.exchanges().idle(player);
    }
    public void request(Player from, String name) {
        if (!allowed(from)) return;
        Player to = Bukkit.getPlayerExact(name);
        if (to == null || !NekoCorePlugin.present(to) || !from.canSee(to)) { plugin.messages().send(from, "tpn-offline"); return; }
        if (to.getUniqueId().equals(from.getUniqueId())) { plugin.messages().send(from, "tpn-self"); return; }
        if (!to.hasPermission("nekocore.tpn")) { plugin.messages().send(from, "tpn-unavailable"); return; }
        if (requests.containsKey(from.getUniqueId()) || requests.containsKey(to.getUniqueId())) { plugin.messages().send(from, "tpn-pending"); return; }
        long remaining = remaining(from.getUniqueId());
        if (remaining > 0) { plugin.messages().send(from, "tpn-cooldown", Map.of("seconds", "" + remaining)); return; }
        Request request = new Request(from, to);
        requests.put(from.getUniqueId(), request); requests.put(to.getUniqueId(), request);
        request.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> cancel(request, "tpn-expired"), plugin.features().tpn().timeout() * 20L);
        Map<String,String> vars = vars(request);
        plugin.messages().send(from, "tpn-sent", vars); plugin.messages().send(to, "tpn-received", vars);
    }
    public void answer(Player target, boolean accept) {
        if (!allowed(target)) return;
        Request request = requests.get(target.getUniqueId());
        if (request == null || request.to != target || request.accepting) { plugin.messages().send(target, "tpn-no-request"); return; }
        if (!accept) { cancel(request, "tpn-declined"); return; }
        if (!NekoCorePlugin.present(request.from) || !request.from.hasPermission("nekocore.tpn") || request.from.isDead() || target.isDead()) {
            cancel(request, "tpn-unavailable"); return;
        }
        if (!plugin.exchanges().idle(request.from) || remaining(request.from.getUniqueId()) > 0) { cancel(request, "tpn-unavailable"); return; }
        request.accepting = true; request.timeout.cancel();
        request.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> cancel(request, "tpn-load-timeout"), 600L);
        Location destination = target.getLocation().clone();
        World world = destination.getWorld(); UUID sourceWorld = request.from.getWorld().getUID();
        plugin.messages().send(target, "tpn-accepted", vars(request));
        plugin.messages().send(request.from, "tpn-loading", vars(request));
        List<CompletableFuture<?>> chunks = new ArrayList<>();
        try {
            for (int x = (destination.getBlockX() - 4) >> 4; x <= (destination.getBlockX() + 4) >> 4; x++)
                for (int z = (destination.getBlockZ() - 4) >> 4; z <= (destination.getBlockZ() + 4) >> 4; z++)
                    chunks.add(world.getChunkAtAsync(x, z));
        } catch (RuntimeException e) { cancel(request, "tpn-unsafe"); return; }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> plugin.onMain(() -> {
            if (!current(request)) return;
            if (error != null || !NekoCorePlugin.present(request.from) || !NekoCorePlugin.present(target)
                    || request.from.isDead() || target.isDead() || Bukkit.getWorld(world.getUID()) != world
                    || !request.from.getWorld().getUID().equals(sourceWorld)
                    || !request.from.hasPermission("nekocore.tpn") || !target.hasPermission("nekocore.tpn")
                    || plugin.exchanges().busy(request.from.getUniqueId()) || plugin.exchanges().busy(target.getUniqueId())) {
                cancel(request, "tpn-unavailable"); return;
            }
            Optional<Location> safe = SafeBedLocation.find(destination, destination.getYaw());
            if (safe.isEmpty()) { cancel(request, "tpn-unsafe"); return; }
            // Capture was made when /yes was received. Later target movement is not followed.
            try {
                if (!request.from.teleport(safe.get(), PlayerTeleportEvent.TeleportCause.PLUGIN)) { cancel(request, "tpn-cancelled"); return; }
            } catch (RuntimeException e) { cancel(request, "tpn-cancelled"); return; }
            finish(request);
            startCooldown(request.from);
            plugin.messages().send(request.from, "tpn-success-from", vars(request));
            plugin.messages().send(target, "tpn-success-to", vars(request));
            var config = plugin.features().tpn();
            for (Player player : List.of(request.from, target)) player.playSound(player.getLocation(), config.sound(), SoundCategory.MASTER, config.volume(), config.pitch());
            Location point = safe.get().clone().add(0, 1, 0);
            if (config.particles() > 0) for (Player viewer : world.getPlayers())
                if (viewer.getLocation().distanceSquared(point) <= 32 * 32)
                    viewer.spawnParticle(Particle.END_ROD, point.getX(), point.getY(), point.getZ(), config.particles(), 0.35, 0.4, 0.35, 0.015, null, true);
        }));
    }
    private Map<String,String> vars(Request r) { return Map.of("player", r.from.getName(), "target", r.to.getName(), "seconds", "" + plugin.features().tpn().timeout()); }
    private boolean current(Request r) { return requests.get(r.from.getUniqueId()) == r && requests.get(r.to.getUniqueId()) == r; }
    private void finish(Request r) {
        requests.remove(r.from.getUniqueId(), r); requests.remove(r.to.getUniqueId(), r);
        if (r.timeout != null) r.timeout.cancel();
    }
    private void cancel(Request r, String message) {
        if (!current(r)) return; finish(r);
        if (NekoCorePlugin.present(r.from)) plugin.messages().send(r.from, message, vars(r));
        if (NekoCorePlugin.present(r.to)) plugin.messages().send(r.to, message, vars(r));
    }
    private long remaining(UUID player) {
        Long until = cooldowns.get(player);
        if (until == null) return 0;
        long nanos = until - System.nanoTime();
        if (nanos <= 0) { cooldowns.remove(player); return 0; }
        return (nanos + 999_999_999L) / 1_000_000_000L;
    }
    private void startCooldown(Player p) {
        int seconds = plugin.prefixes().cooldown(p); if (seconds == 0) return;
        long until = System.nanoTime() + seconds * 1_000_000_000L;
        UUID id = p.getUniqueId(); cooldowns.put(id, until);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cooldowns.remove(id, until), seconds * 20L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        Request request = requests.get(event.getPlayer().getUniqueId()); if (request != null) cancel(request, "tpn-player-left");
    }
    public void stop() { for (Request request : new HashSet<>(requests.values())) cancel(request, "tpn-reloaded"); }
}
