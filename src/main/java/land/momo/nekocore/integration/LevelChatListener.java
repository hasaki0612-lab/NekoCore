package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import io.papermc.paper.event.player.ChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.event.*;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class LevelChatListener implements Listener {
    private final NekoCorePlugin plugin;
    public LevelChatListener(NekoCorePlugin plugin) { this.plugin = plugin; }
    // Paper's synchronous companion event keeps Bukkit/cache access on the main thread.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(ChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.prefixes().coloredChat(event.getPlayer()) && plain.matches("(?is).*&(?:[0-9a-fr]|#[0-9a-f]{6}).*"))
            event.message(PrefixService.safeChatColors(plain));
        var settings = plugin.settings();
        if (!settings.features().chat().enabled()) return;
        var profile = plugin.data().view(event.getPlayer().getUniqueId());
        if (profile == null) return;
        Component prefix = plugin.prefixes().chat(event.getPlayer().getUniqueId());
        event.renderer(decorate(event.renderer(), prefix));
    }
    public static ChatRenderer decorate(ChatRenderer previous, Component prefix) {
        // Preserve other renderers, per-viewer logic, original message component and event recipients.
        return (source, name, message, viewer) -> previous.render(source, prefix.append(name), message, viewer);
    }
}
