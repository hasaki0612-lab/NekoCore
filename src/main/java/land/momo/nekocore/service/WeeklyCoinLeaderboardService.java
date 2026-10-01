package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.WeeklyCoinRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

public final class WeeklyCoinLeaderboardService {
    static final String HOLOGRAM_ID = "weekly-coins";
    private final NekoCorePlugin plugin;
    private final WeeklyCoinRepository repository;
    private BukkitTask task;
    private long generation;
    private boolean warned;
    public WeeklyCoinLeaderboardService(NekoCorePlugin plugin, WeeklyCoinRepository repository) {
        this.plugin = plugin; this.repository = repository;
    }
    public void restart() {
        stop(); var config = plugin.settings().weeklyLeaderboard();
        if (!config.enabled()) return;
        if (!config.positionConfigured()) {
            warn("金币周榜坐标尚未配置，周收入会继续统计，但不会生成 Hologram。"); return;
        }
        if (Bukkit.getWorld(config.world()) == null) { warn("金币周榜世界尚未加载：" + config.world()); return; }
        refresh(); long token = generation;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (token == generation) refresh(); },
                config.refreshSeconds() * 20L, config.refreshSeconds() * 20L);
    }
    private void warn(String message) { if (!warned) { warned = true; plugin.getLogger().warning(message); } }
    void refresh() {
        long token = generation;
        repository.current().whenComplete((board, error) -> plugin.onMain(() -> {
            if (token != generation) return;
            if (error != null) { plugin.getLogger().log(Level.SEVERE, "金币周榜读取失败", NekoCorePlugin.unwrap(error)); return; }
            render(board);
        }));
    }
    void render(WeeklyCoinRepository.Board board) {
        var config = plugin.settings().weeklyLeaderboard(); World world = Bukkit.getWorld(config.world());
        if (!config.enabled() || !config.positionConfigured() || world == null) return;
        Component text = Messages.text(plugin.messages().raw("weekly-coins.title"), Map.of());
        if (board.entries().isEmpty()) text = text.append(Component.newline()).append(Messages.text(plugin.messages().raw("weekly-coins.empty"), Map.of()));
        for (int index = 0; index < board.entries().size(); index++) {
            var entry = board.entries().get(index); int rank = index + 1;
            String key = rank == 1 ? "weekly-coins.first" : rank == 2 ? "weekly-coins.second" : rank == 3 ? "weekly-coins.third" : "weekly-coins.line";
            text = text.append(Component.newline()).append(Messages.text(plugin.messages().raw(key), Map.of(
                    "rank", "" + rank, "player", entry.name(), "coins", String.format(Locale.ROOT, "%,d", entry.earned()))));
        }
        plugin.holograms().show(HOLOGRAM_ID, new Location(world, config.x(), config.y(), config.z(), config.yaw(), 0), text);
    }
    public void stop() {
        generation++; if (task != null) { task.cancel(); task = null; }
        if (plugin.holograms() != null) plugin.holograms().remove(HOLOGRAM_ID);
    }
}
