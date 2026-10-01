package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;

public final class JoinWelcomeService {
    private final NekoCorePlugin plugin;
    public JoinWelcomeService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public void show(Player player) {
        var config = plugin.settings().welcomeTitle(); if (!config.enabled()) return;
        if (plugin.afkPool() != null) plugin.afkPool().suppressTitle(player.getUniqueId(),
                (long) config.delayTicks() + config.fadeInTicks() + config.stayTicks() + config.fadeOutTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!NekoCorePlugin.present(player) || plugin.data().view(player.getUniqueId()) == null) return;
            var times = Title.Times.times(Duration.ofMillis(config.fadeInTicks() * 50L),
                    Duration.ofMillis(config.stayTicks() * 50L), Duration.ofMillis(config.fadeOutTicks() * 50L));
            player.showTitle(Title.title(Messages.text(plugin.messages().raw("welcome-title.title"), Map.of("player", player.getName())),
                    Messages.text(plugin.messages().raw("welcome-title.subtitle"), Map.of("player", player.getName())), times));
        }, config.delayTicks());
    }
}
