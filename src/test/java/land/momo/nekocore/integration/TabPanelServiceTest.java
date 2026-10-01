package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import land.momo.nekocore.model.Profile;
import land.momo.nekocore.service.PlayerDataService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TabPanelServiceTest {
    NekoCorePlugin plugin; Player player; PrefixService prefixes; PlayerDataService data; Settings settings; Messages messages;
    MockedStatic<Bukkit> bukkit; UUID id; AtomicLong nanos;
    @BeforeEach void setup() {
        plugin=mock(NekoCorePlugin.class); player=mock(Player.class); prefixes=mock(PrefixService.class);
        data=mock(PlayerDataService.class); settings=mock(Settings.class); messages=mock(Messages.class); id=UUID.randomUUID(); nanos=new AtomicLong();
        when(plugin.settings()).thenReturn(settings); when(settings.tab()).thenReturn(new Settings.GlobalTab(true,true,1));
        when(plugin.prefixes()).thenReturn(prefixes); when(plugin.data()).thenReturn(data); when(plugin.messages()).thenReturn(messages);
        when(messages.raw("tab.header")).thenReturn("✦ My Server · 全局面板 ✦\n\n位置  X {x}   Y {y}   Z {z}\nTPS   {tps_1m} · {tps_5m} · {tps_15m}\n");
        when(messages.raw("tab.footer")).thenReturn("\n在线  {online}/{max_players}     金币  {coins}\nUTC+8 · {time}\n服务器运行 · {uptime}");
        when(player.getUniqueId()).thenReturn(id); when(player.getName()).thenReturn("Momo");
        when(player.getLocation()).thenReturn(new Location(null,-10.5,54,33.5));
        when(data.view(id)).thenReturn(new Profile(id,"Momo",1286,18,1000,0,1,1,true));
        when(prefixes.primary(id)).thenReturn(Component.text("[Neko] "));
        when(prefixes.chatLocation(id)).thenReturn(Component.text("[浙江] "));
        when(player.playerListName()).thenReturn(Component.text("Momo")); when(player.playerListHeader()).thenReturn(Component.empty());
        when(player.playerListFooter()).thenReturn(Component.empty());
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(Bukkit::getTPS).thenReturn(new double[]{20,19.5,17}); bukkit.when(Bukkit::getMaxPlayers).thenReturn(30);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
    }
    @AfterEach void close(){bukkit.close();}

    @Test void headerFooterCoordinatesTpsCoinsBeijingTimeAndUptimeUseOneCachedUpdate() {
        Clock clock=Clock.fixed(Instant.parse("2026-09-30T08:07:54Z"),ZoneOffset.UTC);
        TabPanelService service=new TabPanelService(plugin,clock,nanos::get); nanos.set(Duration.ofDays(2).plusHours(19).plusMinutes(12).toNanos());
        service.update();
        var name=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).playerListName(name.capture());
        assertEquals("[浙江] [Neko] Momo",PlainTextComponentSerializer.plainText().serialize(name.getValue()));
        var header=org.mockito.ArgumentCaptor.forClass(Component.class); var footer=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(player).sendPlayerListHeaderAndFooter(header.capture(),footer.capture());
        String h=PlainTextComponentSerializer.plainText().serialize(header.getValue());
        String f=PlainTextComponentSerializer.plainText().serialize(footer.getValue());
        assertTrue(h.contains("My Server · 全局面板")); assertTrue(h.contains("X -11")); assertTrue(h.contains("Y 54")); assertTrue(h.contains("Z 33"));
        assertTrue(h.contains("20.0")); assertTrue(h.contains("19.5")); assertTrue(h.contains("17.0"));
        assertTrue(f.contains("1/30")); assertTrue(f.contains("1,286")); assertTrue(f.contains("2026/09/30 16:07:54"));
        assertTrue(f.contains("UTC+8")); assertFalse(f.contains("北京时间")); assertTrue(h.contains("\n\n位置"));
        assertTrue(f.contains("2d 19h 12m"));
        verify(prefixes).primary(id); verify(prefixes).chatLocation(id); verify(prefixes,never()).chat(any());
    }

    @Test void tabUsesLocationComponentAndRestoresOnlyItsOwnValues() {
        TabPanelService service=new TabPanelService(plugin,Clock.systemUTC(),nanos::get);
        AtomicReference<Component> list=new AtomicReference<>(Component.text("Momo"));
        AtomicReference<Component> header=new AtomicReference<>(Component.empty()),footer=new AtomicReference<>(Component.empty());
        when(player.playerListName()).thenAnswer(i->list.get()); doAnswer(i->{list.set(i.getArgument(0));return null;}).when(player).playerListName(any(Component.class));
        when(player.playerListHeader()).thenAnswer(i->header.get()); when(player.playerListFooter()).thenAnswer(i->footer.get());
        doAnswer(i->{header.set(i.getArgument(0));footer.set(i.getArgument(1));return null;}).when(player).sendPlayerListHeaderAndFooter(any(),any());
        service.update(); assertEquals("[浙江] [Neko] Momo",PlainTextComponentSerializer.plainText().serialize(list.get()));
        service.stop(); assertEquals("Momo",PlainTextComponentSerializer.plainText().serialize(list.get()));
        verify(prefixes,never()).location(any()); verify(prefixes,never()).legacyLocation(any());
    }

    @Test void locationComposesWithEitherLevelOrTitleButNeverDuplicatesPrimary() {
        TabPanelService service=new TabPanelService(plugin,Clock.systemUTC(),nanos::get);
        when(prefixes.primary(id)).thenReturn(Component.text("[Lv.18] "));
        service.update(player);
        var name=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).playerListName(name.capture());
        assertEquals("[浙江] [Lv.18] Momo",PlainTextComponentSerializer.plainText().serialize(name.getValue()));

        clearInvocations(player); when(prefixes.chatLocation(id)).thenReturn(Component.text("[日本] "));
        when(prefixes.primary(id)).thenReturn(Component.text("[Neko] ")); service.update(player);
        verify(player).playerListName(name.capture());
        String titleName=PlainTextComponentSerializer.plainText().serialize(name.getValue());
        assertEquals("[日本] [Neko] Momo",titleName); assertFalse(titleName.contains("Lv."));
    }

    @Test void hiddenOrUnavailableLocationFallsBackWithoutEmptyBrackets() {
        TabPanelService service=new TabPanelService(plugin,Clock.systemUTC(),nanos::get);
        when(prefixes.chatLocation(id)).thenReturn(Component.empty()); service.update(player);
        var name=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).playerListName(name.capture());
        assertEquals("[Neko] Momo",PlainTextComponentSerializer.plainText().serialize(name.getValue()));
        assertFalse(PlainTextComponentSerializer.plainText().serialize(name.getValue()).contains("[]"));
    }

    @Test void tabLocationSwitchDoesNotAffectChatAndDisabledTabWritesNothing() {
        when(settings.tab()).thenReturn(new Settings.GlobalTab(true,false,1));
        TabPanelService service=new TabPanelService(plugin,Clock.systemUTC(),nanos::get); service.update(player);
        var name=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).playerListName(name.capture());
        assertEquals("[Neko] Momo",PlainTextComponentSerializer.plainText().serialize(name.getValue()));
        verify(prefixes,never()).chatLocation(any());

        clearInvocations(player,prefixes); when(settings.tab()).thenReturn(new Settings.GlobalTab(false,true,1));
        service.update(player); verify(player,never()).playerListName(any(Component.class));
        verify(player,never()).sendPlayerListHeaderAndFooter(any(),any()); verifyNoInteractions(prefixes);
    }

    @Test void uptimeFormattingIsStable() {
        assertEquals("0h 0m",TabPanelService.uptime(0));
        assertEquals("1d 2h 3m",TabPanelService.uptime(Duration.ofDays(1).plusHours(2).plusMinutes(3).toNanos()));
    }

    @Test void configRestartDoesNotResetServerUptimeOrigin() {
        BukkitScheduler scheduler=mock(BukkitScheduler.class); BukkitTask task=mock(BukkitTask.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(20L),eq(20L))).thenReturn(task);
        TabPanelService service=new TabPanelService(plugin,Clock.fixed(Instant.parse("2026-09-30T08:00:00Z"),ZoneOffset.UTC),nanos::get);
        nanos.set(Duration.ofHours(1).toNanos()); service.restart();
        nanos.set(Duration.ofHours(2).toNanos()); service.restart();
        var footer=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(player,atLeast(2)).sendPlayerListHeaderAndFooter(any(Component.class),footer.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(footer.getAllValues().getLast()).contains("2h 0m"));
    }
}
