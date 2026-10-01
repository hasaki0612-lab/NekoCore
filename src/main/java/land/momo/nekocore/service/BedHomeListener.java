package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.model.Home;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerBedEnterEvent;
import java.util.Map;
import java.util.logging.Level;

/** Observes successful sleeping only; never changes the bed event, respawn point or night. */
public final class BedHomeListener implements Listener {
    private final NekoCorePlugin plugin;
    public BedHomeListener(NekoCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!allowed(player, world)) return;
        Location clicked = event.getBed().getLocation();
        // The event precedes sleeping. Next tick confirms actual sleep after ALL listeners ran.
        // Bukkit cancels this one-shot task on plugin disable; no repeating bed scheduler exists.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !NekoCorePlugin.present(player) || player.isDead()
                    || player.getWorld() != world || Bukkit.getWorld(world.getUID()) != world
                    || !allowed(player, world) || !player.isSleeping()) return;
            Location bed = player.getBedLocation();
            if (bed.getWorld() != world || clicked.getWorld() != world || bed.distanceSquared(clicked) > 1.01) return;
            if (!plugin.available(player)) return;
            var safe = SafeBedLocation.find(bed, player.getLocation().getYaw());
            if (safe.isEmpty()) { plugin.messages().send(player, "home-bed-unsafe"); return; }
            Location at = safe.get();
            Home home = new Home(player.getUniqueId(), world.getUID(), world.getName(),
                    at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
            // Same UUID-keyed UPSERT used by /sethome. JDBC work remains off-thread.
            plugin.store().saveHome(home).whenComplete((ignored, error) -> plugin.onMain(() -> {
                if (error != null) plugin.getLogger().log(Level.WARNING, "保存床 Home 失败", NekoCorePlugin.unwrap(error));
                if (!NekoCorePlugin.present(player)) return;
                if (error != null) { plugin.messages().send(player, "home-bed-failed"); return; }
                plugin.messages().send(player, "home-bed-saved", Map.of("world", home.worldName(),
                        "x", "" + at.getBlockX(), "y", "" + at.getBlockY(), "z", "" + at.getBlockZ()));
            }));
        });
    }

    private boolean allowed(Player player, World world) {
        var settings = plugin.settings().bedAutoSet();
        return settings.enabled() && settings.worlds().contains(world.getName()) && player.hasPermission("nekocore.home.set");
    }
}
