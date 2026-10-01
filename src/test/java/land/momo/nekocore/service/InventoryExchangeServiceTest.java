package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.data.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryExchangeServiceTest {
    final NekoCorePlugin plugin = mock(NekoCorePlugin.class);
    final CommerceRepository repo = mock(CommerceRepository.class);
    final Player player = mock(Player.class);
    final PlayerInventory inventory = mock(PlayerInventory.class);
    final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
    final World world = mock(World.class);
    final UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
    final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
    final FakeItems items = new FakeItems();
    final ItemStack[] before = new ItemStack[36], after = new ItemStack[36];
    final AtomicReference<ItemStack[]> actual = new AtomicReference<>(before);
    final AtomicReference<String> marker = new AtomicReference<>();
    final CompletableFuture<CommerceRepository.Exchange> prepared = new CompletableFuture<>();
    final CompletableFuture<Void> committed = new CompletableFuture<>();
    InventoryExchangeService service;
    CommerceRepository.Exchange exchange;
    MockedStatic<Bukkit> bukkit;
    @BeforeEach void setup() {
        when(plugin.getName()).thenReturn("NekoCore"); when(plugin.commerce()).thenReturn(repo);
        when(plugin.namespace()).thenReturn("nekocore");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger()); when(plugin.messages()).thenReturn(mock(land.momo.nekocore.config.Messages.class));
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
        when(world.getUID()).thenReturn(worldId); when(player.getInventory()).thenReturn(inventory); when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(inventory.getStorageContents()).thenAnswer(ignored -> actual.get());
        doAnswer(call -> { actual.set(call.getArgument(0)); return null; }).when(inventory).setStorageContents(any(ItemStack[].class));
        when(pdc.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenAnswer(ignored -> marker.get());
        doAnswer(call -> { marker.set(call.getArgument(2)); return null; }).when(pdc).set(any(NamespacedKey.class),eq(PersistentDataType.STRING),anyString());
        after[0] = items.item("stone",16,64);
        exchange = new CommerceRepository.Exchange(UUID.randomUUID(),id,worldId,"store",new byte[]{1},new byte[]{2},-48,"stone","2026-09-29","buy",16,null,null,0);
        when(repo.commit(any())).thenReturn(committed); when(repo.commit(any(), any())).thenReturn(committed);
        when(repo.rollback(any())).thenReturn(CompletableFuture.completedFuture(null));
        bukkit = mockStatic(Bukkit.class); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        service = new InventoryExchangeService(plugin);
    }
    @AfterEach void cleanup() { bukkit.close(); }
    void pump() { while(!callbacks.isEmpty()) callbacks.remove().run(); }
    CompletableFuture<Void> start() { return service.execute(player,exchange,before,after,() -> true,() -> prepared); }
    @Test void preparesBeforeTouchingInventoryThenSavesReceiptBeforeAcknowledgement() {
        var result = start(); assertTrue(service.busy(id)); verify(inventory,never()).setStorageContents(any());
        prepared.complete(exchange); verify(inventory,never()).setStorageContents(any()); // DB completion never uses Bukkit directly
        pump(); assertEquals(16,actual.get()[0].getAmount()); assertEquals(exchange.id().toString(),marker.get()); verify(player).saveData();
        assertFalse(result.isDone()); assertTrue(service.busy(id));
        committed.complete(null); pump(); assertTrue(result.isDone()); assertFalse(result.isCompletedExceptionally()); assertFalse(service.busy(id));
    }
    @Test void databasePrepareFailureNeverGivesItemsOrReceipt() {
        var result = start(); prepared.completeExceptionally(new StorageException("not-enough-coins")); pump();
        assertTrue(result.isCompletedExceptionally()); assertFalse(service.busy(id)); assertNull(actual.get()[0]); verify(player,never()).saveData();
        verify(repo,never()).commit(any()); verify(repo,never()).rollback(any());
    }
    @Test void duplicateClicksCannotPrepareAnotherTransfer() {
        start(); AtomicInteger attempts = new AtomicInteger();
        var duplicate = service.execute(player,exchange,before,after,() -> true,() -> { attempts.incrementAndGet(); return prepared; });
        assertTrue(duplicate.isCompletedExceptionally()); assertEquals(0,attempts.get());
    }
    @Test void changedInventoryOrCursorRollsBackBeforeDelivery() {
        var result = start(); actual.set(after); prepared.complete(exchange); pump();
        assertTrue(result.isCompletedExceptionally()); verify(repo).rollback(exchange); verify(inventory,never()).setStorageContents(any());
    }
    @Test void changedWorldAndReconnectNeverReceiveOldDelivery() {
        var result = start(); World other = mock(World.class); when(other.getUID()).thenReturn(UUID.randomUUID()); when(player.getWorld()).thenReturn(other);
        prepared.complete(exchange); pump(); assertTrue(result.isCompletedExceptionally()); verify(repo).rollback(exchange);
        verify(player,never()).saveData();
    }
    @Test void revokedPermissionOrReadonlyWorldCheckCancelsPreparedOperation() {
        var result = service.execute(player,exchange,before,after,() -> false,() -> prepared);
        prepared.complete(exchange); pump(); verify(repo).rollback(exchange); assertTrue(result.isCompletedExceptionally()); assertNull(actual.get()[0]);
    }
    @Test void commitFailureQuarantinesRatherThanDuplicatingOrRefundingDeliveredItems() {
        var result = start(); prepared.complete(exchange); pump(); committed.completeExceptionally(new IllegalStateException("disk")); pump();
        assertTrue(result.isCompletedExceptionally()); assertTrue(service.busy(id)); assertEquals(16,actual.get()[0].getAmount());
        verify(repo,never()).rollback(any());
    }
    @Test void playerSaveFailureLeavesJournalForInspectionNotFakeSuccess() {
        doThrow(new IllegalStateException("player save")).when(player).saveData();
        var result = start(); prepared.complete(exchange); pump();
        assertTrue(result.isCompletedExceptionally()); assertTrue(service.busy(id)); verify(repo,never()).commit(any()); verify(repo,never()).rollback(any());
    }
    @Test void shutdownBeforeDeliveryWaitsForPrepareThenCompensatesWithoutBukkit() {
        start(); var drained = service.drain(); assertFalse(drained.isDone()); prepared.complete(exchange);
        assertTrue(drained.isDone()); verify(repo).rollback(exchange); pump(); verify(inventory,never()).setStorageContents(any());
    }
    @Test void preparedReceiptRecoveryAcknowledgesWithoutReplayingItems() {
        marker.set(exchange.id().toString()); actual.set(after); recover(false); committed.complete(null); pump();
        assertFalse(service.busy(id)); verify(repo).commit(exchange.id(), CoinChangeReason.RECOVERY); verify(inventory,never()).setStorageContents(any());
    }
    @Test void unappliedPreparedRecoveryRefundsOnlyWhenBeforeSnapshotMatches() {
        recover(false); pump(); assertFalse(service.busy(id)); verify(repo).rollback(exchange); verify(inventory,never()).setStorageContents(any());
    }
    @Test void acknowledgedButMissingReceiptIsNotBlindlyReplayedOrRefunded() {
        recover(true); assertTrue(service.busy(id)); verify(repo,never()).commit(any()); verify(repo,never()).rollback(any()); verify(inventory,never()).setStorageContents(any());
    }
    void recover(boolean wasCommitted) {
        when(repo.recovery(id)).thenReturn(CompletableFuture.completedFuture(Optional.of(new CommerceRepository.Recovery(exchange,wasCommitted))));
        try (var codec = mockStatic(InventoryItems.class, CALLS_REAL_METHODS)) {
            codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenAnswer(call -> ((byte[]) call.getArgument(0))[0] == 1 ? before : after);
            service.recover(player, () -> {}); pump();
        }
    }
    @Test void multiverseStyleInventoryRollbackDoesNotUnlockBagOrReplay() {
        marker.set(exchange.id().toString()); recover(false);
        assertTrue(service.busy(id)); verify(repo,never()).commit(any()); verify(repo,never()).rollback(any());
    }
    @Test void consumingPurchasedItemsAfterCommitIsNormalAndDoesNotQuarantine() {
        marker.set(exchange.id().toString()); recover(true); committed.complete(null); pump();
        assertFalse(service.busy(id)); verify(inventory,never()).setStorageContents(any());
    }
}
