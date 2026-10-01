package land.momo.nekocore.data;

import land.momo.nekocore.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class Upgrade111Test {
    @TempDir Path directory;
    final UUID player = UUID.randomUUID(), world = UUID.randomUUID(), worldNew = UUID.randomUUID();
    SqliteStore store; Path db;
    final Home first = new Home(player, world, "world", 128.5, 72, -435.5, 90, 15);
    final Home second = new Home(player, worldNew, "world_new", -50.5, 80, 30.5, 30, 0);
    static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }
    @BeforeEach void setup() throws Exception {
        db = directory.resolve("110.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
            for (String resource : List.of("V1__initial.sql", "V2__daily_checkins.sql")) try (var in = getClass().getResourceAsStream("/db/migration/" + resource)) {
                for (String sql : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";")) if (!sql.isBlank()) s.execute(sql);
            }
            s.execute("INSERT INTO players VALUES('" + player + "','Momo',777,3,275,3600,1000,2000,1)");
            for (Home h : List.of(first, second)) s.execute("INSERT INTO homes VALUES('" + player + "','" + h.worldUuid() + "','" + h.worldName() + "'," + h.x() + "," + h.y() + "," + h.z() + "," + h.yaw() + "," + h.pitch() + ")");
            s.execute("INSERT INTO daily_checkins VALUES('" + player + "','2026-09-28',1790582400000,'Asia/Shanghai',100,50)");
            s.execute("PRAGMA user_version=2");
        }
        store = new SqliteStore(Logger.getAnonymousLogger(), Clock.fixed(Instant.parse("2026-09-28T06:00:00Z"), ZoneOffset.UTC));
    }
    @AfterEach void close() throws Exception { store.closeAsync(); assertTrue(store.awaitClosed(10)); }
    Map<String, List<String>> snapshot() throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
            // V3 adds tables, but definitions and rows of all pre-existing tables must stay identical.
            for (String sql : List.of("SELECT * FROM players ORDER BY uuid", "SELECT * FROM homes ORDER BY world_uuid",
                    "SELECT * FROM daily_checkins ORDER BY player_uuid,claim_date",
                    "SELECT type,name,sql FROM sqlite_master WHERE tbl_name IN ('players','homes','daily_checkins') ORDER BY name")) {
                List<String> rows = new ArrayList<>();
                try (ResultSet rs = s.executeQuery(sql)) { while (rs.next()) {
                    List<String> row = new ArrayList<>(); for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.add(rs.getString(i));
                    rows.add(row.toString());
                } }
                result.put(sql, rows);
            }
        }
        return result;
    }
    @Test void schema2UpgradePreservesEveryRowAndSchemaAndTodaysClaim() throws Exception {
        var before = snapshot(); await(store.open(db, new LevelCurve(100, 50, 0, 10000))); assertEquals(before, snapshot());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA user_version")) {
            rs.next(); assertEquals(5, rs.getInt(1));
        }
        var profile = await(store.join(player, "Momo", 3000));
        assertEquals(new Profile(player, "Momo", 777, 3, 275, 3600, 1000, 3000, true), profile);
        assertEquals(first, await(store.home(player, world, "world")).orElseThrow());
        assertEquals(second, await(store.home(player, worldNew, "world_new")).orElseThrow());
        assertFalse(await(store.checkin(player, ZoneId.of("Asia/Shanghai"), 100, 50)).claimed());
        assertEquals(profile, store.cached(player));
    }
    @Test void bedStyleUpsertUpdatesOnlyRequestedWorldAndFailedSaveKeepsOldHome() throws Exception {
        await(store.open(db, new LevelCurve(100, 50, 0, 10000)));
        Home bed = new Home(player, worldNew, "world_new", -435.5, 72, 128.5, 0, 0);
        await(store.saveHome(bed)); assertEquals(first, await(store.home(player, world, "world")).orElseThrow());
        assertEquals(bed, await(store.home(player, worldNew, "world_new")).orElseThrow());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER reject_home BEFORE UPDATE ON homes BEGIN SELECT RAISE(ABORT,'test rejection'); END");
        }
        assertThrows(ExecutionException.class, () -> await(store.saveHome(second)));
        assertEquals(bed, await(store.home(player, worldNew, "world_new")).orElseThrow());
        assertEquals(first, await(store.home(player, world, "world")).orElseThrow());
    }
}
