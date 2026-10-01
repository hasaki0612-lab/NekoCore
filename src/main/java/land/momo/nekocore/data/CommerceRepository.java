package land.momo.nekocore.data;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** SQLite half of inventory exchanges. All parameters are immutable bytes/scalars, never Bukkit objects. */
public final class CommerceRepository {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public record Bag(int capacity, long revision, byte[] contents) {
        public Bag { contents = contents.clone(); }
        @Override public byte[] contents() { return contents.clone(); }
    }
    public record Offer(int slot, String enchantment, int level, long price, boolean maximum) {}
    public record Batch(String id, String period, List<Offer> offers) {
        public Batch { offers = List.copyOf(offers); }
    }
    public record Quota(String period, int bought, int sold) {}
    public record Exchange(UUID id, UUID player, UUID world, String kind, byte[] before, byte[] after,
                           long coinsDelta, String product, String period, String direction, int quantity,
                           byte[] bagBefore, byte[] bagAfter, long bagRevision) {
        public Exchange {
            before = before.clone(); after = after.clone();
            bagBefore = bagBefore == null ? null : bagBefore.clone();
            bagAfter = bagAfter == null ? null : bagAfter.clone();
            if (before.length > 8_388_608 || after.length > 8_388_608) throw new IllegalArgumentException("Inventory snapshot too large");
        }
        @Override public byte[] before() { return before.clone(); }
        @Override public byte[] after() { return after.clone(); }
        @Override public byte[] bagBefore() { return bagBefore == null ? null : bagBefore.clone(); }
        @Override public byte[] bagAfter() { return bagAfter == null ? null : bagAfter.clone(); }
    }
    private final SqliteStore store;
    public CommerceRepository(SqliteStore store) { this.store = store; }
    public static String day(long millis) { return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().toString(); }
    public static String enchantDay(long millis) { return Instant.ofEpochMilli(millis).atZone(ZONE).minusHours(4).toLocalDate().toString(); }

    public CompletableFuture<Bag> bag(UUID player, int capacity) {
        if (capacity != 18 && capacity != 27 && capacity != 36) throw new IllegalArgumentException("bag capacity");
        return store.atomic(c -> {
            requireNoPending(c, player);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO bags(player_uuid,capacity) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET capacity=MAX(capacity,excluded.capacity)")) {
                ps.setString(1, player.toString()); ps.setInt(2, capacity); ps.executeUpdate();
            }
            return readBag(c, player);
        });
    }
    /** Capacity is a permanent unlock, including offline/admin level changes, not only opening /bag. */
    public CompletableFuture<Void> unlock(UUID player, int capacity) {
        if (capacity != 18 && capacity != 27 && capacity != 36) throw new IllegalArgumentException("bag capacity");
        return store.atomic(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO bags(player_uuid,capacity) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET capacity=MAX(capacity,excluded.capacity)")) {
                ps.setString(1, player.toString()); ps.setInt(2, capacity); ps.executeUpdate();
            }
            return null;
        });
    }
    public CompletableFuture<Void> unlockAll(int level27, int level36) {
        return store.atomic(c -> {
            unlockAll(c, level27, level36);
            return null;
        });
    }
    static void unlockAll(Connection c, int level27, int level36) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bags(player_uuid,capacity) SELECT uuid,CASE WHEN level>=? THEN 36 WHEN level>=? THEN 27 ELSE 18 END FROM players WHERE 1 ON CONFLICT(player_uuid) DO UPDATE SET capacity=MAX(capacity,excluded.capacity)")) {
            ps.setInt(1, level36); ps.setInt(2, level27); ps.executeUpdate();
        }
    }
    public CompletableFuture<Quota> quota(UUID player, String product, String enchantBatch) {
        return store.atomic(c -> {
            String period = enchantBatch == null ? currentDay(c) : "enchant:" + enchantBatch;
            return new Quota(period, used(c, player, period, product, "buy"), used(c, player, period, product, "sell"));
        });
    }
    /** Candidate generation happens on the main thread. Only one worker can commit a daily batch. */
    public CompletableFuture<Batch> ensureBatch(List<Offer> candidates, boolean force) {
        List<Offer> copy = List.copyOf(candidates);
        if (copy.size() != 8 || copy.stream().filter(Offer::maximum).count() != 2
                || copy.stream().map(Offer::slot).distinct().count() != 8
                || copy.stream().anyMatch(o -> o.slot() < 0 || o.slot() > 7 || o.price() <= 0 || o.level() <= 0))
            throw new IllegalArgumentException("A batch requires two maximum and six normal books");
        return store.atomic(c -> {
            Batch old = latestBatch(c);
            String period = enchantDay(store.nowMillis());
            if (old != null && old.period().compareTo(period) >= 0 && !force) return old;
            if (old != null && old.period().compareTo(period) > 0) period = old.period();
            String id = UUID.randomUUID().toString();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO enchant_batches VALUES(?,?,?)")) {
                ps.setString(1, id); ps.setString(2, period); ps.setLong(3, store.nowMillis()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO enchant_offers VALUES(?,?,?,?,?,?)")) {
                for (Offer offer : copy) {
                    ps.setString(1, id); ps.setInt(2, offer.slot()); ps.setString(3, offer.enchantment());
                    ps.setInt(4, offer.level()); ps.setLong(5, offer.price()); ps.setBoolean(6, offer.maximum()); ps.addBatch();
                }
                ps.executeBatch();
            }
            return new Batch(id, period, copy);
        });
    }
    public CompletableFuture<Batch> batch() { return store.query(CommerceRepository::latestBatch); }

    public CompletableFuture<Exchange> prepareStore(Exchange exchange, long unitPrice, int limit, boolean enchanted) {
        return store.atomic(c -> {
            validate(exchange, "store");
            requireNoPending(c, exchange.player());
            if (exchange.quantity() < 1 || unitPrice < 0 || limit < 0) throw new StorageException("invalid-amount");
            long cost;
            try { cost = Math.multiplyExact(unitPrice, exchange.quantity()); }
            catch (ArithmeticException e) { throw new StorageException("amount-overflow"); }
            if (!Set.of("buy", "sell").contains(exchange.direction())
                    || exchange.coinsDelta() != (exchange.direction().equals("buy") ? -cost : cost))
                throw new IllegalArgumentException("Invalid exchange delta");
            if (enchanted) {
                Batch batch = latestBatch(c);
                if (batch == null || !exchange.period().equals("enchant:" + batch.id())
                        || batch.period().compareTo(enchantDay(store.nowMillis())) < 0)
                    throw new StorageException("store-expired");
                Offer offer = batch.offers().stream().filter(o -> exchange.product().equals("enchant-" + o.slot())).findFirst()
                        .orElseThrow(() -> new StorageException("store-expired"));
                if (!exchange.direction().equals("buy") || unitPrice != offer.price() || limit != 1 || exchange.quantity() != 1)
                    throw new StorageException("store-expired");
            } else if (!exchange.period().equals(currentDay(c))) throw new StorageException("store-expired");
            int used = used(c, exchange.player(), exchange.period(), exchange.product(), exchange.direction());
            if ((long) used + exchange.quantity() > limit) throw new StorageException("store-limit");
            changeCoins(c, exchange.player(), exchange.coinsDelta());
            setQuota(c, exchange, used + exchange.quantity());
            insert(c, exchange);
            return exchange;
        }, exchange.player());
    }
    public CompletableFuture<Exchange> prepareBag(Exchange exchange) {
        return store.atomic(c -> {
            validate(exchange, "bag"); requireNoPending(c, exchange.player());
            Bag bag = readBag(c, exchange.player());
            if (bag.revision() != exchange.bagRevision() || !Arrays.equals(bag.contents(), exchange.bagBefore()))
                throw new StorageException("exchange-stale");
            if (exchange.coinsDelta() != 0 || exchange.quantity() != 0 || exchange.bagAfter() == null
                    || exchange.bagAfter().length > 8_388_608) throw new IllegalArgumentException("Invalid bag exchange");
            writeBag(c, exchange.player(), exchange.bagAfter());
            insert(c, exchange);
            return exchange;
        });
    }
    public CompletableFuture<Void> commit(UUID id) {
        return commit(id, CoinChangeReason.STORE_SELL);
    }
    public CompletableFuture<Void> commit(UUID id, CoinChangeReason reason) {
        return store.atomic(c -> {
            // Award only the prepared -> committed transition, in the same transaction.
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM inventory_exchanges WHERE exchange_id=? AND state='prepared'")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString("kind").equals("store") && rs.getString("direction").equals("sell"))
                        WeeklyCoinRepository.record(c, UUID.fromString(rs.getString("player_uuid")), rs.getLong("coins_delta"), reason, store.nowMillis());
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE inventory_exchanges SET state='committed' WHERE exchange_id=? AND state='prepared'")) {
                ps.setString(1, id.toString()); ps.executeUpdate();
            }
            return null;
        });
    }
    /** Idempotent compensation. Never restores an entire old player row over concurrent changes. */
    public CompletableFuture<Void> rollback(Exchange exchange) {
        return store.atomic(c -> {
            if (!"prepared".equals(state(c, exchange.id()))) return null;
            if (exchange.kind().equals("store")) {
                changeCoins(c, exchange.player(), Math.negateExact(exchange.coinsDelta()));
                int old = used(c, exchange.player(), exchange.period(), exchange.product(), exchange.direction());
                if (old < exchange.quantity()) throw new StorageException("exchange-recovery");
                setQuota(c, exchange, old - exchange.quantity());
            } else {
                Bag current = readBag(c, exchange.player());
                if (current.revision() != exchange.bagRevision() + 1 || !Arrays.equals(current.contents(), exchange.bagAfter()))
                    throw new StorageException("exchange-recovery");
                writeBag(c, exchange.player(), exchange.bagBefore());
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE inventory_exchanges SET state='rolled_back' WHERE exchange_id=?")) {
                ps.setString(1, exchange.id().toString()); ps.executeUpdate();
            }
            return null;
        }, exchange.player());
    }
    public CompletableFuture<Optional<Exchange>> pending(UUID player) {
        return store.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM inventory_exchanges WHERE player_uuid=? AND state='prepared'")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(fromRow(rs)) : Optional.empty(); }
            }
        });
    }
    public record Recovery(Exchange exchange, boolean committed) {}
    public CompletableFuture<Optional<Recovery>> recovery(UUID player) {
        return store.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM inventory_exchanges WHERE player_uuid=? AND state!='rolled_back' ORDER BY rowid DESC LIMIT 1")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(new Recovery(fromRow(rs), rs.getString("state").equals("committed"))) : Optional.empty(); }
            }
        });
    }
    public CompletableFuture<String> status(UUID player) {
        return store.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT state,COUNT(*) FROM inventory_exchanges WHERE player_uuid=? GROUP BY state")) {
                ps.setString(1, player.toString());
                List<String> values = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) values.add(rs.getString(1) + "=" + rs.getInt(2)); }
                return String.join(", ", values);
            }
        });
    }
    public CompletableFuture<Void> resetQuotas(UUID player) {
        return store.atomic(c -> {
            requireNoPending(c, player);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM store_quotas WHERE player_uuid=?")) {
                ps.setString(1, player.toString()); ps.executeUpdate();
            }
            return null;
        });
    }
    private static void validate(Exchange x, String kind) {
        if (!x.kind().equals(kind)) throw new IllegalArgumentException("exchange kind");
        Objects.requireNonNull(x.id()); Objects.requireNonNull(x.player()); Objects.requireNonNull(x.world());
    }
    static void requireNoPending(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM inventory_exchanges WHERE player_uuid=? AND state='prepared'")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) throw new StorageException("exchange-recovery"); }
        }
    }
    private String currentDay(Connection c) throws SQLException {
        String period = day(store.nowMillis());
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO store_periods VALUES('daily',?) ON CONFLICT(kind) DO UPDATE SET period=MAX(period,excluded.period)")) {
            ps.setString(1, period); ps.executeUpdate();
        }
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT period FROM store_periods WHERE kind='daily'")) {
            rs.next(); return rs.getString(1);
        }
    }
    private static int used(Connection c, UUID player, String period, String product, String direction) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT quantity FROM store_quotas WHERE player_uuid=? AND period=? AND product_id=? AND direction=?")) {
            ps.setString(1, player.toString()); ps.setString(2, period); ps.setString(3, product); ps.setString(4, direction);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }
    private static void setQuota(Connection c, Exchange x, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO store_quotas VALUES(?,?,?,?,?) ON CONFLICT(player_uuid,period,product_id,direction) DO UPDATE SET quantity=excluded.quantity")) {
            ps.setString(1, x.player().toString()); ps.setString(2, x.period()); ps.setString(3, x.product()); ps.setString(4, x.direction()); ps.setInt(5, quantity); ps.executeUpdate();
        }
    }
    private static void changeCoins(Connection c, UUID player, long delta) throws SQLException {
        long balance;
        try (PreparedStatement ps = c.prepareStatement("SELECT coins FROM players WHERE uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new StorageException("unknown-player"); balance = rs.getLong(1); }
        }
        long next;
        try { next = Math.addExact(balance, delta); }
        catch (ArithmeticException e) { throw new StorageException("amount-overflow"); }
        if (next < 0) throw new StorageException("not-enough-coins");
        try (PreparedStatement ps = c.prepareStatement("UPDATE players SET coins=? WHERE uuid=?")) {
            ps.setLong(1, next); ps.setString(2, player.toString()); ps.executeUpdate();
        }
    }
    private static Bag readBag(Connection c, UUID player) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT capacity,revision,contents FROM bags WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StorageException("loading");
                return new Bag(rs.getInt(1), rs.getLong(2), rs.getBytes(3));
            }
        }
    }
    private static void writeBag(Connection c, UUID player, byte[] contents) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE bags SET contents=?,revision=revision+1 WHERE player_uuid=?")) {
            ps.setBytes(1, contents); ps.setString(2, player.toString());
            if (ps.executeUpdate() != 1) throw new StorageException("loading");
        }
    }
    private static Batch latestBatch(Connection c) throws SQLException {
        String id, period;
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT batch_id,period FROM enchant_batches ORDER BY rowid DESC LIMIT 1")) {
            if (!rs.next()) return null; id = rs.getString(1); period = rs.getString(2);
        }
        List<Offer> offers = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT slot,enchantment,level,price,maximum FROM enchant_offers WHERE batch_id=? ORDER BY slot")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) offers.add(new Offer(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getBoolean(5))); }
        }
        return new Batch(id, period, offers);
    }
    private static String state(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT state FROM inventory_exchanges WHERE exchange_id=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }
    private void insert(Connection c, Exchange x) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO inventory_exchanges VALUES(?,?,?,?,'prepared',?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, x.id().toString()); ps.setString(2, x.player().toString()); ps.setString(3, x.world().toString()); ps.setString(4, x.kind());
            ps.setLong(5, store.nowMillis()); ps.setBytes(6, x.before()); ps.setBytes(7, x.after()); ps.setLong(8, x.coinsDelta());
            ps.setString(9, x.product()); ps.setString(10, x.period()); ps.setString(11, x.direction()); ps.setInt(12, x.quantity());
            ps.setBytes(13, x.bagBefore()); ps.setBytes(14, x.bagAfter()); ps.setLong(15, x.bagRevision()); ps.executeUpdate();
        }
    }
    private static Exchange fromRow(ResultSet rs) throws SQLException {
        return new Exchange(UUID.fromString(rs.getString("exchange_id")), UUID.fromString(rs.getString("player_uuid")),
                UUID.fromString(rs.getString("world_uuid")), rs.getString("kind"), rs.getBytes("inventory_before"), rs.getBytes("inventory_after"),
                rs.getLong("coins_delta"), rs.getString("product_id"), rs.getString("period"), rs.getString("direction"), rs.getInt("quantity"),
                rs.getBytes("bag_before"), rs.getBytes("bag_after"), rs.getLong("bag_revision"));
    }
}
