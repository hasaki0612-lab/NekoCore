package land.momo.nekocore.data;

import land.momo.nekocore.model.Home;
import land.momo.nekocore.model.LevelCurve;
import land.momo.nekocore.model.Profile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SqliteStoreTest {
    @TempDir Path temporary;
    SqliteStore store;
    final UUID player = UUID.randomUUID();
    final LevelCurve curve = new LevelCurve(100, 50, 0, 10_000);
    Path file;

    @BeforeEach void setup() throws Exception {
        file = temporary.resolve("test.db");
        store = new SqliteStore(Logger.getAnonymousLogger());
        await(store.open(file, curve));
        await(store.join(player, "Momo", 1000));
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }

    @Test void defaultsAndJoinTimestamps() throws Exception {
        Profile first = store.cached(player);
        assertEquals(0, first.coins()); assertEquals(1, first.level()); assertEquals(0, first.exp());
        assertEquals(0, first.playtimeSeconds()); assertFalse(first.showIp());
        Profile renamed = await(store.join(player, "NewName", 2000));
        assertEquals(1000, renamed.firstJoin()); assertEquals(2000, renamed.lastJoin());
        assertEquals("NewName", renamed.name());
        assertEquals(10, await(store.coins("newname", "add", 10)).coins());
    }

    @Test void serializedCoinMutationsNeverLoseUpdates() throws Exception {
        List<CompletableFuture<Profile>> requests = new ArrayList<>();
        for (int i = 0; i < 200; i++) requests.add(store.coins("Momo", "add", 1));
        await(CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)));
        assertEquals(200, store.cached(player).coins());
        assertEquals(0, await(store.coins("Momo", "take", 300)).coins());
        assertEquals(42, await(store.coins(player.toString(), "set", 42)).coins());
    }

    @Test void overflowAndNegativeAmountsRollback() throws Exception {
        await(store.coins("Momo", "set", Long.MAX_VALUE));
        ExecutionException overflow = assertThrows(ExecutionException.class, () -> await(store.coins("Momo", "add", 1)));
        assertEquals("amount-overflow", ((StorageException) overflow.getCause()).messageKey());
        assertEquals(Long.MAX_VALUE, store.cached(player).coins());
        assertThrows(ExecutionException.class, () -> await(store.coins("Momo", "set", -1)));
        assertEquals(Long.MAX_VALUE, store.cached(player).coins());
        assertEquals(0, await(store.coins("Momo", "take", Long.MAX_VALUE)).coins());
    }

    @Test void totalExperienceAndCurveReloadArePersisted() throws Exception {
        Profile first = await(store.experience("Momo", "set", 275));
        assertEquals(3, first.level()); assertEquals(275, first.exp());
        assertEquals(4, await(store.experience("Momo", "add", 200)).level());
        await(store.changeCurve(new LevelCurve(50, 0, 0, 10_000)));
        assertEquals(10, store.cached(player).level()); assertEquals(475, store.cached(player).exp());
        assertEquals(1, await(store.experience("Momo", "set", 0)).level());
        await(store.experience("Momo", "set", Long.MAX_VALUE));
        assertThrows(ExecutionException.class, () -> await(store.experience("Momo", "add", 1)));
        assertEquals(Long.MAX_VALUE, store.cached(player).exp());
    }

    @Test void afkPoolRewardAtomicallyUpdatesCoinsExperienceLevelAndCache() throws Exception {
        await(store.experience("Momo", "set", 95));
        var reward = await(store.afkReward(player, 11, 4));
        assertEquals(11, reward.exp()); assertEquals(4, reward.coins()); assertEquals(1, reward.previousLevel());
        assertEquals(106, reward.profile().exp()); assertEquals(4, reward.profile().coins()); assertEquals(2, reward.profile().level());
        assertEquals(reward.profile(), store.cached(player));
        await(store.coins("Momo", "set", Long.MAX_VALUE));
        assertThrows(ExecutionException.class, () -> await(store.afkReward(player, 1, 1)));
        assertEquals(Long.MAX_VALUE, store.cached(player).coins()); assertEquals(106, store.cached(player).exp());
    }

    @Test void homesAreIndependentPerPlayerAndWorldAndRetainOrientation() throws Exception {
        UUID lobby = UUID.randomUUID(), world = UUID.randomUUID(), other = UUID.randomUUID();
        Home a = new Home(player, lobby, "lobby", 1.25, 65, -3.5, 172.5f, -12);
        Home b = new Home(player, world, "world", 99, 71, 24, 12, 3);
        await(store.saveHome(a)); await(store.saveHome(b));
        assertEquals(a, await(store.home(player, lobby, "ignored")).orElseThrow());
        assertEquals(b, await(store.home(player, null, "WORLD")).orElseThrow());
        assertTrue(await(store.home(other, lobby, "lobby")).isEmpty());
        Home replaced = new Home(player, lobby, "renamed_lobby", 8, 85, 10, 0, 0);
        await(store.saveHome(replaced));
        assertEquals(replaced, await(store.home(player, lobby, "renamed_lobby")).orElseThrow());
        assertEquals(b, await(store.home(player, world, "world")).orElseThrow());
        // Recreating a world with the same name must not send the player to an unrelated location.
        assertTrue(await(store.home(player, UUID.randomUUID(), "world")).isEmpty());
    }

    @Test void restartRetainsAllFieldsAndHomes() throws Exception {
        UUID world = UUID.randomUUID();
        Home home = new Home(player, world, "world", 10, 50, -9, 20, 30);
        await(store.coins("Momo", "set", 123)); await(store.experience("Momo", "set", 500));
        await(store.addPlaytime(Map.of(player, 1234L))); await(store.togglePrivacy(player)); await(store.saveHome(home));
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store = new SqliteStore(Logger.getAnonymousLogger()); await(store.open(file, curve));
        Profile reopened = await(store.join(player, "Momo", 3000));
        assertEquals(123, reopened.coins()); assertEquals(500, reopened.exp()); assertEquals(4, reopened.level());
        assertEquals(1234, reopened.playtimeSeconds()); assertTrue(reopened.showIp());
        assertEquals(1000, reopened.firstJoin()); assertEquals(3000, reopened.lastJoin());
        assertEquals(home, await(store.home(player, world, "world")).orElseThrow());
    }

    @Test void playtimeDoesNotOverwriteOtherFieldsAndFailedBatchRollsBack() throws Exception {
        await(store.coins("Momo", "set", 123)); await(store.togglePrivacy(player));
        await(store.addPlaytime(Map.of(player, 10L))); await(store.addPlaytime(Map.of(player, 20L)));
        assertEquals(30, store.cached(player).playtimeSeconds());
        assertEquals(123, store.cached(player).coins()); assertTrue(store.cached(player).showIp());
        Map<UUID, Long> invalid = new LinkedHashMap<>(); invalid.put(player, 5L); invalid.put(UUID.randomUUID(), 1L);
        assertThrows(ExecutionException.class, () -> await(store.addPlaytime(invalid)));
        assertEquals(30, await(store.join(player, "Momo", 2000)).playtimeSeconds());
    }

    @Test void nameAmbiguityRequiresUuidAndUnknownPlayerIsNotCreated() throws Exception {
        await(store.join(UUID.randomUUID(), "MOMO", 1000));
        ExecutionException ambiguous = assertThrows(ExecutionException.class, () -> await(store.coins("momo", "add", 1)));
        assertEquals("ambiguous-player", ((StorageException) ambiguous.getCause()).messageKey());
        assertEquals(1, await(store.coins(player.toString(), "add", 1)).coins());
        assertThrows(ExecutionException.class, () -> await(store.coins("Unseen", "add", 1)));
        assertThrows(ExecutionException.class, () -> await(store.coins("Momo' OR 1=1 --", "add", 1)));
    }

    @Test void shutdownDrainsWritesAndRejectsNewOnes() throws Exception {
        List<CompletableFuture<Profile>> requests = new ArrayList<>();
        for (int i = 0; i < 30; i++) requests.add(store.coins("Momo", "add", 1));
        store.closeAsync();
        await(CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)));
        assertTrue(store.awaitClosed(10));
        assertThrows(ExecutionException.class, () -> await(store.coins("Momo", "add", 1)));
        store = new SqliteStore(Logger.getAnonymousLogger()); await(store.open(file, curve));
        assertEquals(30, await(store.join(player, "Momo", 3000)).coins());
    }

    @Test void schemaChecksAndFutureVersionGuard() throws Exception {
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.execute("UPDATE players SET coins=-1"));
            assertThrows(SQLException.class, () -> s.execute("UPDATE players SET show_ip=2"));
            s.execute("PRAGMA user_version=6");
        }
        store = new SqliteStore(Logger.getAnonymousLogger());
        assertThrows(ExecutionException.class, () -> await(store.open(file, curve)));
    }
}
