import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.LevelCurve;
import land.momo.nekocore.model.Home;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

class JarSmoke {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        UUID player = UUID.randomUUID(), world = UUID.randomUUID();
        SqliteStore store = new SqliteStore(Logger.getAnonymousLogger());
        try {
            store.open(path, new LevelCurve(100, 50, 0, 10000)).get(10, TimeUnit.SECONDS);
            store.join(player, "JarSmoke", 1000).get(10, TimeUnit.SECONDS);
            store.coins("JarSmoke", "set", 72).get(10, TimeUnit.SECONDS);
            if (!store.checkin(player, java.time.ZoneId.of("Asia/Shanghai"), 100, 50).get(10, TimeUnit.SECONDS).claimed()) throw new AssertionError("first checkin failed");
            store.saveHome(new Home(player, world, "world", 1, 64, 2, 30, -10)).get(10, TimeUnit.SECONDS);
        } finally { store.closeAsync(); if (!store.awaitClosed(10)) throw new AssertionError("close timed out"); }
        SqliteStore reopened = new SqliteStore(Logger.getAnonymousLogger());
        try {
            reopened.open(path, new LevelCurve(100, 50, 0, 10000)).get(10, TimeUnit.SECONDS);
            if (reopened.join(player, "JarSmoke", 2000).get(10, TimeUnit.SECONDS).coins() != 172) throw new AssertionError("coins lost");
            if (reopened.checkin(player, java.time.ZoneId.of("Asia/Shanghai"), 100, 50).get(10, TimeUnit.SECONDS).claimed()) throw new AssertionError("duplicate checkin reward");
            if (reopened.cached(player).exp() != 50) throw new AssertionError("checkin exp lost");
            if (reopened.home(player, world, "world").get(10, TimeUnit.SECONDS).orElseThrow().yaw() != 30) throw new AssertionError("home lost");
        } finally { reopened.closeAsync(); if (!reopened.awaitClosed(10)) throw new AssertionError("close timed out"); }
        System.out.println("SHADED_JAR_SQLITE_SMOKE_OK");
    }
}
