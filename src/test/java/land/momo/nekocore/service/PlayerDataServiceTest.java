package land.momo.nekocore.service;

import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.LevelCurve;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataServiceTest {
    @TempDir Path directory;
    SqliteStore store;
    PlayerDataService service;
    final AtomicLong nanos = new AtomicLong();
    final UUID player = UUID.randomUUID();
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }

    @BeforeEach void setup() throws Exception {
        store = new SqliteStore(Logger.getAnonymousLogger());
        await(store.open(directory.resolve("clock.db"), new LevelCurve(100, 50, 0, 100)));
        service = new PlayerDataService(store, nanos::get);
        await(service.join(player, "Momo", 1000));
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }

    @Test void keepsFractionalRemaindersAndDoesNotDoubleCountAcrossFlushes() throws Exception {
        nanos.set(1_500_000_000L); await(service.flush());
        assertEquals(1, store.cached(player).playtimeSeconds()); assertEquals(1, service.view(player).playtimeSeconds());
        nanos.set(2_100_000_000L); await(service.flush());
        assertEquals(2, store.cached(player).playtimeSeconds()); assertEquals(2, service.view(player).playtimeSeconds());
        await(service.flush()); assertEquals(2, store.cached(player).playtimeSeconds());
        nanos.set(9_900_000_000L); await(service.quit(player));
        assertEquals(9, store.cached(player).playtimeSeconds()); assertFalse(service.loaded(player));
    }

    @Test void rapidReconnectAndAdminChangesPreserveBalancesAndTime() throws Exception {
        nanos.set(5_500_000_000L);
        var quit = service.quit(player);
        var join = service.join(player, "Momo", 2000);
        var coins = store.coins("Momo", "set", 77);
        await(quit); await(join); await(coins);
        nanos.set(8_900_000_000L); await(service.flush());
        assertEquals(8, service.view(player).playtimeSeconds());
        assertEquals(8, store.cached(player).playtimeSeconds()); assertEquals(77, store.cached(player).coins());
        assertEquals(1000, store.cached(player).firstJoin()); assertEquals(2000, store.cached(player).lastJoin());
    }

    @Test void livePlaytimeIncludesUnflushedSeconds() {
        nanos.set(120_000_000_000L);
        assertEquals(120, service.view(player).playtimeSeconds()); assertEquals(0, store.cached(player).playtimeSeconds());
    }
}
