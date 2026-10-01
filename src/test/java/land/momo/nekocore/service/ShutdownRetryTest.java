package land.momo.nekocore.service;

import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.Profile;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShutdownRetryTest {
    @Test void lateFailedBatchIsRetriedBeforeCloseEvenWhenFinalDeltaIsEmpty() throws Exception {
        SqliteStore store = mock(SqliteStore.class);
        UUID player = UUID.randomUUID();
        when(store.join(eq(player), anyString(), anyLong())).thenReturn(CompletableFuture.completedFuture(
                new Profile(player, "Momo", 0, 1, 0, 0, 1000, 1000, false)));
        CompletableFuture<Void> pending = new CompletableFuture<>(), fence = new CompletableFuture<>();
        when(store.addPlaytime(anyMap())).thenReturn(pending, CompletableFuture.completedFuture(null));
        when(store.fence()).thenReturn(fence);
        AtomicLong nanos = new AtomicLong();
        PlayerDataService data = new PlayerDataService(store, nanos::get);
        data.join(player, "Momo", 1000).get(1, TimeUnit.SECONDS);
        nanos.set(10_000_000_000L); data.flush();
        var stopped = data.stop();
        assertFalse(stopped.isDone());
        pending.completeExceptionally(new IllegalStateException("temporary busy"));
        fence.complete(null);
        stopped.get(1, TimeUnit.SECONDS);
        verify(store, times(2)).addPlaytime(Map.of(player, 10L));
        assertFalse(data.loaded(player));
    }
}
