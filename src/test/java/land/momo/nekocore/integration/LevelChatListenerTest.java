package land.momo.nekocore.integration;

import io.papermc.paper.chat.ChatRenderer;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelChatListenerTest {
    @Test void placesLevelBeforeNameAndPreservesExistingRendererAndMessage() {
        Player player = mock(Player.class); Audience viewer = mock(Audience.class);
        Component body = Component.text("hello &a literal text");
        ChatRenderer existing = (source, name, message, audience) -> {
            assertSame(player, source); assertSame(viewer, audience); assertSame(body, message);
            return Component.text("[本地频道] ").append(name).append(Component.text(" » ")).append(message);
        };
        var wrapped = LevelChatListener.decorate(existing, Messages.text("&#9FD9F6[&fLv.{level}&#9FD9F6] ", Map.of("level", "3")));
        var output = wrapped.render(player, Component.text("羽咲"), body, viewer);
        assertEquals("[本地频道] [Lv.3] 羽咲 » hello &a literal text", PlainTextComponentSerializer.plainText().serialize(output));
        verifyNoInteractions(player, viewer);
    }
    @Test void unifiedTitleReplacesLevelAndFutureLocationStaysOnlyInChatComposition() {
        Player player=mock(Player.class); Audience viewer=mock(Audience.class);
        ChatRenderer original=(source,name,message,audience)->name.append(Component.text(" » ")).append(message);
        for(String primary:new String[]{"[Lv.18] ","[Sora] ","[Lv.18] "}) {
            Component chatPrefix=Component.text("[JP] ").append(Component.text(primary));
            var rendered=LevelChatListener.decorate(original,chatPrefix).render(player,Component.text("羽咲"),Component.text("你好"),viewer);
            String plain=PlainTextComponentSerializer.plainText().serialize(rendered);
            assertEquals("[JP] "+primary+"羽咲 » 你好",plain);
            assertFalse(plain.contains("[Lv.18] [Sora]"));
        }
    }
}
