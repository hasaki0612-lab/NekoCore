package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.FeatureSettings;
import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.service.InventoryItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** A global bag with explicit direct-to-slot transfers. Cursor items never cross a persistence boundary. */
public final class BagMenu implements Listener {
    private static final class View {
        final CommerceRepository.Bag saved;
        final ItemStack[] items;
        boolean arranging;
        int selected = -1;
        View(CommerceRepository.Bag saved) { this.saved = saved; items = InventoryItems.decode(saved.contents(), 36); }
    }
    private final NekoCorePlugin plugin;
    private final Map<UUID, Long> notices = new HashMap<>();
    public BagMenu(NekoCorePlugin plugin) { this.plugin = plugin; }
    private boolean allowed(Player p) {
        if (!plugin.featuresAvailable(p) || !plugin.permission(p, "nekocore.bag")) return false;
        if (!plugin.features().bag().enabled()) { plugin.messages().send(p, "feature-disabled"); return false; }
        return plugin.exchanges().idle(p);
    }
    public void open(Player p) {
        if (!allowed(p)) return;
        var ui = plugin.featureGui(); var waiting = ui.screen(p, 27, "bag-title", Map.of("capacity", "…"), "bag", null);
        waiting.busy = true; if (!ui.open(p, waiting)) return;
        UUID world = p.getWorld().getUID();
        int capacity = plugin.features().bag().capacity(plugin.data().view(p.getUniqueId()).level());
        var future = plugin.commerce().bag(p.getUniqueId(), capacity);
        future.whenComplete((value, error) -> plugin.onMain(() -> waiting.busy = false));
        plugin.finish(p, future, saved -> {
            if (!ui.current(p, waiting) || !p.getWorld().getUID().equals(world)) return;
            try { show(p, new View(saved)); }
            catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bag 内容读取失败，保留原始数据：" + p.getUniqueId(), e);
                plugin.messages().send(p, "bag-corrupt"); p.closeInventory();
            }
        });
    }
    private void show(Player p, View view) {
        int capacity = view.saved.capacity();
        var ui = plugin.featureGui(); var screen = ui.screen(p, capacity + 9, "bag-title", Map.of("capacity", "" + capacity), "bag", null);
        Map<String,String> vars = variables(p, view);
        for (int slot = 0; slot < capacity; slot++) {
            int index = slot; screen.set(slot, InventoryItems.empty(view.items[slot]) ? null : view.items[slot].clone());
            screen.action(slot, click -> {
                if (!writable(p)) return;
                if (view.arranging) arrange(p, view, screen, index); else transfer(p, view, screen, index, true, click.isRightClick());
            });
        }
        screen.set(capacity, FeatureGui.icon(Material.MAP, plugin.messages().raw("bag-status-name"), plugin.messages().lines("bag-status-lore"), vars));
        ui.button(screen, capacity + 3, Material.CHEST, view.arranging ? "bag-sort-on" : "bag-sort-off", "bag-sort-lore", vars, click -> {
            if (!writable(p)) return; view.arranging = !view.arranging; view.selected = -1; show(p, view);
        });
        screen.bottom = (slot, click) -> {
            if (!writable(p) || slot < 0 || slot >= 36) return;
            if (view.arranging) { plugin.messages().send(p, "bag-sort-stop-first"); return; }
            transfer(p, view, screen, slot, false, click.isRightClick());
        };
        screen.rejected = () -> { if (!isWritable(p)) readonlyNotice(p); };
        ui.open(p, screen);
    }
    private Map<String,String> variables(Player p, View view) {
        return Map.of("capacity", "" + view.saved.capacity(), "world", p.getWorld().getName(),
                "status", plugin.messages().raw(isWritable(p) ? "bag-state-writable" : "bag-state-readonly"),
                "level27", "" + plugin.features().bag().level27(), "level36", "" + plugin.features().bag().level36());
    }
    private boolean isWritable(Player p) { return plugin.features().bag().writable(p.getWorld().getName()); }
    private boolean writable(Player p) {
        if (!allowed(p)) return false;
        if (!isWritable(p)) { readonlyNotice(p); return false; }
        return true;
    }
    private void readonlyNotice(Player p) {
        long now = System.nanoTime(), next = notices.getOrDefault(p.getUniqueId(), Long.MIN_VALUE);
        if (next != Long.MIN_VALUE && now - next < 0) return;
        notices.put(p.getUniqueId(), now + plugin.features().bag().noticeSeconds() * 1_000_000_000L);
        plugin.messages().send(p, "bag-readonly");
    }
    private void arrange(Player p, View view, FeatureGui.Screen screen, int slot) {
        if (view.selected < 0) {
            view.selected = slot;
            plugin.messages().send(p, "bag-sort-selected", Map.of("slot", "" + (slot + 1))); return;
        }
        int first = view.selected; view.selected = -1;
        if (first == slot) return;
        ItemStack[] after = InventoryItems.copy(view.items); ItemStack swap = after[first]; after[first] = after[slot]; after[slot] = swap;
        ItemStack[] inventory = InventoryItems.copy(p.getInventory().getStorageContents());
        save(p, view, screen, inventory, inventory, after);
    }
    private void transfer(Player p, View view, FeatureGui.Screen screen, int slot, boolean taking, boolean single) {
        ItemStack[] before = InventoryItems.copy(p.getInventory().getStorageContents());
        ItemStack[] source = taking ? view.items : before;
        if (slot < 0 || slot >= source.length || InventoryItems.empty(source[slot])) return;
        ItemStack item = source[slot]; int amount = single ? 1 : item.getAmount();
        ItemStack[] after, bagAfter;
        try {
            if (taking) {
                after = InventoryItems.add(before, item, amount, 36);
                bagAfter = InventoryItems.removeSlot(view.items, slot, amount);
            } else {
                bagAfter = InventoryItems.add(view.items, item, amount, view.saved.capacity());
                after = InventoryItems.removeSlot(before, slot, amount);
            }
        } catch (IllegalArgumentException e) { plugin.messages().send(p, taking ? "inventory-full" : "bag-full"); return; }
        save(p, view, screen, before, after, bagAfter);
    }
    private void save(Player p, View view, FeatureGui.Screen screen, ItemStack[] before, ItemStack[] after, ItemStack[] bagAfter) {
        if (!writable(p) || !plugin.featureGui().current(p, screen)) return;
        if (!InventoryItems.empty(p.getItemOnCursor())) { plugin.messages().send(p, "ui-cursor"); return; }
        var exchange = new CommerceRepository.Exchange(UUID.randomUUID(), p.getUniqueId(), p.getWorld().getUID(), "bag",
                InventoryItems.encode(before), InventoryItems.encode(after), 0, "", "", "", 0,
                view.saved.contents(), InventoryItems.encode(bagAfter), view.saved.revision());
        FeatureSettings config = plugin.features(); screen.busy = true;
        var future = plugin.exchanges().execute(p, exchange, before, after,
                () -> p.hasPermission("nekocore.bag") && plugin.features() == config && config.bag().enabled() && config.bag().writable(p.getWorld().getName()),
                () -> plugin.commerce().prepareBag(exchange));
        future.whenComplete((ignored, error) -> plugin.onMain(() -> {
            screen.busy = false;
            if (plugin.featureGui().current(p, screen) && !plugin.exchanges().busy(p.getUniqueId())) open(p);
        }));
        plugin.finish(p, future, ignored -> {}); // Silent per-stack success; failure is never hidden.
    }
    @EventHandler(priority=EventPriority.MONITOR) public void world(PlayerChangedWorldEvent e) { plugin.featureGui().close(e.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e) { plugin.featureGui().close(e.getEntity()); }
    @EventHandler public void quit(PlayerQuitEvent e) { notices.remove(e.getPlayer().getUniqueId()); }
}
