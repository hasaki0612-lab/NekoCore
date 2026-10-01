package land.momo.nekocore.data;

import land.momo.nekocore.model.LevelCurve;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class CommerceRepositoryTest {
    @TempDir Path directory;
    SqliteStore store;
    CommerceRepository repo;
    TitleRepository titles;
    final UUID player = UUID.randomUUID(), world = UUID.randomUUID();
    final MutableClock clock = new MutableClock();
    @BeforeEach void setup() throws Exception {
        store = new SqliteStore(Logger.getAnonymousLogger(), clock);
        await(store.open(directory.resolve("data.db"), new LevelCurve(100, 50, 0, 10000)));
        await(store.join(player, "Momo", clock.millis())); await(store.coins("Momo", "set", 10000));
        repo = new CommerceRepository(store); titles = new TitleRepository(store);
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }
    static String failure(CompletableFuture<?> future) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> await(future));
        return ((StorageException) e.getCause()).messageKey();
    }
    CommerceRepository.Exchange trade(int quantity, long price, String direction) throws Exception {
        String period = await(repo.quota(player, "stone", null)).period();
        return new CommerceRepository.Exchange(UUID.randomUUID(), player, world, "store", new byte[]{1}, new byte[]{2},
                quantity * price * (direction.equals("buy") ? -1 : 1), "stone", period, direction, quantity, null, null, 0);
    }
    @Test void titlePurchaseIsPermanentUniqueAtomicAndEquips() throws Exception {
        assertEquals("mame", await(titles.purchase(player, "mame", 1500)).equipped());
        assertEquals(8500, store.cached(player).coins());
        assertEquals("title-already-owned", failure(titles.purchase(player, "mame", 1500)));
        await(titles.purchase(player, "momo", 4500));
        assertEquals(Set.of("mame", "momo"), titles.cached(player).owned());
        await(titles.equip(player, "mame")); await(titles.equip(player, ""));
        assertEquals("", titles.cached(player).equipped()); assertEquals(4000, store.cached(player).coins());
        assertEquals("title-not-owned", failure(titles.equip(player, "sora")));
        assertEquals("not-enough-coins", failure(titles.purchase(player, "sora", 12000)));
    }
    @Test void simultaneousTitlePurchasesChargeOnlyOnce() throws Exception {
        var first = titles.purchase(player, "mame", 1500);
        var second = titles.purchase(player, "mame", 1500);
        await(first); assertEquals("title-already-owned", failure(second)); assertEquals(8500, store.cached(player).coins());
    }
    @Test void storeReservationAndCommitAreIdempotent() throws Exception {
        var x = trade(32, 3, "buy"); await(repo.prepareStore(x, 3, 64, false));
        assertEquals(9904, store.cached(player).coins()); assertEquals(32, await(repo.quota(player, "stone", null)).bought());
        assertEquals(x.id(), await(repo.pending(player)).orElseThrow().id());
        assertEquals("exchange-recovery", failure(repo.prepareStore(trade(1, 3, "buy"), 3, 64, false)));
        assertEquals("exchange-recovery", failure(store.coins("Momo", "set", 0)));
        await(repo.commit(x.id())); await(repo.commit(x.id())); await(repo.rollback(x));
        assertEquals(9904, store.cached(player).coins()); assertTrue(await(repo.pending(player)).isEmpty());
    }
    @Test void rollbackRestoresMoneyAndQuotaExactlyOnce() throws Exception {
        var x = trade(64, 3, "buy"); await(repo.prepareStore(x, 3, 64, false));
        await(repo.rollback(x)); await(repo.rollback(x));
        assertEquals(10000, store.cached(player).coins()); assertEquals(0, await(repo.quota(player, "stone", null)).bought());
    }
    @Test void ordinaryBuyAndSellQuotasAreSeparateAndPersist() throws Exception {
        var x = trade(64, 3, "buy"); await(repo.prepareStore(x, 3, 64, false)); await(repo.commit(x.id()));
        assertEquals("store-limit", failure(repo.prepareStore(trade(1, 3, "buy"), 3, 64, false)));
        x = trade(200, 2, "sell"); await(repo.prepareStore(x, 2, 200, false)); await(repo.commit(x.id()));
        assertEquals("store-limit", failure(repo.prepareStore(trade(1, 2, "sell"), 2, 200, false)));
        assertEquals(10208, store.cached(player).coins());
        assertEquals(64, await(new CommerceRepository(store).quota(player, "stone", null)).bought());
    }
    @Test void fullStacksAreProvidedAsPerProductLimitsNotFixed64() throws Exception {
        for (int limit : new int[]{1, 16, 64}) {
            await(repo.resetQuotas(player));
            var x = trade(limit, 3, "buy"); await(repo.prepareStore(x, 3, limit, false)); await(repo.commit(x.id()));
            assertEquals("store-limit", failure(repo.prepareStore(trade(1, 3, "buy"), 3, limit, false)));
        }
    }
    @Test void midnightResetsDailyButClockBackCannotReopenQuota() throws Exception {
        var old = trade(1, 3, "buy"); await(repo.prepareStore(old, 3, 64, false)); await(repo.commit(old.id()));
        clock.value = Instant.parse("2026-09-29T16:00:00Z");
        assertEquals(0, await(repo.quota(player, "stone", null)).bought());
        assertEquals("store-expired", failure(repo.prepareStore(tradeForPeriod(old.period()), 3, 64, false)));
        var fresh = trade(1, 3, "buy"); await(repo.prepareStore(fresh, 3, 64, false)); await(repo.commit(fresh.id()));
        clock.value = Instant.parse("2026-09-29T01:00:00Z");
        assertEquals(1, await(repo.quota(player, "stone", null)).bought());
    }
    CommerceRepository.Exchange tradeForPeriod(String period) {
        return new CommerceRepository.Exchange(UUID.randomUUID(), player, world, "store", new byte[]{1}, new byte[]{2}, -3,
                "stone", period, "buy", 1, null, null, 0);
    }
    @Test void notEnoughMoneyOrOverflowDoesNotConsumeQuota() throws Exception {
        assertEquals("not-enough-coins", failure(repo.prepareStore(trade(64, 1000, "buy"), 1000, 64, false)));
        assertEquals(0, await(repo.quota(player, "stone", null)).bought());
        await(store.coins("Momo", "set", Long.MAX_VALUE));
        assertEquals("amount-overflow", failure(repo.prepareStore(trade(1, 2, "sell"), 2, 200, false)));
        assertEquals(0, await(repo.quota(player, "stone", null)).sold());
    }
    @Test void bagCapacityNeverShrinksAndRollbackPreservesContents() throws Exception {
        assertEquals(18, await(repo.bag(player, 18)).capacity());
        assertEquals(36, await(repo.bag(player, 36)).capacity());
        var old = await(repo.bag(player, 18)); assertEquals(36, old.capacity());
        var x = new CommerceRepository.Exchange(UUID.randomUUID(), player, world, "bag", new byte[]{1}, new byte[]{2}, 0,
                "", "", "", 0, old.contents(), new byte[]{7,8}, old.revision());
        await(repo.prepareBag(x)); assertEquals("exchange-recovery", failure(repo.bag(player, 18)));
        await(repo.rollback(x));
        var restored = await(repo.bag(player, 18)); assertArrayEquals(old.contents(), restored.contents());
        assertEquals(36, restored.capacity()); assertEquals(2, restored.revision());
        assertEquals("exchange-stale", failure(repo.prepareBag(x)));
    }
    @Test void restartKeepsPendingReceiptAndBagForReconciliation() throws Exception {
        await(titles.purchase(player, "mame", 1500)); await(repo.bag(player, 27));
        var x = trade(16, 3, "buy"); await(repo.prepareStore(x, 3, 64, false));
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store = new SqliteStore(Logger.getAnonymousLogger(), clock);
        await(store.open(directory.resolve("data.db"), new LevelCurve(100, 50, 0, 10000)));
        await(store.join(player, "Renamed", clock.millis())); repo = new CommerceRepository(store); titles = new TitleRepository(store);
        assertEquals("mame", await(titles.load(player)).equipped()); assertEquals(x.id(), await(repo.pending(player)).orElseThrow().id());
        await(repo.rollback(await(repo.pending(player)).orElseThrow())); assertEquals(8500, store.cached(player).coins());
        assertEquals(27, await(repo.bag(player, 18)).capacity());
    }
    List<CommerceRepository.Offer> offers() {
        List<CommerceRepository.Offer> result = new ArrayList<>();
        for (int i = 0; i < 8; i++) result.add(new CommerceRepository.Offer(i, "minecraft:unbreaking", i < 2 ? 3 : 1, i < 2 ? 1800 : 300, i < 2));
        return result;
    }
    @Test void enchantBatchIsPersistentAndOnlyChangesAtFourNotMidnight() throws Exception {
        var first = await(repo.ensureBatch(offers(), false)); assertEquals(8, first.offers().size());
        assertEquals(first, await(repo.ensureBatch(offers(), false)));
        clock.value = Instant.parse("2026-09-29T16:00:00Z");
        assertEquals(first.id(), await(repo.ensureBatch(offers(), false)).id());
        clock.value = Instant.parse("2026-09-29T20:00:00Z");
        assertNotEquals(first.id(), await(repo.ensureBatch(offers(), false)).id());
    }
    @Test void enchantOfferCanOnlyBeBoughtOnceAndOldGuiRejected() throws Exception {
        var first = await(repo.ensureBatch(offers(), false));
        var x = enchanted(first); await(repo.prepareStore(x, 1800, 1, true)); await(repo.commit(x.id()));
        assertEquals("store-limit", failure(repo.prepareStore(enchanted(first), 1800, 1, true)));
        var refreshed = await(repo.ensureBatch(offers(), true)); assertNotEquals(first.id(), refreshed.id());
        assertEquals("store-expired", failure(repo.prepareStore(enchanted(first), 1800, 1, true)));
        var next = enchanted(refreshed); await(repo.prepareStore(next, 1800, 1, true)); await(repo.commit(next.id()));
        assertEquals(6400, store.cached(player).coins());
    }
    CommerceRepository.Exchange enchanted(CommerceRepository.Batch batch) {
        return new CommerceRepository.Exchange(UUID.randomUUID(), player, world, "store", new byte[]{1}, new byte[]{2}, -1800,
                "enchant-0", "enchant:" + batch.id(), "buy", 1, null, null, 0);
    }
    @Test void playersHaveIndependentLimitsTitlesAndBags() throws Exception {
        UUID other = UUID.randomUUID(); await(store.join(other, "Sora", 1));
        await(titles.purchase(player, "mame", 1500)); await(repo.bag(player, 36));
        assertTrue(await(titles.load(other)).owned().isEmpty()); assertEquals(18, await(repo.bag(other, 18)).capacity());
        var x = trade(64, 3, "buy"); await(repo.prepareStore(x, 3, 64, false)); await(repo.commit(x.id()));
        assertEquals(0, await(repo.quota(other, "stone", null)).bought());
    }
    static final class MutableClock extends Clock {
        Instant value = Instant.parse("2026-09-29T01:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return value; }
    }
    @Test void unopenedOfflineBagsUnlockAndNeverShrinkAfterLevelReduction() throws Exception {
        await(store.experience("Momo", "set", 50000));
        await(repo.unlockAll(10, 25));
        await(store.experience("Momo", "set", 0));
        await(repo.unlockAll(10, 25));
        assertEquals(36, await(repo.bag(player, 18)).capacity());
    }
    @Test void curveAndCapacityReloadRollbackTogetherWhenDatabaseRejectsBagWrite() throws Exception {
        await(store.experience("Momo", "set", 275)); int oldLevel = store.cached(player).level();
        await(repo.bag(player, 18));
        await(store.query(c -> { try(var s = c.createStatement()) { s.execute("CREATE TRIGGER reject_bag BEFORE UPDATE ON bags BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END"); } return null; }));
        assertThrows(ExecutionException.class, () -> await(store.changeCurveAndBags(new LevelCurve(1,0,0,10000),10,25)));
        assertEquals(oldLevel, store.cached(player).level());
        await(store.query(c -> { try(var s = c.createStatement()) { s.execute("DROP TRIGGER reject_bag"); } return null; }));
        assertEquals(18, await(repo.bag(player,18)).capacity());
        assertEquals(3, await(store.experience("Momo","add",1)).level()); // previous curve still active
        await(store.changeCurveAndBags(new LevelCurve(1,0,0,10000),10,25));
        assertEquals(36, await(repo.bag(player,18)).capacity()); assertEquals(277,store.cached(player).level());
    }
    @Test void committedBagContentsSurviveRestartRenameAndCapacityGrowth() throws Exception {
        var bag = await(repo.bag(player,18)); byte[] items = {42,17,88};
        var x = new CommerceRepository.Exchange(UUID.randomUUID(),player,world,"bag",new byte[]{1},new byte[]{2},0,"","","",0,bag.contents(),items,bag.revision());
        await(repo.prepareBag(x)); await(repo.commit(x.id()));
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store = new SqliteStore(Logger.getAnonymousLogger(),clock); await(store.open(directory.resolve("data.db"),new LevelCurve(100,50,0,10000)));
        await(store.join(player,"Renamed",clock.millis())); repo = new CommerceRepository(store);
        var restored = await(repo.bag(player,36)); assertArrayEquals(items,restored.contents()); assertEquals(36,restored.capacity());
        assertArrayEquals(items,await(repo.bag(player,18)).contents());
    }
}
