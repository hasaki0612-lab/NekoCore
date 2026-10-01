package land.momo.nekocore.config;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {
    @Test void hexColorsAreActualComponentsAndPrefixExportsLegacyColors() {
        var component = Messages.text("&#9FD9F6NekoCore", Map.of());
        assertEquals(TextColor.fromHexString("#9FD9F6"), component.color());
        assertEquals("NekoCore", PlainTextComponentSerializer.plainText().serialize(component));
        String prefix = Messages.legacy("&#9FD9F6[&fLv.{level}&#9FD9F6] &r", Map.of("level", "3"));
        assertTrue(prefix.contains("Lv.3")); assertTrue(prefix.contains("§x")); assertFalse(prefix.contains("&#"));
    }
    @Test void defaultMessagesAndMenusUseSoftHexAccentsNotHarshLegacyPurpleGreenOrRed() throws Exception {
        for (String resource : new String[]{"/config.yml", "/messages.yml"}) {
            try (var input = getClass().getResourceAsStream(resource)) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(java.util.regex.Pattern.compile("(?i)&[ad25c4]").matcher(text).find(), resource);
            }
        }
    }
}
