package land.momo.nekocore.integration;

import land.momo.nekocore.*;
import land.momo.nekocore.data.*;
import land.momo.nekocore.model.Profile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NameTagServiceTest {
    final NekoCorePlugin plugin = mock(NekoCorePlugin.class);
    final TitleRepository titles = mock(TitleRepository.class);
    final SqliteStore store = mock(SqliteStore.class);
    final PrefixService prefixes = mock(PrefixService.class);
    final Scoreboard board = mock(Scoreboard.class);
    final Player first = mock(Player.class), second = mock(Player.class);
    final Map<String,Team> teams = new HashMap<>(), entries = new HashMap<>();
    MockedStatic<Bukkit> bukkit;
    NameTagService service;
    @BeforeEach void setup() throws Exception {
        var settings = TestDefaults.features(); when(plugin.features()).thenReturn(settings);
        when(plugin.titles()).thenReturn(titles); when(plugin.store()).thenReturn(store); when(plugin.prefixes()).thenReturn(prefixes);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        int index = 0;
        for (Player p : List.of(first,second)) {
            UUID id = UUID.randomUUID(); String name = "Momo" + index++;
            when(p.getUniqueId()).thenReturn(id); when(p.getName()).thenReturn(name); when(p.getScoreboard()).thenReturn(board);
            when(store.cached(id)).thenReturn(new Profile(id,name,0,1,0,0,1,1,false));
            when(titles.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(),""));
            when(prefixes.primary(id)).thenReturn(Component.text("[Lv.1] "));
            when(p.playerListName()).thenReturn(Component.text(name));
        }
        when(board.getEntryTeam(anyString())).thenAnswer(call -> entries.get(call.getArgument(0)));
        when(board.getTeam(anyString())).thenAnswer(call -> teams.get(call.getArgument(0)));
        when(board.registerNewTeam(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0); Team team = mock(Team.class); teams.put(name,team);
            var prefix = new AtomicReference<>(Component.empty()); var current = new AtomicReference<>(board);
            when(team.prefix()).thenAnswer(ignored -> prefix.get()); doAnswer(a -> { prefix.set(a.getArgument(0)); return null; }).when(team).prefix(any());
            when(team.getScoreboard()).thenAnswer(ignored -> current.get());
            doAnswer(a -> { entries.put(a.getArgument(0),team); return null; }).when(team).addEntry(anyString());
            doAnswer(a -> { teams.remove(name); entries.values().removeIf(value -> value == team); current.set(null); return null; }).when(team).unregister();
            return team;
        });
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(first,second));
        bukkit.when(() -> Bukkit.getPlayer(first.getUniqueId())).thenReturn(first);
        bukkit.when(() -> Bukkit.getPlayer(second.getUniqueId())).thenReturn(second);
        service = new NameTagService(plugin);
    }
    @AfterEach void close() { bukkit.close(); }
    @Test void otherPlayersSeeUpdatedPrimaryPrefixWithoutReplacingTheirScoreboard() {
        service.refresh(); assertEquals(2,teams.size()); assertEquals(Component.text("[Lv.1] "),entries.get(first.getName()).prefix());
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Sora] ")); service.update(first);
        assertEquals(Component.text("[Sora] "),entries.get(first.getName()).prefix());
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Lv.8] ")); service.update(first);
        assertEquals(Component.text("[Lv.8] "),entries.get(first.getName()).prefix());
        verify(first,never()).setScoreboard(any()); verify(second,never()).setScoreboard(any());
        verify(prefixes,never()).chat(any()); verify(prefixes,never()).chatLocation(any());
    }
    @Test void foreignTeamIsNeverReplacedAndCustomTabNameIsNotOverwritten() {
        Team foreign = mock(Team.class); entries.put(first.getName(),foreign);
        when(second.playerListName()).thenReturn(Component.text("custom TAB"));
        service.refresh(); assertSame(foreign,entries.get(first.getName())); assertEquals(1,teams.size());
        verifyNoInteractions(foreign); verify(first,never()).playerListName(any(Component.class)); verify(second,never()).playerListName(any(Component.class));
    }
    @Test void stopRemovesOnlyOwnedTeamsAndNeverTouchesTab() {
        service.refresh(); service.stop(); assertTrue(teams.isEmpty()); assertTrue(entries.isEmpty());
        verify(first,never()).playerListName(any(Component.class)); verify(second,never()).playerListName(any(Component.class));
    }
    @Test void customTabChangeAfterOurPlainGuardSurvivesDisable() {
        service.refresh(); when(first.playerListName()).thenReturn(Component.text("Other plugin")); service.stop();
        verify(first,never()).playerListName((Component) null);
    }
    @Test void reconnectAndReloadRecreateOnlyOwnedTeamsWithCurrentStyle() {
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
        service.refresh(); service.quit(first.getUniqueId()); assertFalse(entries.containsKey(first.getName()));
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Sora] ")); service.refresh();
        assertEquals(Component.text("[Sora] "),entries.get(first.getName()).prefix());
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("new style [Sora] ")); service.restart();
        assertEquals(2,teams.size()); assertEquals(Component.text("new style [Sora] "),entries.get(first.getName()).prefix());
        verify(prefixes,never()).chat(any()); verify(prefixes,never()).chatLocation(any());
        verify(scheduler).runTaskTimer(eq(plugin),any(Runnable.class),eq(100L),eq(100L));
    }
    @Test void ordinaryAndAdminLevelChangesUseLatestLevelPrefixWithoutChangingRealName() {
        service.refresh();
        for(int level:new int[]{2,18,1}) {
            when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Lv."+level+"] ")); service.update(first);
            assertEquals(Component.text("[Lv."+level+"] "),entries.get(first.getName()).prefix());
        }
        verify(first,never()).displayName(any(Component.class)); verify(first,never()).setCustomName(anyString());
    }

    @Test void chatAndTabLocationCanExistButNameTagStillConsumesOnlyPrimary() {
        when(prefixes.chatLocation(first.getUniqueId())).thenReturn(Component.text("[浙江] "));
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Neko] "));
        service.refresh(); assertEquals(Component.text("[Neko] "),entries.get(first.getName()).prefix());

        when(prefixes.chatLocation(first.getUniqueId())).thenReturn(Component.text("[日本] "));
        when(prefixes.primary(first.getUniqueId())).thenReturn(Component.text("[Lv.18] "));
        service.update(first); assertEquals(Component.text("[Lv.18] "),entries.get(first.getName()).prefix());
        verify(prefixes,never()).chatLocation(any()); verify(prefixes,never()).chat(any());
    }
}
