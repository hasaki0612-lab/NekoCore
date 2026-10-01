package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.model.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

public final class MenuService implements Listener {
    private final NekoCorePlugin plugin;
    public MenuService(NekoCorePlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        if (!plugin.permission(player, "nekocore.menu") || !plugin.available(player)) return;
        Profile profile = plugin.data().view(player.getUniqueId());
        if (profile == null) { plugin.messages().send(player, "loading"); return; }
        var menu = plugin.settings().menu();
        if (!menu.enabled()) { plugin.messages().send(player, "feature-disabled"); return; }
        var vars = plugin.variables(profile);
        MenuHolder holder = new MenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), Messages.text(menu.title(), vars));
        holder.inventory = inventory;
        ItemStack filler = item(menu.filler(), menu.fillerName(), List.of(), vars, player);
        for (int i = 0; i < menu.size(); i++) inventory.setItem(i, filler);
        List<String> actions = List.of("profile", "daily", "checkin", "privacy", "survival", "survival-new", "minigames", "afk-pool")
                .stream().filter(this::visible).filter(menu.buttons()::containsKey).toList();
        List<Integer> automatic = menu.autoLayout() ? centeredSlots(menu.size(), actions.size()) : List.of();
        for (int index = 0; index < actions.size(); index++) {
            String action = actions.get(index); var button = menu.buttons().get(action);
            int slot = menu.autoLayout() ? automatic.get(index) : button.slot();
            inventory.setItem(slot, item(button.material(), button.name(), button.lore(), vars, player));
            holder.actions.put(slot, action);
        }
        player.openInventory(inventory);
    }

    private boolean visible(String action) {
        var settings = plugin.settings();
        return switch (action) {
            case "daily" -> plugin.dailyTaskSettings().enabled();
            case "checkin" -> settings.features().checkin().enabled();
            case "privacy" -> settings.locationPrefix().enabled();
            case "survival" -> settings.features().survivalEnabled() && Bukkit.getWorld(settings.survivalWorld()) != null;
            case "survival-new" -> settings.features().secondWorldEnabled() && Bukkit.getWorld(settings.features().newWorld()) != null;
            case "minigames" -> settings.features().minigamesEnabled()
                    && Bukkit.getWorld(settings.features().minigames().world()) != null;
            case "afk-pool" -> settings.afkPool().enabled()
                    && Bukkit.getWorld(settings.afkPool().teleport().world()) != null;
            default -> true;
        };
    }

    static List<Integer> centeredSlots(int size, int count) {
        if (count <= 0) return List.of();
        int rows = size / 9, firstCount = count <= 5 ? count : (count + 1) / 2;
        int secondCount = count - firstCount;
        int firstRow = secondCount == 0 ? rows / 2 : Math.max(0, rows / 2 - 1);
        List<Integer> result = new ArrayList<>(count);
        addCenteredRow(result, firstRow, firstCount);
        if (secondCount > 0) addCenteredRow(result, Math.min(rows - 1, firstRow + 2), secondCount);
        return List.copyOf(result);
    }

    private static void addCenteredRow(List<Integer> slots, int row, int count) {
        int[] columns = switch (count) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            default -> throw new IllegalArgumentException("A centered menu row supports 1..5 buttons");
        };
        for (int column : columns) slots.add(row * 9 + column);
    }

    private ItemStack item(Material material, String name, List<String> lore, Map<String, String> vars, Player player) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Messages.text(name, vars).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Messages.text(line, vars).decoration(TextDecoration.ITALIC, false)).toList());
        if (meta instanceof SkullMeta skull) skull.setPlayerProfile(player.getPlayerProfile());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true); // Also covers shift-click, number keys, offhand swap and double-click collection.
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (holder.busy || event.getRawSlot() < 0 || event.getRawSlot() >= holder.inventory.getSize()) return;
        String action = holder.actions.get(event.getRawSlot());
        if (action == null || !event.isLeftClick() && !event.isRightClick() || event.isShiftClick()) return;
        holder.busy = true;
        // Inventory open/close is unsafe inside InventoryClickEvent itself; run it on the next main tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!NekoCorePlugin.present(player) || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            if (!plugin.permission(player, "nekocore.menu") || !plugin.available(player)) { holder.busy = false; return; }
            switch (action) {
                case "profile" -> {
                    Profile profile = plugin.data().view(player.getUniqueId());
                    if (profile != null) plugin.messages().profile(player, plugin.variables(profile));
                    holder.busy = false;
                }
                case "daily" -> { holder.busy = false; plugin.dailyTaskMenu().open(player); }
                case "survival", "survival-new" -> { player.closeInventory(); plugin.destinations().survival(player, action.equals("survival-new")); }
                case "minigames" -> { player.closeInventory(); plugin.destinations().minigames(player); }
                case "afk-pool" -> { player.closeInventory(); plugin.destinations().afkPool(player); }
                case "checkin" -> { holder.busy = false; plugin.checkins().claim(player); }
                case "privacy" -> {
                    var future = plugin.store().togglePrivacy(player.getUniqueId());
                    future.whenComplete((value, error) -> plugin.onMain(() -> holder.busy = false));
                    plugin.finish(player, future, profile -> {
                        plugin.messages().send(player, "privacy-updated", plugin.variables(profile));
                        if (player.getOpenInventory().getTopInventory() == holder.inventory) open(player);
                    });
                }
                default -> holder.busy = false;
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    public void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) open(player);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) player.closeInventory();
    }

    static final class MenuHolder implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;
        private boolean busy;
        MenuHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
