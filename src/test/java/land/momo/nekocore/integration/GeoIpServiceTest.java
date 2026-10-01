package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeoIpServiceTest {
    @TempDir Path directory;
    @Test void missingDatabaseDisablesOnlyLocationAndWarnsOnce() throws Exception {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class); var messages = mock(Messages.class);
        when(plugin.settings()).thenReturn(settings);
        when(settings.locationPrefix()).thenReturn(new Settings.LocationPrefix(true, "missing.mmdb", true, true, true, true));
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.messages()).thenReturn(messages); when(messages.plain(eq("geoip-database-missing"), anyMap())).thenReturn("missing");
        GeoIpService service = new GeoIpService(plugin);
        service.start(); service.fence().get(5, TimeUnit.SECONDS);
        assertFalse(service.available());
        UUID player = UUID.randomUUID(); service.resolve(player, InetAddress.getByName("8.8.8.8")); service.fence().get(5, TimeUnit.SECONDS);
        assertEquals("", service.location(player));
        service.restart(Map.of(player, InetAddress.getByName("1.1.1.1"))); service.fence().get(5, TimeUnit.SECONDS);
        verify(messages, times(1)).plain(eq("geoip-database-missing"), anyMap());
        service.stop();
    }
    @Test void localAddressesAreNeverLookedUpOrRetained() throws Exception {
        assertTrue(GeoIpService.privateAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(GeoIpService.privateAddress(InetAddress.getByName("192.168.1.2")));
        assertFalse(GeoIpService.privateAddress(InetAddress.getByName("8.8.8.8")));
    }
}
