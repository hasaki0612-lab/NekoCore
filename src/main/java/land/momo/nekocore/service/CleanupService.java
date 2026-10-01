package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class CleanupService {
    private final NekoCorePlugin plugin;
    private BukkitTask task;
    private CleanupClock clock;
    public CleanupService(NekoCorePlugin plugin) { this.plugin = plugin; }

    public void restart() {
        stop();
        Settings.Cleanup settings = plugin.settings().cleanup();
        clock = new CleanupClock(settings.interval());
        if (settings.enabled()) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        int remaining = clock.tick();
        if (remaining <= 0) { cleanNow(); return; }
        if (!CleanupClock.warning(remaining)) return;
        boolean countdown = remaining <= 5;
        Bukkit.broadcast(plugin.messages().component(countdown ? "cleanup-countdown" : "cleanup-warning", Map.of("seconds", "" + remaining)));
        Settings.Cleanup settings = plugin.settings().cleanup();
        if (countdown && settings.soundEnabled()) Bukkit.getOnlinePlayers().forEach(player ->
                player.playSound(player.getLocation(), settings.sound(), SoundCategory.MASTER, settings.volume(), settings.pitch()));
    }

    public int cleanNow() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) count += removeItems(world);
        // Manual cleanup starts a fresh cycle too; no orphan countdown is left behind.
        if (clock != null) clock.reset();
        Bukkit.broadcast(plugin.messages().component("cleanup-done", Map.of("count", "" + count)));
        return count;
    }

    public static int removeItems(World world) {
        int count = 0;
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.isValid() && !item.isDead()) { item.remove(); count++; }
        }
        return count;
    }

    public void stop() { if (task != null) { task.cancel(); task = null; } }
}
