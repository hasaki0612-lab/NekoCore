package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import org.bukkit.Location;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.Map;

public final class DeathLocationListener implements Listener {
    private final NekoCorePlugin plugin;
    public DeathLocationListener(NekoCorePlugin plugin) { this.plugin = plugin; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        Location at = player.getLocation();
        Map<String, String> vars = Map.of("world", player.getWorld().getName(), "x", "" + at.getBlockX(),
                "y", "" + at.getBlockY(), "z", "" + at.getBlockZ());
        // Captured at death, sent one main tick later after vanilla's death message; never broadcast coordinates.
        plugin.onMain(() -> { if (NekoCorePlugin.present(player)) plugin.messages().send(player, "death-location", vars); });
    }
}
