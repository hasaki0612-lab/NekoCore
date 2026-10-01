package land.momo.nekocore.data;

import land.momo.nekocore.config.DailyTaskSettings;
import land.momo.nekocore.model.LevelCurve;
import land.momo.nekocore.task.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DailyTaskRepositoryTest {
    @TempDir Path directory;
    SqliteStore store; DailyTaskRepository repository; UUID player; Path database;
    @BeforeEach void setup() throws Exception {
        database = directory.resolve("tasks.db"); store = new SqliteStore(Logger.getAnonymousLogger());
        await(store.open(database, new LevelCurve(100,50,0,10000))); player = UUID.randomUUID(); await(store.join(player,"Momo",1));
        repository = new DailyTaskRepository(store);
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }
    static List<DailyTaskRotation.Entry> tasks(String id) {
        return List.of(new DailyTaskRotation.Entry(DailyTaskDefinition.byId(id).difficulty(),0,id));
    }

    @Test void sameDateAndRestartKeepRotationWhileNewDateCreatesAnother() throws Exception {
        LocalDate date = LocalDate.of(2026,9,30);
        var first = await(repository.ensure(date, tasks("simple_crafting")));
        var again = await(repository.ensure(date, tasks("simple_healthy"))); assertEquals(first, again);
        store.closeAsync(); assertTrue(store.awaitClosed(10));
        store = new SqliteStore(Logger.getAnonymousLogger()); await(store.open(database,new LevelCurve(100,50,0,10000)));
        repository = new DailyTaskRepository(store);
        assertEquals(first, await(repository.ensure(date, tasks("simple_healthy"))));
        var next = await(repository.ensure(date.plusDays(1), tasks("simple_healthy")));
        assertNotEquals(first.id(), next.id()); assertEquals(date.plusDays(1), next.date());
    }

    @Test void distinctCraftStateAndAtomicRewardArePersistedExactlyOnce() throws Exception {
        var rotation = await(repository.ensure(LocalDate.of(2026,9,30), tasks("simple_crafting")));
        var reward = new DailyTaskSettings.Reward(20,15);
        for (String material : List.of("CHEST","CHEST","FURNACE","TORCH","WOODEN_PICKAXE","WOODEN_SWORD"))
            await(repository.advance(player,rotation,"simple_crafting",1,material,5,reward));
        var progress = await(repository.progress(player,rotation)).get("simple_crafting");
        assertEquals(5,progress.progress()); assertTrue(progress.completed()); assertTrue(progress.rewardGiven());
        assertEquals(20,store.cached(player).coins()); assertEquals(15,store.cached(player).exp());
        var duplicate = await(repository.advance(player,rotation,"simple_crafting",99,"NEW",5,reward));
        assertFalse(duplicate.completedNow()); assertEquals(20,store.cached(player).coins()); assertEquals(15,store.cached(player).exp());
    }

    @Test void rerollArchivesOldProgressAndNewRotationStartsAtZeroThenResetWorks() throws Exception {
        LocalDate date = LocalDate.of(2026,9,30); var reward = new DailyTaskSettings.Reward(20,15);
        var old = await(repository.ensure(date,tasks("simple_healthy")));
        await(repository.advance(player,old,"simple_healthy",1,null,1,reward));
        var next = await(repository.reroll(date,tasks("simple_healthy")));
        assertNotEquals(old.id(),next.id()); assertEquals(0,await(repository.progress(player,next)).get("simple_healthy").progress());
        await(repository.advance(player,next,"simple_healthy",1,null,1,reward)); assertEquals(40,store.cached(player).coins());
        await(repository.reset(player,next)); assertEquals(0,await(repository.progress(player,next)).get("simple_healthy").progress());
        assertEquals(1,await(repository.progress(player,old)).get("simple_healthy").progress());
    }
}
