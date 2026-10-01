package land.momo.nekocore.service;

import land.momo.nekocore.*;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.integration.PrefixService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeleportRequestServiceTest {
    final NekoCorePlugin plugin = mock(NekoCorePlugin.class);
    final Messages messages = mock(Messages.class);
    final InventoryExchangeService exchanges = mock(InventoryExchangeService.class);
    final PrefixService prefixes = mock(PrefixService.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final BedWorldFixture targetWorld = new BedWorldFixture();
    final World sourceWorld = mock(World.class);
    final Player from = mock(Player.class), to = mock(Player.class), nearby = mock(Player.class), distant = mock(Player.class);
    final Queue<Runnable> main = new ArrayDeque<>();
    final List<Runnable> timers = new ArrayList<>();
    final CompletableFuture<Chunk> chunks = new CompletableFuture<>();
    MockedStatic<Bukkit> bukkit;
    TeleportRequestService service;
    @BeforeEach void setup() throws Exception {
        var config = TestDefaults.features(); when(plugin.features()).thenReturn(config); when(plugin.messages()).thenReturn(messages);
        when(plugin.featuresAvailable(any())).thenReturn(true); when(plugin.permission(any(),anyString())).thenReturn(true);
        when(plugin.exchanges()).thenReturn(exchanges); when(exchanges.idle(any())).thenReturn(true);
        when(plugin.prefixes()).thenReturn(prefixes); when(prefixes.cooldown(from)).thenReturn(30);
        when(sourceWorld.getUID()).thenReturn(UUID.randomUUID());
        for (Player p : List.of(from,to,nearby,distant)) {
            when(p.getUniqueId()).thenReturn(UUID.randomUUID()); when(p.isOnline()).thenReturn(true); when(p.hasPermission("nekocore.tpn")).thenReturn(true);
            when(p.canSee(any())).thenReturn(true);
        }
        when(from.getName()).thenReturn("Momo"); when(to.getName()).thenReturn("Sora");
        when(from.getWorld()).thenReturn(sourceWorld); when(to.getWorld()).thenReturn(targetWorld.world);
        when(from.getLocation()).thenReturn(new Location(sourceWorld,0,72,0)); when(to.getLocation()).thenAnswer(ignored -> targetWorld.bed.clone());
        when(nearby.getLocation()).thenReturn(targetWorld.bed.clone().add(2,0,0)); when(distant.getLocation()).thenReturn(targetWorld.bed.clone().add(100,0,0));
        when(targetWorld.world.getPlayers()).thenReturn(List.of(nearby,distant));
        when(targetWorld.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(chunks);
        when(from.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);
        doAnswer(call -> { main.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(call -> { timers.add(call.getArgument(1)); return mock(BukkitTask.class); });
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayerExact("Sora")).thenReturn(to); bukkit.when(() -> Bukkit.getPlayerExact("Momo")).thenReturn(from);
        for (Player p : List.of(from,to)) bukkit.when(() -> Bukkit.getPlayer(p.getUniqueId())).thenReturn(p);
        bukkit.when(() -> Bukkit.getWorld(targetWorld.world.getUID())).thenReturn(targetWorld.world);
        service = new TeleportRequestService(plugin);
    }
    @AfterEach void close() { bukkit.close(); }
    void pump() { while(!main.isEmpty()) main.remove().run(); }
    void accept() { service.request(from,"Sora"); service.answer(to,true); }
    void loaded() { chunks.complete(mock(Chunk.class)); pump(); }
    @Test void acceptingUsesAcceptanceSnapshotAndAsyncSafeLandingThenStartsCooldown() {
        accept(); verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        when(to.getLocation()).thenReturn(targetWorld.bed.clone().add(100,0,100)); loaded();
        var location = org.mockito.ArgumentCaptor.forClass(Location.class);
        verify(from).teleport(location.capture(),eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
        assertSame(targetWorld.world,location.getValue().getWorld()); assertTrue(location.getValue().distanceSquared(targetWorld.bed) < 20);
        verify(messages).send(eq(from),eq("tpn-success-from"),anyMap()); verify(messages).send(eq(to),eq("tpn-success-to"),anyMap());
        verify(from).playSound(any(Location.class),anyString(),eq(SoundCategory.MASTER),anyFloat(),anyFloat());
        verify(to).playSound(any(Location.class),anyString(),eq(SoundCategory.MASTER),anyFloat(),anyFloat());
        verify(nearby).spawnParticle(eq(Particle.END_ROD),anyDouble(),anyDouble(),anyDouble(),eq(12),anyDouble(),anyDouble(),anyDouble(),anyDouble(),isNull(),eq(true));
        verify(distant,never()).spawnParticle(any(Particle.class),anyDouble(),anyDouble(),anyDouble(),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble(),any(),anyBoolean());
        service.request(from,"Sora"); verify(messages).send(eq(from),eq("tpn-cooldown"),anyMap());
        verify(scheduler,atLeastOnce()).runTaskLater(eq(plugin),any(Runnable.class),eq(600L));
    }
    @Test void selfOfflineHiddenAndPermissionDeniedAreRejected() {
        service.request(from,"Momo"); verify(messages).send(from,"tpn-self");
        service.request(from,"Nobody"); verify(messages).send(from,"tpn-offline");
        when(from.canSee(to)).thenReturn(false); service.request(from,"Sora"); verify(messages,times(2)).send(from,"tpn-offline");
        when(from.canSee(to)).thenReturn(true); when(to.hasPermission("nekocore.tpn")).thenReturn(false);
        service.request(from,"Sora"); verify(messages).send(from,"tpn-unavailable");
        verifyNoInteractions(scheduler);
    }
    @Test void duplicateBidirectionalAndMultipleRequestsCannotReplacePendingRequest() {
        service.request(from,"Sora"); service.request(from,"Sora"); service.request(to,"Momo");
        verify(messages).send(from,"tpn-pending"); verify(messages).send(to,"tpn-pending"); assertEquals(1,timers.size());
    }
    @Test void declineAndExpiredRequestsNeverChargeCooldown() {
        service.request(from,"Sora"); service.answer(to,false); verify(messages).send(eq(from),eq("tpn-declined"),anyMap());
        service.request(from,"Sora"); timers.getLast().run(); verify(messages).send(eq(from),eq("tpn-expired"),anyMap());
        service.answer(to,true); verify(messages).send(to,"tpn-no-request");
        service.request(from,"Sora"); verify(messages,times(3)).send(eq(from),eq("tpn-sent"),anyMap());
    }
    @Test void eitherPlayerLeavingCancelsAndCannotApplyLateChunkResult() {
        accept(); service.quit(new PlayerQuitEvent(to,net.kyori.adventure.text.Component.empty(),PlayerQuitEvent.QuitReason.DISCONNECTED));
        loaded(); verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        verify(messages).send(eq(from),eq("tpn-player-left"),anyMap());
    }
    @Test void permissionRevokedOrSourceWorldChangedDuringLoadCancels() {
        accept(); when(from.hasPermission("nekocore.tpn")).thenReturn(false); loaded();
        verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        verify(messages).send(eq(from),eq("tpn-unavailable"),anyMap());
    }
    @Test void unloadedDestinationOrFailedChunksDoNotTeleport() {
        accept(); bukkit.when(() -> Bukkit.getWorld(targetWorld.world.getUID())).thenReturn(null); loaded();
        verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }
    @Test void unsafeTargetDoesNotTeleportOrClaimSuccess() {
        targetWorld.layer(71,Material.AIR); accept(); loaded();
        verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        verify(messages).send(eq(from),eq("tpn-unsafe"),anyMap());
        verify(messages,never()).send(eq(from),eq("tpn-success-from"),anyMap());
    }
    @Test void cancelledTeleportDoesNotStartCooldown() {
        when(from.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(false);
        accept(); loaded(); service.request(from,"Sora");
        verify(messages).send(eq(from),eq("tpn-cancelled"),anyMap()); verify(messages,times(2)).send(eq(from),eq("tpn-sent"),anyMap());
        verify(messages,never()).send(eq(from),eq("tpn-success-from"),anyMap());
    }
    @Test void reloadAndChunkTimeoutInvalidateOutstandingCallbacks() {
        accept(); timers.getLast().run(); loaded(); verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        service.request(from,"Sora"); service.stop(); service.answer(to,true); verify(messages).send(to,"tpn-no-request");
    }
    @Test void sourceWorldChangeDuringChunkLoadRefusesAndLeavesNoCooldown() {
        accept(); World moved=mock(World.class); when(moved.getUID()).thenReturn(UUID.randomUUID()); when(from.getWorld()).thenReturn(moved);
        loaded(); verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        service.request(from,"Sora"); verify(messages,times(2)).send(eq(from),eq("tpn-sent"),anyMap());
        verify(messages,never()).send(eq(from),eq("tpn-success-from"),anyMap());
    }
    @Test void exceptionalChunkResultRefusesAndLeavesNoCooldown() {
        accept(); chunks.completeExceptionally(new IllegalStateException("simulated chunk failure")); pump();
        verify(from,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        service.request(from,"Sora"); verify(messages,times(2)).send(eq(from),eq("tpn-sent"),anyMap());
        verify(messages,never()).send(eq(from),eq("tpn-success-from"),anyMap());
    }
}
