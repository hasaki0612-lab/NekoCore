package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.data.CommerceRepository.Exchange;
import land.momo.nekocore.data.StorageException;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Level;

/** Coordinates the main-thread inventory with a durable SQL prepare/acknowledgement journal.
 * Bukkit/player.dat and SQLite cannot form one ACID transaction. A player-data receipt disambiguates
 * the common crash windows. Ambiguous foreign-plugin/storage failures are quarantined, never replayed.
 */
public final class InventoryExchangeService implements Listener {
    private enum Stage { BEFORE, UNCERTAIN, APPLIED }
    private static final class Operation {
        final Player player; final Exchange exchange; final ItemStack[] before, after; final BooleanSupplier valid;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture<Exchange> prepared; Stage stage = Stage.BEFORE;
        Operation(Player player, Exchange exchange, ItemStack[] before, ItemStack[] after, BooleanSupplier valid) {
            this.player = player; this.exchange = exchange; this.before = InventoryItems.copy(before); this.after = InventoryItems.copy(after); this.valid = valid;
        }
    }
    private final NekoCorePlugin plugin;
    private final NamespacedKey receipt;
    private final Map<UUID, Operation> active = new HashMap<>();
    private final Map<UUID, Player> recovering = new HashMap<>();
    private final Set<UUID> quarantined = new HashSet<>();
    private boolean stopping;
    public InventoryExchangeService(NekoCorePlugin plugin) { this.plugin = plugin; receipt = new NamespacedKey(plugin, "inventory_receipt"); }
    public boolean busy(UUID id) { return active.containsKey(id) || recovering.containsKey(id) || quarantined.contains(id); }
    public boolean idle(Player player) {
        if (busy(player.getUniqueId())) { plugin.messages().send(player, quarantined.contains(player.getUniqueId()) ? "exchange-recovery" : "exchange-busy"); return false; }
        return true;
    }
    public boolean hasActive() { return !active.isEmpty() || !recovering.isEmpty(); }
    public CompletableFuture<Void> execute(Player player, Exchange exchange, ItemStack[] before, ItemStack[] after,
                                           BooleanSupplier valid, Supplier<CompletableFuture<Exchange>> prepare) {
        UUID id = player.getUniqueId();
        if (stopping || busy(id)) return CompletableFuture.failedFuture(new StorageException("exchange-busy"));
        Operation operation = new Operation(player, exchange, before, after, valid); active.put(id, operation);
        try { operation.prepared = prepare.get(); }
        catch (RuntimeException e) { active.remove(id); return CompletableFuture.failedFuture(e); }
        operation.prepared.whenComplete((ignored, error) -> plugin.onMain(() -> {
            if (stopping) return; // drain() owns the terminal operation during disable.
            if (error != null) { complete(operation, error, false); return; }
            boolean canDeliver;
            try { canDeliver = canApply(operation); }
            catch (RuntimeException e) { canDeliver = false; }
            if (!canDeliver) {
                plugin.commerce().rollback(exchange).whenComplete((none, failure) -> plugin.onMain(() ->
                        complete(operation, failure == null ? new StorageException("exchange-stale") : failure, failure != null)));
                return;
            }
            try {
                operation.stage = Stage.UNCERTAIN;
                player.getInventory().setStorageContents(InventoryItems.copy(operation.after));
                player.getPersistentDataContainer().set(receipt, PersistentDataType.STRING, exchange.id().toString());
                // Paper's supported player persistence API must run on the server thread. No JDBC here.
                // Only once per confirmed transfer, never per tick, menu open, hover, or autosave scan.
                player.saveData();
                operation.stage = Stage.APPLIED;
            } catch (RuntimeException errorApplying) { complete(operation, errorApplying, true); return; }
            plugin.commerce().commit(exchange.id()).whenComplete((none, failure) -> plugin.onMain(() -> complete(operation, failure, failure != null)));
        }));
        return operation.result;
    }
    private boolean canApply(Operation operation) {
        Player p = operation.player;
        return NekoCorePlugin.present(p) && !p.isDead() && p.getWorld().getUID().equals(operation.exchange.world())
                && operation.valid.getAsBoolean() && InventoryItems.empty(p.getItemOnCursor())
                && InventoryItems.same(operation.before, p.getInventory().getStorageContents());
    }
    private void complete(Operation operation, Throwable error, boolean quarantine) {
        UUID id = operation.player.getUniqueId(); active.remove(id, operation);
        if (quarantine) {
            quarantined.add(id);
            plugin.getLogger().log(Level.SEVERE, "物品交易保留待核对：player=" + id + " exchange=" + operation.exchange.id(), error);
            operation.result.completeExceptionally(new StorageException("exchange-recovery"));
        } else if (error != null) operation.result.completeExceptionally(error);
        else operation.result.complete(null);
    }
    /** Call after join data + Multiverse's normal inventory restoration; never teleports a player. */
    public void recover(Player player, Runnable ready) {
        UUID id = player.getUniqueId();
        if (recovering.get(id) == player) return;
        recovering.put(id, player);
        plugin.commerce().recovery(id).whenComplete((saved, error) -> plugin.onMain(() -> {
            if (recovering.get(id) != player) return;
            if (!NekoCorePlugin.present(player)) { recovering.remove(id, player); return; }
            if (error != null) { recoveryFailed(player, error); return; }
            if (saved.isEmpty()) { recovering.remove(id); quarantined.remove(id); ready.run(); return; }
            var journal = saved.get(); Exchange exchange = journal.exchange();
            String marker = player.getPersistentDataContainer().get(receipt, PersistentDataType.STRING);
            boolean applied = exchange.id().toString().equals(marker);
            boolean before, after;
            try {
                before = player.getWorld().getUID().equals(exchange.world()) && InventoryItems.empty(player.getItemOnCursor())
                        && InventoryItems.same(InventoryItems.decode(exchange.before(), 36), player.getInventory().getStorageContents());
                after = player.getWorld().getUID().equals(exchange.world()) && InventoryItems.empty(player.getItemOnCursor())
                        && InventoryItems.same(InventoryItems.decode(exchange.after(), 36), player.getInventory().getStorageContents());
            } catch (RuntimeException e) { recoveryFailed(player, e); return; }
            // A missing receipt on an already-acknowledged exchange can mean an external restore.
            // Do not guess, refund, grant items, or overwrite a Multiverse world inventory.
            if (!applied && (!before || journal.committed())) { recoveryFailed(player, new IllegalStateException("Inventory receipt/snapshot mismatch: " + exchange.id())); return; }
            // Pending exchanges were frozen: they must still match the applied snapshot.
            // After acknowledgement normal gameplay may consume/move every item, so an old
            // snapshot is NOT an authority to reject or overwrite a committed live inventory.
            if (applied && !journal.committed() && !after) {
                recoveryFailed(player, new IllegalStateException("Inventory profile may have been restored by another plugin: " + exchange.id())); return;
            }
            var future = applied ? plugin.commerce().commit(exchange.id(), land.momo.nekocore.data.CoinChangeReason.RECOVERY) : plugin.commerce().rollback(exchange);
            future.whenComplete((none, failure) -> plugin.onMain(() -> {
                if (recovering.get(id) != player) return;
                if (failure != null) { recoveryFailed(player, failure); return; }
                recovering.remove(id); quarantined.remove(id);
                if (NekoCorePlugin.present(player)) ready.run();
            }));
        }));
    }
    private void recoveryFailed(Player player, Throwable error) {
        UUID id = player.getUniqueId(); recovering.remove(id); quarantined.add(id);
        plugin.getLogger().log(Level.SEVERE, "物品记录需要人工核对，未重发或覆盖物品：" + id, error);
        if (NekoCorePlugin.present(player)) plugin.messages().send(player, "exchange-recovery");
    }
    public CompletableFuture<Void> drain() {
        stopping = true;
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (Operation operation : active.values()) {
            Stage stage = operation.stage;
            pending.add(operation.prepared.handle((value, error) -> error == null).thenCompose(prepared -> {
                if (!prepared || stage == Stage.UNCERTAIN) return CompletableFuture.completedFuture(null);
                return stage == Stage.APPLIED ? plugin.commerce().commit(operation.exchange.id()) : plugin.commerce().rollback(operation.exchange);
            }));
        }
        return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }
    // Freeze only the short prepare/apply window. Quarantined players may play normally, but not
    // initiate another NekoCore exchange until their preserved snapshots have been reconciled.
    private boolean locked(Player p) { return active.containsKey(p.getUniqueId()) || recovering.containsKey(p.getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void open(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void swap(PlayerSwapHandItemsEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void entityInteract(PlayerInteractEntityEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void consume(PlayerItemConsumeEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void damageItem(PlayerItemDamageEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void shoot(EntityShootBowEvent e) { if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void bucketFill(PlayerBucketFillEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void bucketEmpty(PlayerBucketEmptyEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void fish(PlayerFishEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void place(BlockPlaceEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void breakBlock(BlockBreakEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void teleport(PlayerTeleportEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void gameMode(PlayerGameModeChangeEvent e) { if (locked(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); plugin.messages().send(e.getPlayer(), "exchange-busy"); }
    }
}
