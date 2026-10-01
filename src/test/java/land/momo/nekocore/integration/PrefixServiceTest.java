package land.momo.nekocore.integration;

import land.momo.nekocore.*;
import land.momo.nekocore.config.Settings;
import land.momo.nekocore.data.*;
import land.momo.nekocore.model.Profile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrefixServiceTest {
    @Test void titleReplacesLevelAndUnequipRestoresItWhilePerksStayHighestOwned() throws Exception {
        var plugin = mock(NekoCorePlugin.class); var repo = mock(TitleRepository.class); var store = mock(SqliteStore.class);
        Settings settings = mock(Settings.class, RETURNS_DEEP_STUBS);
        var features = TestDefaults.features();
        when(plugin.settings()).thenReturn(settings); when(plugin.features()).thenReturn(features);
        when(settings.features().chat().prefix()).thenReturn("&#9FD9F6[Lv.{level}] &r");
        when(plugin.titles()).thenReturn(repo); when(plugin.store()).thenReturn(store);
        when(settings.afkPool().reward().normalMultiplier()).thenReturn(1.10);
        UUID id = UUID.randomUUID(); var player = mock(Player.class); when(player.getUniqueId()).thenReturn(id);
        when(store.cached(id)).thenReturn(new Profile(id,"Neko",123,3,275,100,1,2,false));
        var prefixes = new PrefixService(plugin);
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(), ""));
        assertEquals("[Lv.3] ", plain(prefixes.primary(id)));
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("mame","sora"), "mame"));
        assertEquals("[Yuki] ", plain(prefixes.primary(id))); assertFalse(plain(prefixes.primary(id)).contains("Lv."));
        assertEquals("", plain(prefixes.chatLocation(id))); assertEquals(plain(prefixes.primary(id)), plain(prefixes.chat(id)));
        assertTrue(prefixes.autoCheckin(player)); assertTrue(prefixes.coloredChat(player)); assertEquals(30, prefixes.cooldown(player));
        assertEquals(1.20, prefixes.afkExpMultiplier(id));
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("mame"), "mame"));
        assertEquals(1.10, prefixes.afkExpMultiplier(id));
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("momo"), ""));
        assertEquals(1.10, prefixes.afkExpMultiplier(id));
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(), ""));
        assertEquals(1.10, prefixes.afkExpMultiplier(id));
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of("mame","sora"), ""));
        assertEquals("[Lv.3] ", plain(prefixes.primary(id))); assertEquals(30, prefixes.cooldown(player));
        assertEquals(1.20, prefixes.afkExpMultiplier(id));
    }
    @Test void locationComponentIsSharedByChatAndTabAndPrivacyImmediatelyHidesIt() throws Exception {
        var plugin=mock(NekoCorePlugin.class); var repo=mock(TitleRepository.class); var store=mock(SqliteStore.class);
        var geo=mock(GeoIpService.class); var messages=mock(land.momo.nekocore.config.Messages.class);
        Settings settings=mock(Settings.class,RETURNS_DEEP_STUBS); UUID id=UUID.randomUUID();
        var features=TestDefaults.features(); when(plugin.settings()).thenReturn(settings); when(plugin.features()).thenReturn(features);
        when(plugin.titles()).thenReturn(repo); when(plugin.store()).thenReturn(store); when(plugin.locations()).thenReturn(geo);
        when(plugin.messages()).thenReturn(messages); when(messages.raw("location-prefix-format")).thenReturn("&#A8BDCC[&f{location}&#A8BDCC] &r");
        when(settings.features().chat().prefix()).thenReturn("[Lv.{level}] ");
        when(repo.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(),"")); when(geo.location(id)).thenReturn("浙江");
        when(store.cached(id)).thenReturn(new Profile(id,"Momo",0,18,0,0,1,1,true));
        PrefixService prefixes=new PrefixService(plugin);
        assertEquals("[浙江] [Lv.18] ",plain(prefixes.chat(id)));
        assertEquals("[Lv.18] ",plain(prefixes.primary(id)));
        when(store.cached(id)).thenReturn(new Profile(id,"Momo",0,18,0,0,1,1,false));
        assertEquals("",prefixes.location(id)); assertEquals("[Lv.18] ",plain(prefixes.chat(id)));
        when(store.cached(id)).thenReturn(new Profile(id,"Momo",0,18,0,0,1,1,true)); when(geo.location(id)).thenReturn("");
        assertEquals("", plain(prefixes.chatLocation(id))); assertEquals("[Lv.18] ", plain(prefixes.chat(id)));
    }
    @Test void coloredChatOnlyParsesSafeColorsNotEventsDecorationsOrMiniMessage() {
        Component message = PrefixService.safeChatColors("&ahello &#9FD9F6蓝色 &k隐藏 <click:run_command:'/op me'>X</click> &l粗体");
        assertEquals("hello 蓝色 &k隐藏 <click:run_command:'/op me'>X</click> &l粗体", plain(message));
        assertSafe(message);
        assertFalse(plain(PrefixService.safeChatColors("§ktext")).contains("§"));
    }
    private static void assertSafe(Component component) {
        assertNull(component.clickEvent()); assertNull(component.hoverEvent());
        component.children().forEach(PrefixServiceTest::assertSafe);
    }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
}
