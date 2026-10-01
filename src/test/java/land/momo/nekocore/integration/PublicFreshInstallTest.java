package land.momo.nekocore.integration;

import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.LevelCurve;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.*;

/** Headless fresh-install contract; live Paper startup remains in VERIFICATION.md. */
class PublicFreshInstallTest {
    @TempDir Path server;

    @Test void emptyPluginDirectoryCreatesPublicResourcesMigratesV1ToV5AndClosesCleanly() throws Exception {
        Path data = server.resolve("plugins/NekoCore");
        assertFalse(Files.exists(data));
        Files.createDirectories(data);
        copy("/config.yml", data.resolve("config.yml"));
        copy("/messages.yml", data.resolve("messages.yml"));

        YamlConfiguration config = yaml(data.resolve("config.yml"));
        YamlConfiguration messages = yaml(data.resolve("messages.yml"));
        assertEquals(8, config.getInt("config-version"));
        assertEquals(7, messages.getInt("messages-version"));
        assertFalse(Files.exists(data.resolve("GeoLite2-City.mmdb")));
        for (String path : List.of("location-prefix.enabled", "survival-new.enabled", "minigames.enabled",
                "afk-pool.enabled", "afk-pool.position-configured", "weekly-coin-leaderboard.enabled",
                "weekly-coin-leaderboard.position-configured", "mascot.enabled"))
            assertFalse(config.getBoolean(path), path);

        AtomicInteger severe = new AtomicInteger();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { if (record.getLevel().intValue() >= Level.SEVERE.intValue()) severe.incrementAndGet(); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        Path database = data.resolve("nekocore.db");
        SqliteStore store = new SqliteStore(logger);
        store.open(database, new LevelCurve(100, 50, 0, 10_000)).get(10, TimeUnit.SECONDS);
        store.closeAsync();
        assertTrue(store.awaitClosed(10));
        assertEquals(0, severe.get());

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(result.next());
                assertEquals(SqliteStore.SCHEMA_VERSION, result.getInt(1));
            }
            for (String table : List.of("players", "daily_checkins", "bags", "daily_task_rotations", "weekly_coin_earnings"))
                try (var result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                    assertTrue(result.next(), table);
                }
        }

        YamlConfiguration plugin = resourceYaml("/plugin.yml");
        for (String command : List.of("menu", "coins", "checkin", "sethome", "home", "store", "bag", "tpn", "nekocore"))
            assertTrue(plugin.contains("commands." + command), command);
    }

    private void copy(String resource, Path destination) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            Files.copy(input, destination);
        }
    }

    private static YamlConfiguration yaml(Path path) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        return yaml;
    }

    private YamlConfiguration resourceYaml(String resource) throws Exception {
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }
}
