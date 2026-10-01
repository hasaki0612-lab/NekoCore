package land.momo.nekocore.data;

import land.momo.nekocore.config.DailyTaskSettings;
import land.momo.nekocore.task.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** All rotation/progress/reward writes share NekoCore's single SQLite worker. */
public final class DailyTaskRepository {
    public record Advance(DailyTaskProgress progress, boolean changed, boolean completedNow,
                          long coins, long exp) {}
    private final SqliteStore store;
    public DailyTaskRepository(SqliteStore store) { this.store = store; }

    public CompletableFuture<DailyTaskRotation> ensure(LocalDate date, List<DailyTaskRotation.Entry> candidates) {
        List<DailyTaskRotation.Entry> copy = validate(candidates);
        return store.atomic(connection -> {
            DailyTaskRotation existing = readRotation(connection, date);
            if (existing != null) return existing;
            DailyTaskRotation created = new DailyTaskRotation(date, UUID.randomUUID(), copy);
            insertRotation(connection, created); return created;
        });
    }

    public CompletableFuture<DailyTaskRotation> reroll(LocalDate date, List<DailyTaskRotation.Entry> candidates) {
        List<DailyTaskRotation.Entry> copy = validate(candidates);
        return store.atomic(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM daily_task_rotations WHERE date=?")) {
                statement.setString(1, date.toString()); statement.executeUpdate();
            }
            DailyTaskRotation created = new DailyTaskRotation(date, UUID.randomUUID(), copy);
            insertRotation(connection, created); return created;
        });
    }

    public CompletableFuture<DailyTaskRotation> rotation(LocalDate date) {
        return store.query(connection -> readRotation(connection, date));
    }

    public CompletableFuture<Map<String, DailyTaskProgress>> progress(UUID player, DailyTaskRotation rotation) {
        return store.query(connection -> {
            Map<String,DailyTaskProgress> result = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT task_id,progress,completed,reward_given,extra_state
                    FROM player_daily_task_progress WHERE player_uuid=? AND date=? AND rotation_id=?
                    """)) {
                statement.setString(1, player.toString()); statement.setString(2, rotation.date().toString());
                statement.setString(3, rotation.id().toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getString(1), new DailyTaskProgress(rows.getString(1), rows.getLong(2),
                            rows.getBoolean(3), rows.getBoolean(4), rows.getString(5)));
                }
            }
            for (var entry : rotation.entries()) result.putIfAbsent(entry.taskId(), new DailyTaskProgress(entry.taskId(), 0, false, false, ""));
            return Map.copyOf(result);
        });
    }

    public CompletableFuture<Advance> advance(UUID player, DailyTaskRotation rotation, String taskId,
                                               long amount, String uniqueToken, long target,
                                               DailyTaskSettings.Reward reward) {
        return advance(player, rotation, taskId, amount, uniqueToken, target, reward, CoinChangeReason.DAILY_TASK);
    }
    public CompletableFuture<Advance> advance(UUID player, DailyTaskRotation rotation, String taskId,
                                               long amount, String uniqueToken, long target,
                                               DailyTaskSettings.Reward reward, CoinChangeReason reason) {
        if (amount < 0 || target < 1) throw new IllegalArgumentException("Invalid daily-task progress");
        if (!rotation.contains(taskId)) return CompletableFuture.completedFuture(new Advance(
                new DailyTaskProgress(taskId, 0, false, false, ""), false, false, 0, 0));
        return store.atomic(connection -> {
            if (!isCurrent(connection, rotation, taskId)) return new Advance(
                    new DailyTaskProgress(taskId, 0, false, false, ""), false, false, 0, 0);
            DailyTaskProgress old = readProgress(connection, player, rotation, taskId);
            if (old.rewardGiven()) return new Advance(old, false, false, 0, 0);
            LinkedHashSet<String> state = decode(old.extraState());
            if (uniqueToken != null && !uniqueToken.isBlank() && !state.add(uniqueToken))
                return new Advance(old, false, false, 0, 0);
            long next;
            try { next = Math.min(target, Math.addExact(old.progress(), amount)); }
            catch (ArithmeticException e) { next = target; }
            boolean completed = next >= target;
            boolean completedNow = completed && !old.rewardGiven();
            if (completedNow) {
                award(connection, player, reward);
                WeeklyCoinRepository.record(connection, player, reward.coins(), reason, store.nowMillis());
            }
            DailyTaskProgress updated = new DailyTaskProgress(taskId, next, completed,
                    old.rewardGiven() || completedNow, encode(state));
            writeProgress(connection, player, rotation, updated);
            return new Advance(updated, next != old.progress() || !updated.extraState().equals(old.extraState()), completedNow,
                    completedNow ? reward.coins() : 0, completedNow ? reward.exp() : 0);
        }, player);
    }

    public CompletableFuture<Void> reset(UUID player, DailyTaskRotation rotation) {
        return store.atomic(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM player_daily_task_progress WHERE player_uuid=? AND date=? AND rotation_id=?
                    """)) {
                statement.setString(1, player.toString()); statement.setString(2, rotation.date().toString());
                statement.setString(3, rotation.id().toString()); statement.executeUpdate();
            }
            return null;
        });
    }

    private static List<DailyTaskRotation.Entry> validate(List<DailyTaskRotation.Entry> entries) {
        List<DailyTaskRotation.Entry> copy = List.copyOf(entries);
        if (copy.isEmpty() || copy.stream().map(DailyTaskRotation.Entry::taskId).distinct().count() != copy.size())
            throw new IllegalArgumentException("Daily task candidates must be unique");
        return copy;
    }
    private void insertRotation(Connection connection, DailyTaskRotation rotation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO daily_task_rotations VALUES(?,?,?,?,?,?)")) {
            for (var entry : rotation.entries()) {
                statement.setString(1, rotation.date().toString()); statement.setString(2, rotation.id().toString());
                statement.setString(3, entry.difficulty().key()); statement.setInt(4, entry.index());
                statement.setString(5, entry.taskId()); statement.setLong(6, store.nowMillis()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }
    private static DailyTaskRotation readRotation(Connection connection, LocalDate date) throws SQLException {
        UUID id = null; List<DailyTaskRotation.Entry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT rotation_id,difficulty,slot_index,task_id FROM daily_task_rotations
                WHERE date=? ORDER BY CASE difficulty WHEN 'easy' THEN 0 WHEN 'normal' THEN 1 ELSE 2 END,slot_index
                """)) {
            statement.setString(1, date.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID rowId = UUID.fromString(rows.getString(1));
                    if (id == null) id = rowId; else if (!id.equals(rowId)) throw new SQLException("Mixed daily rotation IDs");
                    entries.add(new DailyTaskRotation.Entry(DailyTaskDifficulty.parse(rows.getString(2)), rows.getInt(3), rows.getString(4)));
                }
            }
        }
        return id == null ? null : new DailyTaskRotation(date, id, entries);
    }
    private static boolean isCurrent(Connection connection, DailyTaskRotation rotation, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM daily_task_rotations WHERE date=? AND rotation_id=? AND task_id=?
                """)) {
            statement.setString(1, rotation.date().toString()); statement.setString(2, rotation.id().toString()); statement.setString(3, taskId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }
    private static DailyTaskProgress readProgress(Connection connection, UUID player, DailyTaskRotation rotation, String taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT progress,completed,reward_given,extra_state FROM player_daily_task_progress
                WHERE player_uuid=? AND date=? AND rotation_id=? AND task_id=?
                """)) {
            statement.setString(1, player.toString()); statement.setString(2, rotation.date().toString());
            statement.setString(3, rotation.id().toString()); statement.setString(4, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return new DailyTaskProgress(taskId, 0, false, false, "");
                return new DailyTaskProgress(taskId, rows.getLong(1), rows.getBoolean(2), rows.getBoolean(3), rows.getString(4));
            }
        }
    }
    private void writeProgress(Connection connection, UUID player, DailyTaskRotation rotation, DailyTaskProgress progress) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_daily_task_progress(player_uuid,date,rotation_id,task_id,progress,completed,reward_given,extra_state,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(player_uuid,date,rotation_id,task_id) DO UPDATE SET
                progress=excluded.progress,completed=excluded.completed,reward_given=excluded.reward_given,
                extra_state=excluded.extra_state,updated_at=excluded.updated_at
                """)) {
            statement.setString(1, player.toString()); statement.setString(2, rotation.date().toString());
            statement.setString(3, rotation.id().toString()); statement.setString(4, progress.taskId());
            statement.setLong(5, progress.progress()); statement.setBoolean(6, progress.completed());
            statement.setBoolean(7, progress.rewardGiven()); statement.setString(8, progress.extraState());
            statement.setLong(9, store.nowMillis()); statement.executeUpdate();
        }
    }
    private void award(Connection connection, UUID player, DailyTaskSettings.Reward reward) throws SQLException {
        long coins, exp;
        try (PreparedStatement statement = connection.prepareStatement("SELECT coins,exp FROM players WHERE uuid=?")) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new StorageException("unknown-player"); coins = rows.getLong(1); exp = rows.getLong(2);
            }
        }
        try { coins = Math.addExact(coins, reward.coins()); exp = Math.addExact(exp, reward.exp()); }
        catch (ArithmeticException e) { throw new StorageException("amount-overflow"); }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE players SET coins=?,exp=?,level=? WHERE uuid=?")) {
            statement.setLong(1, coins); statement.setLong(2, exp); statement.setInt(3, store.levelFor(exp));
            statement.setString(4, player.toString()); statement.executeUpdate();
        }
    }
    private static LinkedHashSet<String> decode(String encoded) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (encoded != null && !encoded.isBlank()) result.addAll(Arrays.asList(encoded.split("\\|", -1)));
        result.remove(""); return result;
    }
    private static String encode(Set<String> values) {
        String result = String.join("|", values);
        if (result.length() > 16_384) throw new IllegalArgumentException("Daily task state too large");
        return result;
    }
}
