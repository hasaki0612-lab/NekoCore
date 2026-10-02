package land.momo.nekocore.integration;

import land.momo.nekocore.*;
import land.momo.nekocore.config.Settings;
import land.momo.nekocore.data.*;
import land.momo.nekocore.service.PlayerDataService;
import land.momo.nekocore.model.*;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NekoExpansionTest {
    @Test void titleDisplayAndLevelPlaceholdersStayIndependentAndNeverIncludeIp() throws Exception {
        var plugin=mock(NekoCorePlugin.class); var settings=mock(Settings.class,RETURNS_DEEP_STUBS);
        var data=mock(PlayerDataService.class); var store=mock(SqliteStore.class); var titles=mock(TitleRepository.class);
        var features=TestDefaults.features(); UUID id=UUID.randomUUID(); var player=mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(id); when(plugin.data()).thenReturn(data); when(plugin.store()).thenReturn(store);
        when(plugin.titles()).thenReturn(titles); when(plugin.features()).thenReturn(features); when(plugin.settings()).thenReturn(settings);
        var geo=mock(GeoIpService.class); var messages=mock(land.momo.nekocore.config.Messages.class);
        when(plugin.locations()).thenReturn(geo); when(plugin.messages()).thenReturn(messages);
        when(messages.raw("location-prefix-format")).thenReturn("[&f{location}&r] "); when(geo.location(id)).thenReturn("日本");
        when(settings.curve()).thenReturn(new LevelCurve(100,50,0,10000));
        when(settings.features().chat().prefix()).thenReturn("[Lv.{level}] ");
        var profile=new Profile(id,"Momo",500,3,275,3600,1,2,true);
        when(data.view(id)).thenReturn(profile); when(store.cached(id)).thenReturn(profile);
        when(plugin.prefixes()).thenReturn(new PrefixService(plugin)); var expansion=new NekoExpansion(plugin);
        assertEquals("1.2.0", expansion.getVersion());
        when(titles.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(),""));
        assertEquals("",expansion.onRequest(player,"title")); assertEquals("",expansion.onRequest(player,"title_prefix"));
        assertEquals("[Lv.3] ",expansion.onRequest(player,"display_prefix"));
        when(titles.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("sora"),"sora"));
        assertEquals("Neko",expansion.onRequest(player,"title")); assertTrue(expansion.onRequest(player,"title_prefix").contains("[Neko]"));
        assertEquals(expansion.onRequest(player,"title_prefix"),expansion.onRequest(player,"display_prefix"));
        assertEquals("[Lv.3] ",expansion.onRequest(player,"level_prefix"));
        assertEquals("500",expansion.onRequest(player,"coins")); assertEquals("25",expansion.onRequest(player,"exp"));
        assertEquals("200",expansion.onRequest(player,"exp_needed")); assertNull(expansion.onRequest(player,"ip"));
        assertEquals("日本",expansion.onRequest(player,"location")); assertTrue(expansion.onRequest(player,"location_prefix").contains("日本"));
        assertFalse(expansion.onRequest(player,"display_prefix").contains("日本"));
        when(titles.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("sora"),""));
        assertEquals("[Lv.3] ",expansion.onRequest(player,"display_prefix")); assertTrue(expansion.persist());
        verify(store,never()).knownPlayer(anyString()); verify(data,never()).join(any(),anyString(),anyLong());
    }
}
