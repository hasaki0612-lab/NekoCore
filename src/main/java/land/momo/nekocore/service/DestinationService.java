package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import java.util.*;
import java.util.logging.Level;

public final class DestinationService {
    private final NekoCorePlugin plugin;
    private final Map<UUID, UUID> pending = new HashMap<>();
    public DestinationService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public void survival(Player player, boolean second) {
        var settings = plugin.settings();
        if (second ? !settings.features().secondWorldEnabled() : !settings.features().survivalEnabled()) {
            plugin.messages().send(player, "feature-disabled"); return;
        }
        String name = second ? settings.features().newWorld() : settings.survivalWorld();
        String template = second ? settings.features().newCommand() : settings.survivalCommand();
        World world = Bukkit.getWorld(name);
        if (world == null) { plugin.messages().send(player, "survival-unavailable"); return; }
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            Location spawn = world.getSpawnLocation();
            teleport(player, new land.momo.nekocore.config.Settings.Destination(name, spawn.getX(), spawn.getY(),
                    spawn.getZ(), spawn.getYaw(), spawn.getPitch()), "destination-arrived", "生存世界入口传送失败");
            return;
        }
        try {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), template.replace("{player}", player.getName()).replace("{world}", name)))
                plugin.messages().send(player, "survival-unavailable");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Multiverse 生存入口命令失败", e);
            plugin.messages().send(player, "survival-unavailable");
        }
    }
    public void minigames(Player player) {
        if (!plugin.settings().features().minigamesEnabled()) { plugin.messages().send(player, "feature-disabled"); return; }
        teleport(player, plugin.settings().features().minigames(), "destination-arrived", "小游戏入口传送失败");
    }
    public void afkPool(Player player) {
        if (!plugin.settings().afkPool().enabled()) { plugin.messages().send(player, "feature-disabled"); return; }
        teleport(player, plugin.settings().afkPool().teleport(), "afk-pool-arrived", "挂机池入口传送失败");
    }
    private void teleport(Player player, land.momo.nekocore.config.Settings.Destination destination,
                          String successMessage, String logMessage) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) { plugin.messages().send(player, "destination-busy"); return; }
        World world = Bukkit.getWorld(destination.world());
        Location at = new Location(world, destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch());
        if (world == null || !valid(world, at)) { plugin.messages().send(player, "destination-unavailable"); return; }
        World from = player.getWorld();
        UUID token = UUID.randomUUID(); pending.put(id, token);
        try {
            world.getChunkAtAsync(at).whenComplete((chunk, error) -> plugin.onMain(() -> {
                if (!pending.remove(id, token) || !NekoCorePlugin.present(player)) return;
                if (error != null) { fail(player, error, logMessage); return; }
                if (player.getWorld() != from) { plugin.messages().send(player, "destination-moved"); return; }
                if (Bukkit.getWorld(destination.world()) != world || !valid(world, at)) {
                    plugin.messages().send(player, "destination-unavailable"); return;
                }
                if (!plugin.permission(player, "nekocore.menu")) return;
                try {
                    boolean success = !player.isDead() && player.teleport(at, TeleportCause.PLUGIN);
                    plugin.messages().send(player, success ? successMessage : "home-failed");
                } catch (RuntimeException e) { fail(player, e, logMessage); }
            }));
        } catch (RuntimeException e) { pending.remove(id, token); fail(player, e, logMessage); }
    }
    public void cancel(UUID id) { pending.remove(id); }
    private boolean valid(World world, Location at) {
        return at.getY() >= world.getMinHeight() && at.getY() < world.getMaxHeight() && world.getWorldBorder().isInside(at);
    }
    private void fail(Player player, Throwable error, String logMessage) {
        plugin.getLogger().log(Level.WARNING, logMessage, NekoCorePlugin.unwrap(error));
        plugin.messages().send(player, "destination-unavailable");
    }
}
