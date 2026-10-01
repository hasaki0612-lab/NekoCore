package land.momo.nekocore.data;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Permanent purchases and a single cosmetic selection. No permissions plugin is required. */
public final class TitleRepository {
    public record Titles(Set<String> owned, String equipped) {
        public Titles { owned = Set.copyOf(owned); equipped = Objects.requireNonNullElse(equipped, ""); }
    }
    private final SqliteStore store;
    private final ConcurrentMap<UUID, Titles> cache = new ConcurrentHashMap<>();
    public TitleRepository(SqliteStore store) { this.store = store; }
    public Titles cached(UUID player) { return cache.get(player); }
    public void forget(UUID player) { cache.remove(player); }
    public CompletableFuture<Titles> load(UUID player) {
        return store.query(c -> read(c, player)).thenApply(t -> publish(player, t));
    }
    public CompletableFuture<Titles> purchase(UUID player, String title, long price) {
        if (price < 0) return CompletableFuture.failedFuture(new StorageException("invalid-amount"));
        return store.atomic(c -> {
            CommerceRepository.requireNoPending(c, player);
            Titles current = read(c, player);
            if (current.owned().contains(title)) throw new StorageException("title-already-owned");
            try (PreparedStatement ps = c.prepareStatement("UPDATE players SET coins=coins-? WHERE uuid=? AND coins>=?")) {
                ps.setLong(1, price); ps.setString(2, player.toString()); ps.setLong(3, price);
                if (ps.executeUpdate() != 1) throw new StorageException("not-enough-coins");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO owned_titles VALUES(?,?,?,?)")) {
                ps.setString(1, player.toString()); ps.setString(2, title); ps.setLong(3, store.nowMillis()); ps.setLong(4, price); ps.executeUpdate();
            }
            equip(c, player, title);
            return read(c, player);
        }, player).thenApply(t -> publish(player, t));
    }
    public CompletableFuture<Titles> equip(UUID player, String title) {
        return store.atomic(c -> {
            if (!title.isEmpty() && !read(c, player).owned().contains(title)) throw new StorageException("title-not-owned");
            equip(c, player, title);
            return read(c, player);
        }).thenApply(t -> publish(player, t));
    }
    private Titles publish(UUID player, Titles titles) { cache.put(player, titles); return titles; }
    private static void equip(Connection c, UUID player, String title) throws SQLException {
        if (title.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM equipped_titles WHERE player_uuid=?")) {
                ps.setString(1, player.toString()); ps.executeUpdate();
            }
        } else try (PreparedStatement ps = c.prepareStatement("INSERT INTO equipped_titles VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET title_id=excluded.title_id")) {
            ps.setString(1, player.toString()); ps.setString(2, title); ps.executeUpdate();
        }
    }
    private static Titles read(Connection c, UUID player) throws SQLException {
        Set<String> owned = new HashSet<>(); String active = "";
        try (PreparedStatement ps = c.prepareStatement("SELECT title_id FROM owned_titles WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) owned.add(rs.getString(1)); }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT title_id FROM equipped_titles WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) active = rs.getString(1); }
        }
        return new Titles(owned, active);
    }
}
