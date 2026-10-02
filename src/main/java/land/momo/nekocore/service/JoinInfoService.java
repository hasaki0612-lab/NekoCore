package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Personal join chat, using the already prepared profile cache and URL-only buttons. */
public final class JoinInfoService {
    private final NekoCorePlugin plugin;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, UUID> requests = new HashMap<>();
    private long generation;
    public JoinInfoService(NekoCorePlugin plugin) { this.plugin = plugin; }

    public void show(Player player) {
        quit(player.getUniqueId());
        var config = plugin.settings().joinInfo();
        if (!config.enabled()) return;
        long token = generation;
        UUID request = UUID.randomUUID();
        requests.put(player.getUniqueId(), request);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!requests.remove(player.getUniqueId(), request)) return;
            pending.remove(player.getUniqueId());
            if (token == generation && player.isOnline()) send(player);
        }, config.delayTicks());
        pending.put(player.getUniqueId(), task);
    }

    void send(Player player) {
        var config = plugin.settings().joinInfo();
        if (!config.enabled() || !player.isOnline()) return;
        var profile = plugin.data().view(player.getUniqueId());
        if (profile == null) return;
        Map<String, String> vars = plugin.variables(profile);
        Component block = Component.empty();
        boolean first = true;
        for (String line : plugin.messages().lines("join-info.lines")) {
            if (!first) block = block.append(Component.newline());
            block = block.append(Messages.text(line, vars)); first = false;
        }
        Component links = Component.empty();
        boolean hasLinks = false;
        for (var entry : config.links()) {
            if (entry.url().isEmpty()) continue;
            if (hasLinks) links = links.append(Component.text("  "));
            links = links.append(Messages.text(plugin.messages().raw("join-info.buttons." + entry.id()), vars)
                    .clickEvent(ClickEvent.openUrl(entry.url()))
                    .hoverEvent(HoverEvent.showText(Messages.text(plugin.messages().raw("join-info.link-hover"), vars))));
            hasLinks = true;
        }
        if (hasLinks) block = block.append(first ? Component.empty() : Component.newline()).append(links);
        if (!first || hasLinks) player.sendMessage(block);
    }

    public void quit(UUID player) {
        requests.remove(player);
        BukkitTask task = pending.remove(player);
        if (task != null) task.cancel();
    }
    public void stop() {
        generation++;
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        requests.clear();
    }
}
