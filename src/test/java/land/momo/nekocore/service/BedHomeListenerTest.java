package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.Home;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BedHomeListenerTest {
    BedWorldFixture grid; NekoCorePlugin plugin; Settings settings; Messages messages; SqliteStore store;
    Player player; PlayerBedEnterEvent event; BedHomeListener listener; MockedStatic<Bukkit> bukkit;
    final UUID id = UUID.randomUUID(); final Queue<Runnable> tasks = new ArrayDeque<>(), callbacks = new ArrayDeque<>();
    final CompletableFuture<Void> saved = new CompletableFuture<>();
    @BeforeEach void setup() {
        grid = new BedWorldFixture(); plugin = mock(NekoCorePlugin.class); settings = mock(Settings.class);
        messages = mock(Messages.class); store = mock(SqliteStore.class); player = mock(Player.class); event = mock(PlayerBedEnterEvent.class);
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.store()).thenReturn(store);
        when(plugin.getLogger()).thenReturn(mock(Logger.class)); when(plugin.available(player)).thenReturn(true);
        when(settings.bedAutoSet()).thenReturn(new Settings.BedAutoSet(true, Set.of("world", "world_new")));
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.isSleeping()).thenReturn(true);
        when(player.hasPermission("nekocore.home.set")).thenReturn(true); when(player.getWorld()).thenReturn(grid.world);
        when(player.getBedLocation()).thenReturn(grid.bed); when(player.getLocation()).thenReturn(grid.bed.clone());
        when(event.getPlayer()).thenReturn(player); Block bed = mock(Block.class); when(event.getBed()).thenReturn(bed); when(bed.getLocation()).thenReturn(grid.bed);
        when(store.saveHome(any())).thenReturn(saved);
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { tasks.add(call.getArgument(1)); return null; });
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); bukkit.when(() -> Bukkit.getWorld(grid.world.getUID())).thenReturn(grid.world);
        listener = new BedHomeListener(plugin);
    }
    @AfterEach void close() { bukkit.close(); }
    void tick() { while (!tasks.isEmpty()) tasks.remove().run(); }
    void reply() { while (!callbacks.isEmpty()) callbacks.remove().run(); }
    @ParameterizedTest @ValueSource(strings = {"world", "world_new"})
    void successfulSleepSavesOnlyThatWorldAndPrivateIntegerMessageAfterCommit(String worldName) {
        when(grid.world.getName()).thenReturn(worldName); listener.onBedEnter(event);
        verifyNoInteractions(store); tick();
        var home = ArgumentCaptor.forClass(Home.class); verify(store).saveHome(home.capture());
        Home h = home.getValue(); assertEquals(id, h.playerUuid()); assertEquals(grid.world.getUID(), h.worldUuid()); assertEquals(worldName, h.worldName());
        assertEquals(72, h.y()); assertNotEquals(grid.bed.getX(), h.x()); verifyNoInteractions(messages);
        saved.complete(null); verifyNoInteractions(messages); reply();
        verify(messages).send(player, "home-bed-saved", Map.of("world", worldName, "x", "" + (int)Math.floor(h.x()), "y", "72", "z", "" + (int)Math.floor(h.z())));
        verifyNoMoreInteractions(messages, store);
        verify(event, never()).setCancelled(anyBoolean()); verify(event, never()).setUseBed(any());
        assertTrue(mockingDetails(player).getInvocations().stream().noneMatch(inv -> inv.getMethod().getName().startsWith("set") || inv.getMethod().getName().equals("teleport")));
    }
    @Test void failedDatabaseSaveNeverPretendsSuccess() {
        listener.onBedEnter(event); tick(); saved.completeExceptionally(new IllegalStateException("test database failure")); reply();
        verify(messages).send(player, "home-bed-failed"); verify(messages, never()).send(eq(player), eq("home-bed-saved"), anyMap());
    }
    @Test void disabledSettingDoesNotScheduleOrSave() {
        when(settings.bedAutoSet()).thenReturn(new Settings.BedAutoSet(false, Set.of("world_new")));
        listener.onBedEnter(event); assertTrue(tasks.isEmpty()); verifyNoInteractions(store);
    }
    @Test void nonAllowedWorldOrPermissionDenialDoesNotSave() {
        when(grid.world.getName()).thenReturn("lobby"); listener.onBedEnter(event);
        when(grid.world.getName()).thenReturn("world_new"); when(player.hasPermission("nekocore.home.set")).thenReturn(false); listener.onBedEnter(event);
        assertTrue(tasks.isEmpty()); verifyNoInteractions(store);
    }
    @Test void cancelledEventIncludingLateCancellationDoesNotSave() {
        when(event.isCancelled()).thenReturn(true); listener.onBedEnter(event); assertTrue(tasks.isEmpty());
        when(event.isCancelled()).thenReturn(false); listener.onBedEnter(event);
        when(event.isCancelled()).thenReturn(true); tick(); verifyNoInteractions(store);
    }
    @Test void unsuccessfulBedUseDoesNotSaveEvenIfEventWasNotCancelled() {
        listener.onBedEnter(event); when(player.isSleeping()).thenReturn(false); tick(); verifyNoInteractions(store, messages);
    }
    @Test void reloadDisableBeforeConfirmationIsRespected() {
        listener.onBedEnter(event); when(settings.bedAutoSet()).thenReturn(new Settings.BedAutoSet(false, Set.of("world_new")));
        tick(); verifyNoInteractions(store);
    }
    @Test void unsafeBedSurroundingsKeepExistingHomeAndExplainWhy() {
        grid.layer(71, Material.LAVA); listener.onBedEnter(event); tick();
        verifyNoInteractions(store); verify(messages).send(player, "home-bed-unsafe");
    }
    @Test void disconnectBeforeConfirmationOrBeforeReplyIsHandled() {
        listener.onBedEnter(event); when(player.isOnline()).thenReturn(false); tick(); verifyNoInteractions(store);
        when(player.isOnline()).thenReturn(true); listener.onBedEnter(event); tick(); saved.complete(null);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mock(Player.class)); reply(); verifyNoInteractions(messages);
    }
    @Test void bedChangedOrWorldChangedBeforeConfirmationDoesNotSave() {
        listener.onBedEnter(event); when(player.getBedLocation()).thenReturn(grid.bed.clone().add(20, 0, 0)); tick();
        listener.onBedEnter(event); when(player.getWorld()).thenReturn(mock(World.class)); tick(); verifyNoInteractions(store);
    }
}
