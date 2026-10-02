package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import land.momo.nekocore.model.Profile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JoinInfoServiceTest {
    NekoCorePlugin plugin; Settings settings; Messages messages; Player player;
    PlayerDataService data; BukkitScheduler scheduler; BukkitTask task;
    MockedStatic<Bukkit> bukkit; List<Runnable> pending; JoinInfoService service;
    @BeforeEach void setup() {
        plugin=mock(NekoCorePlugin.class); settings=mock(Settings.class); messages=mock(Messages.class);
        player=mock(Player.class); data=mock(PlayerDataService.class); scheduler=mock(BukkitScheduler.class); task=mock(BukkitTask.class);
        pending=new ArrayList<>();
        UUID id=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.data()).thenReturn(data);
        var profile=new Profile(id,"Player_A",12,2,100,18,1,1,false);
        when(data.view(id)).thenReturn(profile);
        when(plugin.variables(profile)).thenReturn(Map.of("player","Player_A","server","My Server","coins","12"));
        when(messages.lines("join-info.lines")).thenReturn(List.of("Welcome {player} to {server}"));
        when(messages.raw("join-info.buttons.docs")).thenReturn("[Docs]");
        when(messages.raw("join-info.link-hover")).thenReturn("Open in browser");
        when(settings.joinInfo()).thenReturn(config("",true));
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(30L))).thenAnswer(call -> {pending.add(call.getArgument(1));return task;});
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        service=new JoinInfoService(plugin);
    }
    @AfterEach void close(){bukkit.close();}
    static Settings.JoinInfo config(String url,boolean enabled){return new Settings.JoinInfo(enabled,30,List.of(new Settings.JoinInfoEntry("docs",url)));}
    @Test void delayedPersonalBlockUsesPreparedCacheAndHidesEmptyButtons(){
        service.show(player); verify(player,never()).sendMessage(any(Component.class));
        pending.getFirst().run();
        var capture=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).sendMessage(capture.capture());
        assertEquals("Welcome Player_A to My Server",PlainTextComponentSerializer.plainText().serialize(capture.getValue()));
        assertFalse(hasClick(capture.getValue())); verify(plugin,never()).store(); verify(plugin,never()).locations();
        bukkit.verify(() -> Bukkit.broadcast(any(Component.class)),never());
    }
    @Test void configuredLinksUseOpenUrlAndHoverOnly(){
        when(settings.joinInfo()).thenReturn(config("https://github.com/hasaki0612-lab/NekoCore",true));
        service.send(player);
        var capture=org.mockito.ArgumentCaptor.forClass(Component.class); verify(player).sendMessage(capture.capture());
        assertTrue(hasUrl(capture.getValue()));
    }
    @Test void pendingJoinReadsLatestConfiguration(){
        service.show(player);
        when(messages.lines("join-info.lines")).thenReturn(List.of("Updated {server}"));
        pending.getFirst().run();
        verify(player).sendMessage(argThat((Component c) -> PlainTextComponentSerializer.plainText().serialize(c).equals("Updated My Server")));
    }
    @Test void quitAndStopCancelQueuedChatAndDisableDoesNotSchedule(){
        service.show(player); service.stop(); verify(task).cancel(); pending.getFirst().run();
        verify(player,never()).sendMessage(any(Component.class));
        when(settings.joinInfo()).thenReturn(config("",false)); service.show(player);
        verify(scheduler,times(1)).runTaskLater(eq(plugin),any(Runnable.class),eq(30L));
    }
    @Test void quitOfflineOrUnpreparedPlayerReceivesNothing(){
        service.show(player); service.quit(player.getUniqueId()); verify(task).cancel();
        when(player.isOnline()).thenReturn(false); pending.getFirst().run(); verify(player,never()).sendMessage(any(Component.class));
        when(player.isOnline()).thenReturn(true); when(data.view(any())).thenReturn(null); service.send(player);
        verify(player,never()).sendMessage(any(Component.class));
    }
    static boolean hasClick(Component c){return c.clickEvent()!=null || c.children().stream().anyMatch(JoinInfoServiceTest::hasClick);}
    static boolean hasUrl(Component c){
        if(c.clickEvent()!=null) return c.clickEvent().action()==ClickEvent.Action.OPEN_URL && c.hoverEvent()!=null;
        return c.children().stream().anyMatch(JoinInfoServiceTest::hasUrl);
    }
}
