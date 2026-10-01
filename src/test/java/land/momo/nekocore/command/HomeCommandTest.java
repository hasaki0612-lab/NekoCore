package land.momo.nekocore.command;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.model.Home;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeCommandTest {
    NekoCorePlugin plugin; SqliteStore store; Messages messages; Player player; Command command;
    CoreCommands commands; MockedStatic<Bukkit> bukkit;
    final UUID id = UUID.randomUUID();
    final Map<String, World> worlds = new HashMap<>();
    final Map<UUID, Home> homes = new HashMap<>();
    final Queue<Runnable> callbacks = new ArrayDeque<>();
    final CompletableFuture<Chunk> chunk = new CompletableFuture<>();
    @BeforeEach void setup() {
        plugin = mock(NekoCorePlugin.class); store = mock(SqliteStore.class); messages = mock(Messages.class);
        player = mock(Player.class); command = mock(Command.class); when(command.getName()).thenReturn("home");
        when(plugin.store()).thenReturn(store); when(plugin.messages()).thenReturn(messages); when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.permission(any(), anyString())).thenReturn(true); when(plugin.available(any())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.hasPermission(anyString())).thenReturn(true);
        when(player.teleport(any(Location.class), eq(TeleportCause.PLUGIN))).thenReturn(true);
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        bukkit = mockStatic(Bukkit.class); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        for (String name : List.of("lobby", "world", "world_new")) {
            World world = mock(World.class); worlds.put(name, world); UUID uid = UUID.randomUUID();
            when(world.getUID()).thenReturn(uid); when(world.getName()).thenReturn(name);
            when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
            WorldBorder border = mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
            when(world.getChunkAtAsync(any(Location.class))).thenReturn(chunk);
            bukkit.when(() -> Bukkit.getWorld(name)).thenReturn(world); bukkit.when(() -> Bukkit.getWorld(uid)).thenReturn(world);
            homes.put(uid, new Home(id, uid, name, name.equals("world_new") ? -435.5 : 128.5, 72, 5.5, 30, 12));
        }
        when(player.getWorld()).thenReturn(worlds.get("lobby"));
        when(store.home(eq(id), any(UUID.class), anyString())).thenAnswer(call -> CompletableFuture.completedFuture(Optional.ofNullable(homes.get(call.getArgument(1)))));
        commands = new CoreCommands(plugin);
    }
    @AfterEach void close() { bukkit.close(); }
    void drain() { while (!callbacks.isEmpty()) callbacks.remove().run(); }
    void invoke(String label, String... args) { assertTrue(commands.onCommand(player, command, label, args)); }
    void assertDestination(World target) {
        var captured = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(captured.capture(), eq(TeleportCause.PLUGIN));
        var at = captured.getValue(); var expected = homes.get(target.getUID());
        assertSame(target, at.getWorld()); assertEquals(expected.x(), at.getX()); assertEquals(expected.yaw(), at.getYaw());
        assertEquals(expected.pitch(), at.getPitch());
        verify(messages).send(player, "home-success");
    }
    @Test void noArgumentStillReturnsCurrentWorldHome() {
        when(player.getWorld()).thenReturn(worlds.get("world_new"));
        invoke("home"); drain(); chunk.complete(mock(Chunk.class)); drain(); assertDestination(worlds.get("world_new"));
    }
    @ParameterizedTest @ValueSource(strings = {"world", "world_new"})
    void explicitWorldQueriesUuidAndWaitsForMainThreadAfterChunkLoads(String name) throws Exception {
        World target = worlds.get(name); invoke("home", name);
        verify(store).home(id, target.getUID(), name); verify(target, never()).getChunkAtAsync(any(Location.class));
        drain(); verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
        CompletableFuture.runAsync(() -> chunk.complete(mock(Chunk.class))).get(5, TimeUnit.SECONDS);
        verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
        drain(); assertDestination(target);
    }
    @Test void namespacedLabelAcceptsSameArgument() {
        invoke("nekocore:home", "world_new"); drain(); chunk.complete(mock(Chunk.class)); drain(); assertDestination(worlds.get("world_new"));
    }
    @Test void missingOrUnloadedWorldNeverFallsBackToDatabaseNameLookup() {
        invoke("home", "missing"); verifyNoInteractions(store);
        verify(messages).send(player, "home-world-unavailable", Map.of("world", "missing"));
    }
    @Test void missingTargetHomeDoesNotUseAnotherWorldsHome() {
        homes.remove(worlds.get("world_new").getUID()); invoke("home", "world_new"); drain();
        verify(messages).send(player, "home-missing", Map.of("world", "world_new"));
        verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
    }
    @Test void deniedPermissionNeverQueriesDatabase() throws Exception {
        var field = NekoCorePlugin.class.getDeclaredField("messages"); field.setAccessible(true); field.set(plugin, messages);
        doCallRealMethod().when(plugin).permission(any(), anyString()); when(player.hasPermission("nekocore.home.use")).thenReturn(false);
        invoke("home", "world"); verifyNoInteractions(store); verify(messages).send(player, "no-permission");
    }
    @Test void rejectsExtraArgumentsAndCompletesLoadedWorldNamesOnly() {
        invoke("home", "world", "extra"); verify(messages).send(player, "usage.home"); verifyNoInteractions(store);
        bukkit.when(Bukkit::getWorlds).thenReturn(List.copyOf(worlds.values()));
        assertEquals(List.of("world", "world_new"), commands.onTabComplete(player, command, "nekocore:home", new String[]{"wo"}));
    }
    @Test void targetUnloadedWhileChunkLoadsIsRejected() {
        invoke("home", "world"); drain(); World target = worlds.get("world");
        bukkit.when(() -> Bukkit.getWorld(target.getUID())).thenReturn(null);
        chunk.complete(mock(Chunk.class)); drain();
        verify(messages).send(player, "home-world-unavailable", Map.of("world", "world"));
        verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
    }
    @Test void movingToAnotherWorldWhileWaitingCancelsTeleport() {
        invoke("home", "world"); drain(); when(player.getWorld()).thenReturn(worlds.get("world_new"));
        chunk.complete(mock(Chunk.class)); drain(); verify(messages).send(player, "home-moved");
        verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
    }
    @Test void lostPermissionOrReconnectedSessionCannotTeleport() {
        invoke("home", "world"); drain(); when(plugin.permission(player, "nekocore.home.use")).thenReturn(false);
        chunk.complete(mock(Chunk.class)); drain(); verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
        when(plugin.permission(player, "nekocore.home.use")).thenReturn(true);
        invoke("home", "world"); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mock(Player.class)); drain();
        verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
    }
    @Test void failedChunkLoadClearsPendingAndNeverReportsSuccess() {
        invoke("home", "world"); drain(); chunk.completeExceptionally(new IllegalStateException("test failure")); drain();
        verify(messages).send(player, "home-failed"); verify(messages, never()).send(player, "home-success");
        invoke("home", "world"); verify(store, times(2)).home(id, worlds.get("world").getUID(), "world");
    }
    @Test void checkAndSetHomeKeepOriginalDatabasePaths() {
        when(command.getName()).thenReturn("check"); invoke("check", "home", "world_new");
        verify(store).home(id, worlds.get("world_new").getUID(), "world_new");
        when(command.getName()).thenReturn("sethome"); when(player.getLocation()).thenReturn(new Location(worlds.get("lobby"), 1.5, 65, -2.5, 90, 15));
        when(store.saveHome(any())).thenReturn(CompletableFuture.completedFuture(null)); invoke("sethome");
        verify(store).saveHome(new Home(id, worlds.get("lobby").getUID(), "lobby", 1.5, 65, -2.5, 90, 15));
    }
}
