package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.TestDefaults;
import land.momo.nekocore.config.DailyTaskSettings;
import land.momo.nekocore.data.DailyTaskRepository;
import land.momo.nekocore.task.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyTaskOnlineTimeTest {
    @Test void elapsedTimeCrossingBeijingMidnightIsWrittenToCapturedOldRotation() throws Exception {
        Harness h = new Harness(Instant.parse("2026-09-30T15:59:40Z"));
        h.start();
        h.nanos.set(40_000_000_000L);
        h.clock.instant = Instant.parse("2026-09-30T16:00:20Z");

        h.service.flushOnlineTime(List.of(h.player)).join();

        verify(h.repository).advance(eq(h.playerId), same(h.rotation), eq("simple_good_morning"),
                eq(40L), isNull(), anyLong(), any());
        verify(h.repository, times(1)).ensure(any(), anyList());
    }

    @Test void failedOnlineWriteIsRetriedWithoutLosingElapsedSeconds() throws Exception {
        Harness h = new Harness(Instant.parse("2026-09-30T08:00:00Z"));
        when(h.repository.advance(any(), any(), anyString(), anyLong(), nullable(String.class), anyLong(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")))
                .thenReturn(CompletableFuture.completedFuture(Harness.noCompletion()));
        h.start();

        h.nanos.set(10_000_000_000L);
        assertThrows(CompletionException.class, () -> h.service.flushOnlineTime(List.of(h.player)).join());
        h.nanos.set(15_000_000_000L);
        h.service.flushOnlineTime(List.of(h.player)).join();

        verify(h.repository).advance(eq(h.playerId), same(h.rotation), eq("simple_good_morning"),
                eq(10L), isNull(), anyLong(), any());
        verify(h.repository).advance(eq(h.playerId), same(h.rotation), eq("simple_good_morning"),
                eq(15L), isNull(), anyLong(), any());
    }

    private static final class Harness {
        final NekoCorePlugin plugin = mock(NekoCorePlugin.class);
        final DailyTaskRepository repository = mock(DailyTaskRepository.class);
        final DailyTaskSettings settings;
        final UUID playerId = UUID.randomUUID();
        final Player player = mock(Player.class);
        final MutableClock clock;
        final AtomicLong nanos = new AtomicLong();
        final DailyTaskRotation rotation;
        final DailyTaskService service;

        Harness(Instant instant) throws Exception {
            settings = DailyTaskSettings.load(TestDefaults.yaml());
            clock = new MutableClock(instant);
            rotation = new DailyTaskRotation(LocalDate.of(2026, 9, 30), UUID.randomUUID(), List.of(
                    new DailyTaskRotation.Entry(DailyTaskDifficulty.EASY, 0, "simple_good_morning")));
            when(plugin.dailyTaskSettings()).thenReturn(settings);
            when(player.getUniqueId()).thenReturn(playerId);
            when(repository.ensure(any(), anyList())).thenReturn(CompletableFuture.completedFuture(rotation));
            when(repository.advance(any(), any(), anyString(), anyLong(), nullable(String.class), anyLong(), any()))
                    .thenReturn(CompletableFuture.completedFuture(noCompletion()));
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(plugin).onMain(any(Runnable.class));
            service = new DailyTaskService(plugin, repository, clock, new Random(1), nanos::get,
                    material -> material == Material.APPLE, item -> false);
        }

        void start() {
            service.ensureToday().join();
            service.join(player);
        }

        static DailyTaskRepository.Advance noCompletion() {
            return new DailyTaskRepository.Advance(
                    new DailyTaskProgress("simple_good_morning", 1, false, false, ""), false, false, 0, 0);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
