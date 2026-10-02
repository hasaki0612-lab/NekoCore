package land.momo.nekocore.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettingsTest {
    @TempDir Path directory;
    @Test void normalCommandsAcceptedButPlaceholdersAndSingleLineStillRequired() {
        for (String key : List.of("survival.command", "survival-new.command")) {
            assertDoesNotThrow(() -> Settings.validateSurvivalCommand("mvtp {player} {world}", key));
            for (String invalid : List.of("mvtp {player} world", "mvtp Momo {world}", "/mvtp {player} {world}", "mvtp {player} {world}\nop Momo"))
                assertThrows(IllegalArgumentException.class, () -> Settings.validateSurvivalCommand(invalid, key));
        }
    }
    @Test void completeDefaultsLoadWithBedSettingsAndBothNormalEntrances() throws Exception {
        Path config = directory.resolve("config.yml");
        try (var input = getClass().getResourceAsStream("/config.yml")) { Files.copy(input, config); }
        // Material item validation needs a live server registry; mock that boundary only.
        Material material = mock(Material.class); when(material.isItem()).thenReturn(true);
        try (var materials = mockStatic(Material.class)) {
            materials.when(() -> Material.matchMaterial(anyString())).thenReturn(material);
            Settings settings = Settings.load(config.toFile());
            assertEquals("mvtp {player} {world}", settings.survivalCommand()); assertEquals(settings.survivalCommand(), settings.features().newCommand());
            assertEquals("My Server", settings.serverName()); assertTrue(settings.menu().enabled()); assertTrue(settings.menu().autoLayout());
            assertTrue(settings.bedAutoSet().enabled()); assertEquals(Set.of("world"), settings.bedAutoSet().worlds());
            assertEquals(8, settings.menu().buttons().size()); assertEquals(34, settings.menu().buttons().get("afk-pool").slot());
            assertEquals("GeoLite2-City.mmdb", settings.locationPrefix().databaseFile()); assertFalse(settings.locationPrefix().enabled());
            assertFalse(settings.afkPool().enabled()); assertEquals("world", settings.afkPool().teleport().world()); assertEquals(0.5, settings.afkPool().teleport().x());
            assertEquals(64, settings.afkPool().teleport().y()); assertEquals(0.5, settings.afkPool().teleport().z());
            assertEquals(0, settings.afkPool().teleport().yaw()); assertEquals(60, settings.afkPool().reward().interval());
            assertEquals(10, settings.afkPool().reward().baseExp()); assertEquals(1.10, settings.afkPool().reward().normalMultiplier());
            assertEquals(0.45, settings.afkPool().reward().coinChance(), 0.0001); assertEquals(1, settings.afkPool().reward().coinMin());
            assertEquals(4, settings.afkPool().reward().coinMax()); assertEquals(2, settings.afkPool().exitGrace());
            assertEquals(40, settings.afkPool().title().stayTicks()); assertTrue(settings.tab().enabled());
            assertTrue(settings.tab().showLocationPrefix()); assertEquals(1, settings.tab().refreshSeconds());
            assertTrue(settings.welcomeTitle().enabled()); assertEquals(15, settings.welcomeTitle().delayTicks());
            assertFalse(settings.weeklyLeaderboard().positionConfigured()); assertEquals("world", settings.weeklyLeaderboard().world());
            assertFalse(settings.mascot().enabled()); assertEquals(-1, settings.mascot().npcId()); assertEquals(2, settings.mascot().hologramLines().size()); assertEquals(5, settings.mascot().normalClickLimit());
            var yaml = new YamlConfiguration(); yaml.load(config.toFile());
            yaml.set("home.bed-auto-set.worlds", List.of(123)); yaml.save(config.toFile());
            assertThrows(IllegalArgumentException.class, () -> Settings.load(config.toFile()));
            yaml.set("home.bed-auto-set.worlds", List.of()); yaml.set("home.bed-auto-set.enabled", false); yaml.save(config.toFile());
            assertFalse(Settings.load(config.toFile()).bedAutoSet().enabled());
        }
    }
}
