package land.momo.nekocore.integration;

import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader.FileMode;
import com.maxmind.geoip2.DatabaseReader;
import land.momo.nekocore.NekoCorePlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/** One offline MMDB lookup per connection. Chat only reads the session cache. */
public final class GeoIpService {
    private final NekoCorePlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NekoCore-GeoIP"); thread.setDaemon(true); return thread;
    });
    private final ConcurrentMap<UUID, String> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile DatabaseReader reader;
    private volatile boolean warned;

    public GeoIpService(NekoCorePlugin plugin) { this.plugin = plugin; }

    public void start() { reopen(generation.incrementAndGet()); }

    public void restart(Map<UUID, InetAddress> online) {
        int token = generation.incrementAndGet(); sessions.clear();
        reopen(token);
        online.forEach((id, address) -> resolve(id, address, token));
    }

    private void reopen(int token) {
        executor.execute(() -> {
            closeReader();
            if (token != generation.get() || !plugin.settings().locationPrefix().enabled()) return;
            Path file = plugin.getDataFolder().toPath().resolve(plugin.settings().locationPrefix().databaseFile());
            try {
                if (!Files.isRegularFile(file)) throw new IOException("file missing");
                DatabaseReader loaded = new DatabaseReader.Builder(file.toFile()).fileMode(FileMode.MEMORY)
                        .withCache(new CHMCache()).locales(List.of("zh-CN", "en")).build();
                String type = loaded.metadata().databaseType();
                if (type == null || !type.toLowerCase(Locale.ROOT).contains("city")) {
                    loaded.close(); throw new IOException("MMDB is not a City database: " + type);
                }
                if (token != generation.get()) { loaded.close(); return; }
                reader = loaded; warned = false;
                plugin.getLogger().info("网络地区数据库已载入：" + file.toAbsolutePath() + "（" + type + "）");
            } catch (Exception e) {
                reader = null;
                warnOnce(file, e);
            }
        });
    }

    public void resolve(UUID player, InetAddress address) { resolve(player, address, generation.get()); }

    private void resolve(UUID player, InetAddress address, int token) {
        sessions.remove(player);
        if (address == null || privateAddress(address) || !plugin.settings().locationPrefix().enabled()
                || !plugin.settings().locationPrefix().cacheSession()) return;
        executor.execute(() -> {
            if (token != generation.get()) return;
            DatabaseReader current = reader;
            if (current == null) return;
            try {
                var response = current.tryCity(address);
                if (response.isEmpty()) return;
                var city = response.get();
                String location = LocationFormatter.display(city.country().isoCode(), city.country().names().get("zh-CN"),
                        city.mostSpecificSubdivision().isoCode(), city.mostSpecificSubdivision().names().get("zh-CN"),
                        plugin.settings().locationPrefix().showChinaProvince(),
                        plugin.settings().locationPrefix().showForeignCountry());
                if (token == generation.get() && !location.isBlank()) sessions.put(player, location);
            } catch (Exception e) {
                sessions.remove(player);
                if (reader == current) {
                    reader = null;
                    try { current.close(); } catch (IOException ignored) { }
                }
                Path file = plugin.getDataFolder().toPath().resolve(plugin.settings().locationPrefix().databaseFile());
                warnOnce(file, e);
            }
        });
    }

    public String location(UUID player) { return sessions.getOrDefault(player, ""); }
    public void quit(UUID player) { sessions.remove(player); }
    public boolean available() { return reader != null; }
    CompletableFuture<Void> fence() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> future.complete(null));
        return future;
    }

    public void stop() {
        generation.incrementAndGet(); sessions.clear();
        executor.execute(this::closeReader); executor.shutdown();
    }

    private void closeReader() {
        DatabaseReader old = reader; reader = null;
        if (old != null) try { old.close(); }
        catch (IOException e) { plugin.getLogger().log(Level.WARNING, "关闭网络地区数据库失败", e); }
    }

    private void warnOnce(Path file, Throwable error) {
        if (warned) return;
        warned = true;
        String message = plugin.messages().plain("geoip-database-missing", Map.of("file", file.toAbsolutePath().toString()));
        if (plugin.settings().debug()) plugin.getLogger().log(Level.WARNING, message, error);
        else plugin.getLogger().warning(message + "（" + Objects.toString(error.getMessage(), error.getClass().getSimpleName()) + "）");
    }

    static boolean privateAddress(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
    }
}
