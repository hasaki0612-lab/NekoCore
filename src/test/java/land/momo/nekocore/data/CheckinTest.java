package land.momo.nekocore.data;

import land.momo.nekocore.model.LevelCurve;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class CheckinTest {
    @TempDir Path directory;
    final UUID id = UUID.randomUUID();
    final LevelCurve curve = new LevelCurve(100, 50, 0, 10000);
    final ZoneId shanghai = ZoneId.of("Asia/Shanghai");
    final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T15:59:59Z"));
    SqliteStore store;
    Path db;
    @BeforeEach void setup() throws Exception {
        db = directory.resolve("test.db"); store = new SqliteStore(Logger.getAnonymousLogger(), clock);
        await(store.open(db, curve)); await(store.join(id, "Momo", 1000));
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }

    @Test void concurrentClicksAwardExactlyOnceAndLevelUpAtomically() throws Exception {
        await(store.experience("Momo", "set", 75));
        List<CompletableFuture<SqliteStore.DailyClaim>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) futures.add(store.checkin(id, shanghai, 100, 50));
        await(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));
        assertEquals(1, futures.stream().map(CompletableFuture::join).filter(SqliteStore.DailyClaim::claimed).count());
        assertEquals(100, store.cached(id).coins()); assertEquals(125, store.cached(id).exp()); assertEquals(2, store.cached(id).level());
        assertEquals("2026-09-28", store.cachedCheckin(id));
    }

    @Test void midnightUsesConfiguredTimezoneAndDoesNotRequireReconnect() throws Exception {
        assertTrue(await(store.checkin(id, shanghai, 100, 50)).claimed());
        clock.instant.set(Instant.parse("2026-09-28T16:00:00Z"));
        var next = await(store.checkin(id, shanghai, 100, 50));
        assertTrue(next.claimed()); assertEquals("2026-09-29", next.date());
        assertFalse(await(store.checkin(id, shanghai, 100, 50)).claimed());
        assertEquals(200, store.cached(id).coins()); assertEquals(100, store.cached(id).exp());
        // Moving the wall clock / timezone backwards cannot reopen an older date.
        assertFalse(await(store.checkin(id, ZoneOffset.UTC, 100, 50)).claimed());
        assertEquals("2026-09-29", store.cachedCheckin(id));
    }

    @Test void restartAndRenamingDoNotAllowAnotherReward() throws Exception {
        await(store.checkin(id, shanghai, 100, 50));
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store = new SqliteStore(Logger.getAnonymousLogger(), clock); await(store.open(db, curve));
        await(store.join(id, "Renamed", 2000));
        assertFalse(await(store.checkin(id, shanghai, 100, 50)).claimed()); assertEquals(100, store.cached(id).coins());
        assertEquals("2026-09-28", store.cachedCheckin(id));
    }

    @Test void overflowRollsBackRewardsAndDoesNotConsumeTodaysClaim() throws Exception {
        await(store.coins("Momo", "set", Long.MAX_VALUE));
        assertThrows(ExecutionException.class, () -> await(store.checkin(id, shanghai, 100, 50)));
        assertEquals("", store.cachedCheckin(id)); assertEquals(0, store.cached(id).exp());
        await(store.coins("Momo", "set", 0)); await(store.experience("Momo", "set", Long.MAX_VALUE));
        assertThrows(ExecutionException.class, () -> await(store.checkin(id, shanghai, 100, 50)));
        assertEquals(0, store.cached(id).coins()); assertEquals("", store.cachedCheckin(id));
        await(store.experience("Momo", "set", 0)); assertTrue(await(store.checkin(id, shanghai, 100, 50)).claimed());
    }

    @Test void playersHaveIndependentDailyClaims() throws Exception {
        UUID other = UUID.randomUUID(); await(store.join(other, "Other", 1000));
        assertTrue(await(store.checkin(id, shanghai, 100, 50)).claimed());
        assertEquals("", store.cachedCheckin(other));
        assertTrue(await(store.checkin(other, shanghai, 100, 50)).claimed());
        assertEquals(100, store.cached(other).coins());
    }

    @Test void migrationFromV1KeepsExistingPlayerAndHome() throws Exception {
        Path oldDb = directory.resolve("v1.db"); UUID world = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + oldDb); Statement s = c.createStatement()) {
            String schema;
            try (var in = getClass().getResourceAsStream("/db/migration/V1__initial.sql")) { schema = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            for (String sql : schema.split(";")) if (!sql.isBlank()) s.execute(sql);
            s.execute("INSERT INTO players VALUES('" + id + "','Momo',777,3,275,3600,1000,2000,1)");
            s.execute("INSERT INTO homes VALUES('" + id + "','" + world + "','world',1.5,64,-2.5,90,15)");
            s.execute("PRAGMA user_version=1");
        }
        SqliteStore upgraded = new SqliteStore(Logger.getAnonymousLogger(), clock);
        try {
            await(upgraded.open(oldDb, curve)); var p = await(upgraded.join(id, "Momo", 3000));
            assertEquals(777, p.coins()); assertEquals(275, p.exp()); assertEquals(3600, p.playtimeSeconds()); assertTrue(p.showIp());
            assertEquals(90, await(upgraded.home(id, world, "world")).orElseThrow().yaw());
            assertTrue(await(upgraded.checkin(id, shanghai, 100, 50)).claimed()); assertEquals(877, upgraded.cached(id).coins());
        } finally { upgraded.closeAsync(); assertTrue(upgraded.awaitClosed(10)); }
    }

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> instant;
        private final ZoneId zone;
        MutableClock(Instant instant) { this(new AtomicReference<>(instant), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId next) { return new MutableClock(instant, next); }
        @Override public Instant instant() { return instant.get(); }
    }
}
