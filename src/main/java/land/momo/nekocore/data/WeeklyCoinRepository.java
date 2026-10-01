package land.momo.nekocore.data;

import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class WeeklyCoinRepository {
    public record Entry(UUID player, String name, long earned) {}
    public record Board(String period, List<Entry> entries) {}
    private final SqliteStore store;
    public WeeklyCoinRepository(SqliteStore store) { this.store = store; }
    public static String period(long millis) {
        return Instant.ofEpochMilli(millis).atZone(CommerceRepository.ZONE).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }
    /** Called within the SAME transaction as the source reward, never independently retried. */
    static void record(Connection c, UUID player, long coins, CoinChangeReason reason, long now) throws SQLException {
        if (!reason.countsForWeekly() || coins <= 0) return;
        String period = period(now); long old = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT earned_coins FROM weekly_coin_earnings WHERE period_start=? AND player_uuid=?")) {
            ps.setString(1, period); ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) old = rs.getLong(1); }
        }
        long total;
        try { total = Math.addExact(old, coins); } catch (ArithmeticException e) { throw new StorageException("amount-overflow"); }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO weekly_coin_earnings VALUES(?,?,?,?) ON CONFLICT(period_start,player_uuid) DO UPDATE SET earned_coins=excluded.earned_coins,updated_at=excluded.updated_at")) {
            ps.setString(1, period); ps.setString(2, player.toString()); ps.setLong(3, total); ps.setLong(4, now); ps.executeUpdate();
        }
    }
    public CompletableFuture<Board> current() { return store.query(c -> read(c, period(store.nowMillis()))); }
    public CompletableFuture<Board> history(String period) { return store.query(c -> read(c, period)); }
    private static Board read(Connection c, String period) throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT w.player_uuid,p.name,w.earned_coins FROM weekly_coin_earnings w JOIN players p ON p.uuid=w.player_uuid WHERE period_start=? AND earned_coins>0 ORDER BY earned_coins DESC,player_uuid ASC LIMIT 10")) {
            ps.setString(1, period);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) entries.add(new Entry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3))); }
        }
        return new Board(period, List.copyOf(entries));
    }
}
