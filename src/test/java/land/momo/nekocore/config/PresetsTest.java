package land.momo.nekocore.config;

import land.momo.nekocore.service.EnchantmentService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PresetsTest {
    @TempDir Path directory;
    String resource(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/" + name)) { assertNotNull(input); return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    @Test void defaultsAndEveryPresetPassTheSharedCompleteValidator() throws Exception {
        Files.writeString(directory.resolve("messages.yml"), resource("messages.yml"));
        try (var registry = new PaperRegistryFixture(); var books = mockStatic(EnchantmentService.class)) {
            books.when(() -> EnchantmentService.validatePool(any())).thenReturn(List.of());
            List<String> sources = new ArrayList<>(List.of("config.yml"));
            FirstRunGuide.PRESETS.forEach(name -> sources.add("presets/" + name));
            for (String source : sources) {
                Files.writeString(directory.resolve("config.yml"), resource(source));
                var loaded = ConfigurationValidation.load(directory.resolve("config.yml").toFile(), directory.resolve("messages.yml").toFile(),
                        getClass().getResourceAsStream("/messages.yml"), ignored -> fail("Unexpected warning"));
                assertTrue(loaded.settings().menu().enabled()); assertTrue(loaded.settings().features().survivalEnabled());
                assertTrue(loaded.features().shop().enabled()); assertTrue(loaded.features().bag().enabled());
                assertTrue(loaded.features().tpn().enabled()); assertTrue(loaded.dailyTasks().enabled());
                assertEquals(109, loaded.features().shop().products().size());
                assertEquals(List.of("mame","momo","sora"), loaded.features().levelShop().titles().stream().map(FeatureSettings.Title::id).toList());
                assertFalse(loaded.settings().afkPool().enabled()); assertFalse(loaded.settings().weeklyLeaderboard().enabled());
                assertFalse(loaded.settings().weeklyLeaderboard().positionConfigured());
                assertFalse(loaded.settings().mascot().enabled()); assertFalse(loaded.settings().locationPrefix().enabled());
            }
        }
    }
    @Test void fullKeySetsAndAllNonPresetValuesTrackCanonicalDefaults() throws Exception {
        var canonical = new YamlConfiguration(); canonical.loadFromString(resource("config.yml"));
        Set<String> intentional = Set.of("branding.server-name", "survival.world", "survival-new.enabled", "survival-new.world",
                "home.bed-auto-set.worlds","levelshop.worlds","bag.writable-worlds","gui.items.survival.name",
                "gui.items.survival.lore","gui.items.survival-new.name","gui.items.survival-new.lore");
        for (String name : FirstRunGuide.PRESETS) {
            Set<String> allowed = name.equals("lobby-survival.yml") ? intentional
                    : name.equals("friends-server.yml") ? Set.of("branding.server-name") : Set.of();
            var preset = new YamlConfiguration(); preset.loadFromString(resource("presets/" + name));
            assertEquals(canonical.getKeys(true),preset.getKeys(true),name + " complete sections/keys");
            assertEquals(canonical.getInt("config-version"),preset.getInt("config-version"));
            assertTrue(preset.getBoolean("gui.enabled")); assertTrue(preset.getBoolean("tab.enabled"));
            assertTrue(preset.getBoolean("tips.enabled")); assertTrue(preset.getBoolean("cleanup.enabled"));
            assertFalse(preset.getBoolean("afk-pool.position-configured")); assertFalse(preset.getBoolean("minigames.enabled"));
            for (String key : canonical.getKeys(true)) if (!canonical.isConfigurationSection(key) && !allowed.contains(key))
                assertEquals(canonical.get(key),preset.get(key),name + ":" + key);
            assertEquals(name.equals("lobby-survival.yml"),preset.getBoolean("survival-new.enabled"));
        }
    }
    @Test void missingPresetsAreCopiedAndExistingAdministratorFileIsNeverOverwritten() throws Exception {
        FirstRunGuide.installPresets(directory, name -> getClass().getResourceAsStream("/" + name));
        Path customised = directory.resolve("presets/survival-only.yml");
        Files.writeString(customised,"# admin custom CRLF\r\ncustom: retained\r\n");
        byte[] before = Files.readAllBytes(customised);
        FirstRunGuide.installPresets(directory, name -> getClass().getResourceAsStream("/" + name));
        assertArrayEquals(before,Files.readAllBytes(customised));
        for (String name : FirstRunGuide.PRESETS) assertTrue(Files.exists(directory.resolve("presets").resolve(name)));
        assertFalse(Files.exists(directory.resolve("nekocore.db")));
    }
    @Test void onboardingOnlyRunsWhenDefaultConfigWasJustGenerated() {
        List<String> lines = new ArrayList<>(); FirstRunGuide.show(true,lines::add);
        assertEquals(5,lines.size()); assertTrue(lines.stream().anyMatch(line -> line.contains("/menu")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("branding.server-name")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("survival.world")));
        FirstRunGuide.show(false,lines::add); assertEquals(5,lines.size());
    }
}
