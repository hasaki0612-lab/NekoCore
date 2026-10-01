package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.service.InventoryItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.function.*;

/** Shared navigation/click boundary for the new menus. Item icons never become inventory contents. */
public final class FeatureGui implements Listener {
    public static final class Screen implements InventoryHolder {
        public final UUID owner;
        public final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
        public BiConsumer<Integer, ClickType> bottom;
        public Runnable rejected;
        public boolean busy;
        private boolean queued;
        private final long generation;
        private Inventory inventory;
        public final String kind;
        private Screen(UUID owner, long generation, String kind) { this.owner = owner; this.generation = generation; this.kind = kind; }
        @Override public Inventory getInventory() { return inventory; }
        public void set(int slot, ItemStack item) { inventory.setItem(slot, item); }
        public void action(int slot, Consumer<ClickType> action) { actions.put(slot, action); }
    }
    private final NekoCorePlugin plugin;
    private long generation;
    public FeatureGui(NekoCorePlugin plugin) { this.plugin = plugin; }
    public Screen screen(Player p, int size, String titleKey, Map<String, String> vars, String kind, Runnable back) {
        Screen screen = new Screen(p.getUniqueId(), generation, kind);
        screen.inventory = Bukkit.createInventory(screen, size, Messages.text(plugin.messages().raw(titleKey), vars));
        for (int i = 0; i < size; i++) screen.set(i, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of(), Map.of()));
        button(screen, size - 1, Material.ARROW, back == null ? "ui-close" : "ui-back", "ui-back-lore", Map.of(),
                click -> { if (back == null) p.closeInventory(); else back.run(); });
        return screen;
    }
    public void button(Screen screen, int slot, Material material, String nameKey, String loreKey, Map<String,String> vars, Consumer<ClickType> action) {
        screen.set(slot, icon(material, plugin.messages().raw(nameKey), plugin.messages().lines(loreKey), vars));
        screen.action(slot, action);
    }
    public static ItemStack icon(Material material, String name, List<String> lore, Map<String,String> vars) {
        return decorate(new ItemStack(material), name, lore, vars);
    }
    public static ItemStack decorate(ItemStack source, String name, List<String> lore, Map<String,String> vars) {
        ItemStack item = source.clone(); var meta = item.getItemMeta();
        meta.displayName(Messages.text(name, vars).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Messages.text(line, vars).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta); return item;
    }
    public boolean open(Player p, Screen screen) {
        if (!InventoryItems.empty(p.getItemOnCursor())) { plugin.messages().send(p, "ui-cursor"); return false; }
        p.openInventory(screen.inventory); return true;
    }
    public boolean current(Player p, Screen screen) {
        return NekoCorePlugin.present(p) && screen.owner.equals(p.getUniqueId()) && screen.generation == generation
                && p.getOpenInventory().getTopInventory().getHolder() == screen;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen screen)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !current(player, screen) || screen.busy || screen.queued) return;
        // Explicit single/stack transfers only; no cursor, hotbar swaps, offhand, double collection, or creative cloning.
        ClickType click = event.getClick();
        if (!(click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT)) {
            if (screen.rejected != null) screen.rejected.run(); return;
        }
        int raw = event.getRawSlot(); Consumer<ClickType> action = screen.actions.get(raw);
        if (raw >= screen.inventory.getSize() && event.getClickedInventory() == player.getInventory() && screen.bottom != null) {
            int slot = event.getSlot(); action = type -> screen.bottom.accept(slot, type);
        }
        if (action == null) return;
        Consumer<ClickType> selected = action; screen.queued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            screen.queued = false;
            if (current(player, screen) && !screen.busy && plugin.available(player)) selected.accept(click);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Screen screen) {
            event.setCancelled(true); if (screen.rejected != null) screen.rejected.run();
        }
    }
    public void close(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Screen) p.closeInventory();
    }
    public void closeAll() { generation++; Bukkit.getOnlinePlayers().forEach(this::close); }
}
