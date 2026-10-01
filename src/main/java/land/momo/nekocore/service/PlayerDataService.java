package land.momo.nekocore.service;

import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.Profile;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Session clock uses monotonic time, so clock corrections and low TPS do not corrupt playtime. */
public final class PlayerDataService {
    private final SqliteStore store;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> retrySeconds = new HashMap<>();

    public PlayerDataService(SqliteStore store) { this(store, System::nanoTime); }
    public PlayerDataService(SqliteStore store, LongSupplier nanoClock) { this.store = store; this.nanoClock = nanoClock; }

    public CompletableFuture<Profile> join(UUID id, String name, long epochMillis) {
        Session session = new Session(nanoClock.getAsLong());
        sessions.put(id, session);
        return store.join(id, name, epochMillis).thenApply(profile -> {
            session.initialPlaytime = profile.playtimeSeconds();
            return profile;
        });
    }

    public boolean loaded(UUID id) { Session session = sessions.get(id); return session != null && session.initialPlaytime >= 0; }

    /** Safe for PlaceholderAPI's async callers: only immutable records and session clock data. */
    public Profile view(UUID id) {
        Profile profile = store.cached(id);
        Session session = sessions.get(id);
        if (profile == null || session == null || session.initialPlaytime < 0) return profile;
        long seconds = elapsed(session);
        return profile.withPlaytime(saturatingAdd(session.initialPlaytime, seconds));
    }

    // Called on the main thread. DB failures return deltas to the next batch without reverting newer data.
    public CompletableFuture<Void> flush() {
        Map<UUID, Long> deltas = drainRetries();
        for (var entry : sessions.entrySet()) collect(entry.getKey(), entry.getValue(), deltas);
        return save(deltas);
    }

    public CompletableFuture<Void> quit(UUID id) {
        Session session = sessions.remove(id);
        Map<UUID, Long> delta = new HashMap<>();
        if (session != null) collect(id, session, delta);
        return save(delta);
    }

    /** Capture final main-thread clock deltas, then retry failed writes after the queue drains. */
    public CompletableFuture<Void> stop() {
        var finalFlush = flush();
        sessions.clear();
        return finalFlush.handle((ignored, error) -> null)
                .thenCompose(ignored -> store.fence())
                .thenCompose(ignored -> save(drainRetries()));
    }

    private void collect(UUID id, Session session, Map<UUID, Long> delta) {
        long elapsed = elapsed(session);
        long newSeconds = elapsed - session.queuedSeconds;
        session.queuedSeconds = elapsed;
        if (newSeconds > 0) delta.merge(id, newSeconds, PlayerDataService::saturatingAdd);
    }

    private CompletableFuture<Void> save(Map<UUID, Long> delta) {
        if (delta.isEmpty()) return CompletableFuture.completedFuture(null);
        return store.addPlaytime(delta).whenComplete((ignored, error) -> {
            if (error != null) synchronized (retrySeconds) {
                delta.forEach((id, seconds) -> retrySeconds.merge(id, seconds, PlayerDataService::saturatingAdd));
            }
        });
    }

    private Map<UUID, Long> drainRetries() {
        synchronized (retrySeconds) {
            Map<UUID, Long> batch = new HashMap<>(retrySeconds); retrySeconds.clear(); return batch;
        }
    }

    private long elapsed(Session session) { return Math.max(0, (nanoClock.getAsLong() - session.startedNanos) / 1_000_000_000L); }
    private static long saturatingAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private static final class Session {
        final long startedNanos;
        volatile long initialPlaytime = -1;
        long queuedSeconds;
        Session(long startedNanos) { this.startedNanos = startedNanos; }
    }
}
