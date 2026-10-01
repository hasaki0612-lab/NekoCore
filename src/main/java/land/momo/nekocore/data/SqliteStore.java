package land.momo.nekocore.data;

import land.momo.nekocore.model.Home;
import land.momo.nekocore.model.LevelCurve;
import land.momo.nekocore.model.Profile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Only this executor owns the JDBC connection. No Bukkit objects cross this boundary. */
public final class SqliteStore {
    public static final int SCHEMA_VERSION = 5;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "NekoCore-SQLite"));
    private final ConcurrentMap<UUID, Profile> cache = new ConcurrentHashMap<>();
    private final Logger logger;
    private final Clock clock;
    private final ConcurrentMap<UUID, String> lastCheckins = new ConcurrentHashMap<>();
    private final Thread shutdownHook;
    private Connection connection;
    private LevelCurve curve;
    private boolean closing;
    private volatile java.util.function.Consumer<Profile> levelListener = ignored -> {};

    public SqliteStore(Logger logger) {
        this(logger, Clock.systemUTC());
    }
    public SqliteStore(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
        shutdownHook = new Thread(() -> {
            // Paper's shutdown hook may still be invoking onDisable. Do not close early and
            // reject its final save: hooks run concurrently and their order is unspecified.
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS))
                    logger.severe("NekoCore 数据库关停超过 30 秒，部分写入可能尚未完成。");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "NekoCore-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public CompletableFuture<Void> open(Path path, LevelCurve initialCurve) {
        return submit(() -> {
            Files.createDirectories(path.toAbsolutePath().getParent());
            // Instantiate the bundled driver directly, avoiding other plugins' registered JDBC drivers.
            connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + path.toAbsolutePath(), new Properties());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=FULL");
                int version;
                try (ResultSet rs = s.executeQuery("PRAGMA user_version")) { rs.next(); version = rs.getInt(1); }
                if (version > SCHEMA_VERSION) throw new SQLException("数据库版本高于当前插件支持的版本，请勿降级覆盖。");
                for (int next = version + 1; next <= SCHEMA_VERSION; next++) {
                    String resource = switch (next) {
                        case 1 -> "V1__initial.sql";
                        case 2 -> "V2__daily_checkins.sql";
                        case 3 -> "V3__shops_bags_titles.sql";
                        case 4 -> "V4__daily_tasks.sql";
                        default -> "V5__weekly_coin_earnings.sql";
                    };
                    int targetVersion = next;
                    try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/db/migration/" + resource))) {
                        String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        transaction(() -> {
                            for (String sql : migration.split(";")) if (!sql.isBlank()) s.execute(sql);
                            s.execute("PRAGMA user_version=" + targetVersion);
                            return null;
                        });
                        logger.info("SQLite migration V" + targetVersion + " committed (existing data retained)");
                    }
                }
            }
            recalculate(initialCurve);
            return null;
        });
    }

    public Profile cached(UUID id) { return cache.get(id); }
    /** Resolves only known UUID/name records; never asks Bukkit or a remote identity service. */
    public CompletableFuture<Profile> knownPlayer(String target) { return submit(() -> resolve(target)); }

    public CompletableFuture<Profile> join(UUID id, String name, long now) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO players(uuid,name,first_join,last_join) VALUES(?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_join=excluded.last_join
                    """)) {
                ps.setString(1, id.toString()); ps.setString(2, name); ps.setLong(3, now); ps.setLong(4, now);
                ps.executeUpdate();
            }
            lastCheckins.put(id, lastCheckin(id));
            return publish(read(id));
        });
    }

    /** Last committed date; empty string means loaded and never claimed, null means not loaded. */
    public String cachedCheckin(UUID id) { return lastCheckins.get(id); }
    private String lastCheckin(UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(claim_date) FROM daily_checkins WHERE player_uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return Objects.toString(rs.getString(1), ""); }
        }
    }
    public CompletableFuture<DailyClaim> checkin(UUID id, ZoneId zone, long coins, long experience) {
        return submit(() -> {
            // Resolve today's date at execution time, including queue delays across midnight.
            String date = LocalDate.now(clock.withZone(zone)).toString();
            DailyClaim result = transaction(() -> {
                Profile old = read(id);
                String last = lastCheckin(id);
                if (last.compareTo(date) >= 0) return new DailyClaim(old, false, last, old.level());
                long balance = add(old.coins(), coins), exp = add(old.exp(), experience);
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO daily_checkins VALUES(?,?,?,?,?,?)")) {
                    ps.setString(1, id.toString()); ps.setString(2, date); ps.setLong(3, clock.millis()); ps.setString(4, zone.getId());
                    ps.setLong(5, coins); ps.setLong(6, experience); ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET coins=?,exp=?,level=? WHERE uuid=?")) {
                    ps.setLong(1, balance); ps.setLong(2, exp); ps.setInt(3, curve.progress(exp).level()); ps.setString(4, id.toString()); ps.executeUpdate();
                }
                WeeklyCoinRepository.record(connection, id, coins, CoinChangeReason.CHECKIN, clock.millis());
                return new DailyClaim(read(id), true, date, old.level());
            });
            lastCheckins.put(id, result.date());
            publish(result.profile());
            return result;
        });
    }
    public record DailyClaim(Profile profile, boolean claimed, String date, int previousLevel) {}

    public CompletableFuture<Profile> coins(String target, String action, long amount) {
        return submit(() -> {
            Profile result = transaction(() -> {
                Profile old = resolve(target);
                CommerceRepository.requireNoPending(connection, old.uuid());
                long value = switch (action) {
                    case "add" -> add(old.coins(), amount);
                    case "set" -> nonNegative(amount);
                    case "take" -> old.coins() - Math.min(old.coins(), nonNegative(amount));
                    default -> throw new IllegalArgumentException("Unknown coins operation");
                };
                try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET coins=? WHERE uuid=?")) {
                    ps.setLong(1, value); ps.setString(2, old.uuid().toString()); ps.executeUpdate();
                }
                return read(old.uuid());
            });
            return publish(result);
        });
    }

    public CompletableFuture<Profile> experience(String target, String action, long amount) {
        return submit(() -> {
            Profile result = transaction(() -> {
                Profile old = resolve(target);
                long total = switch (action) {
                    case "add" -> add(old.exp(), amount);
                    case "set" -> nonNegative(amount);
                    default -> throw new IllegalArgumentException("Unknown experience operation");
                };
                try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET exp=?,level=? WHERE uuid=?")) {
                    ps.setLong(1, total); ps.setInt(2, curve.progress(total).level());
                    ps.setString(3, old.uuid().toString()); ps.executeUpdate();
                }
                return read(old.uuid());
            });
            return publish(result);
        });
    }

    /** Atomic AFK_POOL reward. This does not change the multiplier of any other EXP source. */
    public CompletableFuture<Reward> afkReward(UUID player, long experience, long coins) {
        return submit(() -> {
            Reward result = transaction(() -> {
                Profile old = read(player);
                long nextExp = add(old.exp(), experience), nextCoins = add(old.coins(), coins);
                try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET coins=?,exp=?,level=? WHERE uuid=?")) {
                    ps.setLong(1, nextCoins); ps.setLong(2, nextExp); ps.setInt(3, curve.progress(nextExp).level());
                    ps.setString(4, player.toString()); ps.executeUpdate();
                }
                WeeklyCoinRepository.record(connection, player, coins, CoinChangeReason.AFK_POOL, clock.millis());
                return new Reward(read(player), old.level(), experience, coins);
            });
            publish(result.profile());
            return result;
        });
    }
    public record Reward(Profile profile, int previousLevel, long exp, long coins) {}

    public CompletableFuture<Profile> togglePrivacy(UUID id) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET show_ip=1-show_ip WHERE uuid=?")) {
                ps.setString(1, id.toString()); ps.executeUpdate();
            }
            return publish(read(id));
        });
    }

    public CompletableFuture<Void> addPlaytime(Map<UUID, Long> seconds) {
        Map<UUID, Long> batch = Map.copyOf(seconds);
        return submit(() -> {
            List<Profile> changed = transaction(() -> {
                List<Profile> profiles = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET playtime_seconds=? WHERE uuid=?")) {
                    for (var entry : batch.entrySet()) {
                        Profile old = read(entry.getKey());
                        ps.setLong(1, add(old.playtimeSeconds(), entry.getValue()));
                        ps.setString(2, entry.getKey().toString()); ps.executeUpdate();
                        profiles.add(read(entry.getKey()));
                    }
                }
                return profiles;
            });
            changed.forEach(this::publish);
            return null;
        });
    }

    public CompletableFuture<Void> saveHome(Home home) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO homes(player_uuid,world_uuid,world_name,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?)
                    ON CONFLICT(player_uuid,world_uuid) DO UPDATE SET world_name=excluded.world_name,
                    x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch
                    """)) {
                ps.setString(1, home.playerUuid().toString()); ps.setString(2, home.worldUuid().toString());
                ps.setString(3, home.worldName()); ps.setDouble(4, home.x()); ps.setDouble(5, home.y());
                ps.setDouble(6, home.z()); ps.setFloat(7, home.yaw()); ps.setFloat(8, home.pitch()); ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<Home>> home(UUID player, UUID world, String worldName) {
        return submit(() -> {
            String query = world != null ? "world_uuid=?" : "world_name=? COLLATE NOCASE";
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM homes WHERE player_uuid=? AND " + query)) {
                ps.setString(1, player.toString()); ps.setString(2, world != null ? world.toString() : worldName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Home home = new Home(player, UUID.fromString(rs.getString("world_uuid")), rs.getString("world_name"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                    return Optional.of(home);
                }
            }
        });
    }

    public CompletableFuture<Void> changeCurve(LevelCurve next) {
        return submit(() -> { recalculate(next); return null; });
    }
    public CompletableFuture<Void> changeCurveAndBags(LevelCurve next, int level27, int level36) {
        return submit(() -> { recalculate(next, new int[]{level27, level36}); return null; });
    }

    public CompletableFuture<Void> fence() { return submit(() -> null); }

    private void recalculate(LevelCurve next) throws Exception {
        recalculate(next, null);
    }
    private void recalculate(LevelCurve next, int[] bagLevels) throws Exception {
        List<Profile> changed = transaction(() -> {
            List<UUID> ids = new ArrayList<>();
            try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT uuid FROM players")) {
                while (rs.next()) ids.add(UUID.fromString(rs.getString(1)));
            }
            List<Profile> profiles = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET level=? WHERE uuid=?")) {
                for (UUID id : ids) {
                    Profile old = read(id);
                    ps.setInt(1, next.progress(old.exp()).level()); ps.setString(2, id.toString()); ps.addBatch();
                }
                ps.executeBatch();
            }
            if (bagLevels != null) CommerceRepository.unlockAll(connection, bagLevels[0], bagLevels[1]);
            for (UUID id : cache.keySet()) profiles.add(read(id));
            return profiles;
        });
        curve = next;
        changed.forEach(this::publish);
    }

    private Profile resolve(String target) throws SQLException {
        UUID id = null;
        try { id = UUID.fromString(target); } catch (IllegalArgumentException ignored) { }
        if (id != null) return read(id);
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE name=? COLLATE NOCASE LIMIT 2")) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StorageException("unknown-player");
                Profile profile = fromRow(rs);
                if (rs.next()) throw new StorageException("ambiguous-player");
                return profile;
            }
        }
    }

    private Profile read(UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StorageException("unknown-player");
                return fromRow(rs);
            }
        }
    }

    private Profile fromRow(ResultSet rs) throws SQLException {
        return new Profile(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getLong("coins"),
                rs.getInt("level"), rs.getLong("exp"), rs.getLong("playtime_seconds"), rs.getLong("first_join"),
                rs.getLong("last_join"), rs.getBoolean("show_ip"));
    }

    private Profile publish(Profile profile) {
        Profile previous = cache.put(profile.uuid(), profile);
        if (previous == null || previous.level() != profile.level()) levelListener.accept(profile);
        return profile;
    }
    /** Listener runs on the DB worker: integrations must marshal back to the server thread. */
    public void levelListener(java.util.function.Consumer<Profile> listener) { levelListener = Objects.requireNonNull(listener); }

    // New repositories share the existing connection and serialized queue, never open another writer.
    <T> CompletableFuture<T> query(DatabaseWork<T> work) { return submit(() -> work.run(connection)); }
    <T> CompletableFuture<T> atomic(DatabaseWork<T> work, UUID... affectedProfiles) {
        return submit(() -> {
            T result = transaction(() -> work.run(connection));
            for (UUID id : affectedProfiles) publish(read(id));
            return result;
        });
    }
    long nowMillis() { return clock.millis(); }
    int levelFor(long experience) { return curve.progress(experience).level(); }
    @FunctionalInterface interface DatabaseWork<T> { T run(Connection connection) throws Exception; }
    private static long nonNegative(long n) { if (n < 0) throw new StorageException("invalid-amount"); return n; }
    private static long add(long current, long amount) {
        nonNegative(amount);
        try { return Math.addExact(current, amount); }
        catch (ArithmeticException e) { throw new StorageException("amount-overflow"); }
    }

    private <T> T transaction(SqlTask<T> task) throws Exception {
        connection.setAutoCommit(false);
        try { T value = task.run(); connection.commit(); return value; }
        catch (Exception e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    private synchronized <T> CompletableFuture<T> submit(SqlTask<T> task) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Database is closing"));
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try { future.complete(task.run()); }
            catch (Exception | LinkageError e) { future.completeExceptionally(e); }
        });
        return future;
    }

    /** Drains accepted writes and closes off-thread. The JVM hook also handles Paper's System.exit. */
    public synchronized void closeAsync() {
        if (closing) return;
        closing = true;
        executor.execute(() -> {
            try { if (connection != null) connection.close(); }
            catch (SQLException e) { logger.log(Level.SEVERE, "SQLite 关闭失败", e); }
            finally {
                cache.clear();
                lastCheckins.clear();
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException ignored) { /* JVM shutdown already in progress. */ }
            }
        });
        executor.shutdown();
    }

    public boolean awaitClosed(long seconds) throws InterruptedException { return executor.awaitTermination(seconds, TimeUnit.SECONDS); }
    @FunctionalInterface private interface SqlTask<T> { T run() throws Exception; }
}
