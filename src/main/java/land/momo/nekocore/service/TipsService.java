package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.Objects;

public final class TipsService {
    private final NekoCorePlugin plugin;
    private BukkitTask task;
    private int index;
    public TipsService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public void restart() {
        stop(); index = 0;
        var settings = plugin.settings().features().tips();
        if (settings.enabled() && !settings.messages().isEmpty())
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, settings.interval() * 20L, settings.interval() * 20L);
    }
    private void broadcastNext() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        var settings = plugin.settings().features().tips();
        var text = settings.messages().get(index);
        index = (index + 1) % settings.messages().size();
        Bukkit.broadcast(Messages.text(settings.prefix() + text,
                Map.of("server", Objects.toString(plugin.settings().serverName(), "My Server"))));
    }
    public void stop() { if (task != null) { task.cancel(); task = null; } }
}
