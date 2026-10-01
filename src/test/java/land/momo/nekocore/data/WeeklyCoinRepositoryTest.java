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

class WeeklyCoinRepositoryTest {
    @TempDir Path directory;
    MutableClock clock; SqliteStore store; WeeklyCoinRepository weekly; UUID player; Path database;
    @BeforeEach void setup() throws Exception {
        clock=new MutableClock(Instant.parse("2026-09-27T15:59:59Z")); database=directory.resolve("weekly.db");
        store=new SqliteStore(Logger.getAnonymousLogger(),clock); await(store.open(database,new LevelCurve(100,50,0,10000)));
        player=UUID.randomUUID(); await(store.join(player,"Momo",clock.millis())); weekly=new WeeklyCoinRepository(store);
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10,TimeUnit.SECONDS); }

    @Test void mondayMidnightUsesUtcPlusEightAndHistoricalWeekSurvivesRestart() throws Exception {
        assertEquals("2026-09-21",WeeklyCoinRepository.period(clock.millis()));
        assertTrue(await(store.checkin(player,ZoneId.of("Asia/Shanghai"),100,50)).claimed());
        await(store.afkReward(player,11,4)); await(store.coins("Momo","add",500));
        var commerce=new CommerceRepository(store); UUID exchangeId=UUID.randomUUID();
        var sale=new CommerceRepository.Exchange(exchangeId,player,UUID.randomUUID(),"store",new byte[]{1},new byte[]{2},60,
                "stone",CommerceRepository.day(clock.millis()),"sell",2,null,null,0);
        await(commerce.prepareStore(sale,30,200,false)); await(commerce.commit(exchangeId));
        await(store.atomic(c -> { WeeklyCoinRepository.record(c,player,30,CoinChangeReason.GAMEPLAY_REWARD,clock.millis());
            WeeklyCoinRepository.record(c,player,999,CoinChangeReason.PURCHASE,clock.millis()); return null; }));
        var old=await(weekly.current()); assertEquals("2026-09-21",old.period()); assertEquals(194,old.entries().getFirst().earned());
        assertEquals(664,store.cached(player).coins()); // admin balance change is intentionally absent from the board.

        clock.value=Instant.parse("2026-09-27T16:00:00Z");
        assertEquals("2026-09-28",WeeklyCoinRepository.period(clock.millis())); await(store.afkReward(player,1,5));
        assertEquals(5,await(weekly.current()).entries().getFirst().earned());
        assertEquals(194,await(weekly.history("2026-09-21")).entries().getFirst().earned());
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store=new SqliteStore(Logger.getAnonymousLogger(),clock); await(store.open(database,new LevelCurve(100,50,0,10000)));
        await(store.join(player,"Renamed",clock.millis())); weekly=new WeeklyCoinRepository(store);
        assertEquals("Renamed",await(weekly.current()).entries().getFirst().name());
        assertEquals(194,await(weekly.history("2026-09-21")).entries().getFirst().earned());
    }

    @Test void topTenIsDescendingAndAdminRecoveryRollbackMigrationNeverCount() throws Exception {
        for(int i=1;i<=11;i++) {
            UUID id=UUID.randomUUID(); await(store.join(id,"P"+i,clock.millis())); long coins=i*10L;
            await(store.atomic(c -> { WeeklyCoinRepository.record(c,id,coins,CoinChangeReason.DAILY_TASK,clock.millis());
                for(var reason:List.of(CoinChangeReason.ADMIN,CoinChangeReason.RECOVERY,CoinChangeReason.ROLLBACK,
                        CoinChangeReason.DEBUG,CoinChangeReason.MIGRATION,CoinChangeReason.COMPENSATION))
                    WeeklyCoinRepository.record(c,id,999,reason,clock.millis()); return null; }));
        }
        var entries=await(weekly.current()).entries(); assertEquals(10,entries.size());
        assertEquals(110,entries.getFirst().earned()); assertEquals(20,entries.getLast().earned());
        for(int i=1;i<entries.size();i++) assertTrue(entries.get(i-1).earned()>=entries.get(i).earned());
    }

    static final class MutableClock extends Clock {
        Instant value; MutableClock(Instant value){this.value=value;}
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
    }
}
