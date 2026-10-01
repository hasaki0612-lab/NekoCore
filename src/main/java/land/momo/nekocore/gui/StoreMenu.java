package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.FeatureSettings;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.service.EnchantmentService;
import land.momo.nekocore.service.InventoryItems;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Categories -> products -> quantity. No inventory icon is ever used as the traded ItemStack. */
public final class StoreMenu implements Listener {
    public record Entry(String id, String category, String name, ItemStack item, long price, boolean sellable,
                        int buyLimit, int sellLimit, int slot, String batch) {}
    private record Input(Player player, Entry entry, boolean buy, int maximum, BukkitTask timeout) {}
    private final NekoCorePlugin plugin;
    private final Map<UUID, Input> inputs = new HashMap<>();
    public StoreMenu(NekoCorePlugin plugin) { this.plugin = plugin; }
    private boolean allowed(Player p) {
        if (!plugin.featuresAvailable(p) || !plugin.permission(p, "nekocore.store")) return false;
        if (!plugin.features().shop().enabled()) { plugin.messages().send(p, "feature-disabled"); return false; }
        return plugin.exchanges().idle(p);
    }
    public void open(Player p) {
        if (!allowed(p)) return;
        cancelInput(p.getUniqueId());
        var ui = plugin.featureGui();
        var screen = ui.screen(p, plugin.features().shop().rows() * 9, "store-title", Map.of(), "store", null);
        lighten(screen, screen.getInventory().getSize());
        for (var category : plugin.features().shop().categories()) {
            ui.button(screen, category.slot(), category.icon(), "store-category-name", "store-category-lore", Map.of("category", category.name()),
                    click -> products(p, category.id(), 0));
        }
        ui.open(p, screen);
    }
    private List<Entry> entries(String category) {
        if (category.equals("enchantments")) {
            var batch = plugin.enchantments().current();
            if (batch == null) return List.of();
            return batch.offers().stream().map(offer -> {
                String key = offer.enchantment().replace("minecraft:", "");
                String enchantName = plugin.messages().raw("enchant-names." + key);
                if (enchantName.equals("enchant-names." + key)) enchantName = key;
                String name = Messages.replace(plugin.messages().raw("store-book-name"), Map.of("enchantment", enchantName, "level", "" + offer.level(),
                        "tier", plugin.messages().raw(offer.maximum() ? "store-book-maximum" : "store-book-normal")));
                return new Entry("enchant-" + offer.slot(), category, name, EnchantmentService.item(offer), offer.price(), false, 1, 0,
                        -1, batch.id());
            }).toList();
        }
        return plugin.features().shop().products().stream().filter(product -> product.category().equals(category))
                .map(product -> new Entry(product.id(), product.category(), product.name(), new ItemStack(product.material()), product.price(), product.sellable(),
                        product.buyLimit() < 0 ? product.material().getMaxStackSize() : product.buyLimit(), product.sellLimit(), product.slot(), null)).toList();
    }
    private void products(Player p, String categoryId, int page) {
        if (!allowed(p)) return;
        var category = plugin.features().shop().categories().stream().filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
        if (category == null) return;
        List<Entry> entries = entries(categoryId);
        if (categoryId.equals("enchantments") && entries.isEmpty()) { plugin.messages().send(p, "loading"); return; }
        var ui = plugin.featureGui(); var screen = ui.screen(p, 54, "store-products-title", Map.of("category", category.name()), "store", () -> open(p));
        lighten(screen, 54);
        var shop = plugin.features().shop();
        Map<Integer,Entry> placed = positions(entries, shop.productSlots()); int pages = Math.max(1, (placed.keySet().stream().max(Integer::compareTo).orElse(0) / 54) + 1);
        int actual = Math.max(0, Math.min(page, pages - 1));
        for (var position : placed.entrySet()) {
            if (position.getKey() / 54 != actual) continue;
            Entry entry = position.getValue(); int slot = position.getKey() % 54;
            Map<String,String> vars = variables(p, entry, true, 1, null);
            List<String> lore = new ArrayList<>(plugin.messages().lines("store-product-lore"));
            if (entry.batch() != null) lore.addAll(plugin.messages().lines("store-book-lore"));
            screen.set(slot, FeatureGui.decorate(entry.item(), plugin.messages().raw("store-product-name"), lore, vars));
            screen.action(slot, click -> {
                if (click == ClickType.RIGHT) quantity(p, entry, true, 1);
                else if (click == ClickType.LEFT) {
                    if (!entry.sellable()) plugin.messages().send(p, "store-no-sell"); else quantity(p, entry, false, 1);
                }
            });
        }
        if (actual > 0) ui.button(screen, shop.previousSlot(), Material.ARROW, "ui-previous", "ui-empty-lore", Map.of(), click -> products(p, categoryId, actual - 1));
        if (actual < pages - 1) ui.button(screen, shop.nextSlot(), Material.ARROW, "ui-next", "ui-empty-lore", Map.of(), click -> products(p, categoryId, actual + 1));
        screen.set(shop.infoSlot(), FeatureGui.icon(Material.SUNFLOWER, plugin.messages().raw("store-layout-info-name"),
                plugin.messages().lines("store-layout-info-lore"), Map.of("coins", String.format(Locale.ROOT, "%,d", plugin.data().view(p.getUniqueId()).coins()))));
        ui.open(p, screen);
    }
    /** Explicit slots occupy page one; automatic products fill the remaining slots and further pages. */
    static Map<Integer,Entry> positions(List<Entry> entries, List<Integer> layout) {
        Map<Integer,Entry> result = new TreeMap<>();
        for (Entry entry : entries) if (entry.slot() >= 0 && layout.contains(entry.slot())) result.put(entry.slot(), entry);
        int next = 0;
        for (Entry entry : entries) if (entry.slot() < 0 || !layout.contains(entry.slot())) {
            int position;
            do { position = next / layout.size() * 54 + layout.get(next % layout.size()); next++; }
            while (result.containsKey(position));
            result.put(position, entry);
        }
        return result;
    }
    private static void lighten(FeatureGui.Screen screen, int size) {
        for (int slot = 0; slot < size - 1; slot++) screen.set(slot, null);
        for (int slot : List.of(0, 8, size >= 45 ? 36 : 27, size >= 54 ? 44 : 26))
            if (slot >= 0 && slot < size - 1) screen.set(slot, FeatureGui.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of(), Map.of()));
    }
    private void quantity(Player p, Entry entry, boolean buy, int selected) {
        if (!allowed(p)) return;
        var ui = plugin.featureGui();
        var screen = ui.screen(p, 36, "store-quantity-title", Map.of("action", action(buy), "product", entry.name()), "store", () -> products(p, entry.category(), 0));
        screen.busy = true;
        if (!ui.open(p, screen)) return;
        var future = plugin.commerce().quota(p.getUniqueId(), entry.id(), entry.batch());
        future.whenComplete((quota, error) -> plugin.onMain(() -> screen.busy = false));
        plugin.finish(p, future, quota -> {
            if (!ui.current(p, screen)) return;
            Map<String,String> vars = variables(p, entry, buy, selected, quota);
            screen.set(4, FeatureGui.decorate(entry.item(), plugin.messages().raw("store-product-name"), plugin.messages().lines("store-quantity-lore"), vars));
            int slot = 10;
            for (int amount : new int[]{1,8,16,32,64}) if (amount <= entry.item().getMaxStackSize()) {
                Map<String,String> choice = variables(p, entry, buy, amount, quota);
                ui.button(screen, slot++, Material.PAPER, "store-preset", "store-preset-lore", choice, click -> quantity(p, entry, buy, amount));
            }
            ui.button(screen, 21, Material.WRITABLE_BOOK, "store-custom", "store-custom-lore", vars, click -> beginInput(p, entry, buy, quota));
            ui.button(screen, 23, Material.SUNFLOWER, "store-confirm", "store-confirm-lore", vars, click -> trade(p, entry, buy, selected, quota, screen));
        });
    }
    private int maximum(Player p, Entry entry, boolean buy, CommerceRepository.Quota quota) {
        int remaining = Math.max(0, (buy ? entry.buyLimit() - quota.bought() : entry.sellLimit() - quota.sold()));
        return Math.min(remaining, buy ? Integer.MAX_VALUE : InventoryItems.count(p.getInventory().getStorageContents(), entry.item()));
    }
    private Map<String,String> variables(Player p, Entry entry, boolean buy, int quantity, CommerceRepository.Quota quota) {
        long unit = buy ? entry.price() : plugin.features().shop().sellPrice(entry.price());
        Map<String,String> vars = new HashMap<>();
        vars.put("product", entry.name()); vars.put("action", action(buy)); vars.put("quantity", "" + quantity);
        vars.put("buy_price", "" + entry.price()); vars.put("sell_price", entry.sellable() ? "" + plugin.features().shop().sellPrice(entry.price()) : plugin.messages().raw("store-unavailable"));
        vars.put("price", "" + unit); vars.put("total", "" + Math.multiplyExact(unit, quantity));
        vars.put("cost_color", plugin.messages().raw(!buy || plugin.data().view(p.getUniqueId()).coins() >= Math.multiplyExact(unit, quantity) ? "store-cost-affordable" : "store-cost-unaffordable"));
        vars.put("coins", "" + plugin.data().view(p.getUniqueId()).coins());
        vars.put("owned", "" + InventoryItems.count(p.getInventory().getStorageContents(), entry.item()));
        vars.put("limit", "" + (buy ? entry.buyLimit() : entry.sellLimit()));
        vars.put("remaining", "" + (quota == null ? (buy ? entry.buyLimit() : entry.sellLimit()) : Math.max(0, (buy ? entry.buyLimit() - quota.bought() : entry.sellLimit() - quota.sold()))));
        return vars;
    }
    private String action(boolean buy) { return plugin.messages().raw(buy ? "store-action-buy" : "store-action-sell"); }
    private void trade(Player p, Entry entry, boolean buy, int requested, CommerceRepository.Quota quota, FeatureGui.Screen screen) {
        if (!allowed(p) || !plugin.featureGui().current(p, screen)) return;
        if (!buy && !entry.sellable()) { plugin.messages().send(p, "store-no-sell"); return; }
        if (!InventoryItems.empty(p.getItemOnCursor())) { plugin.messages().send(p, "ui-cursor"); return; }
        int maximum = maximum(p, entry, buy, quota);
        if (requested < 1 || requested > maximum) {
            plugin.messages().send(p, "store-range", Map.of("maximum", "" + maximum));
            if (!buy && maximum > 0) quantity(p, entry, false, maximum); // smaller legal amount requires a fresh confirmation
            return;
        }
        ItemStack[] before = InventoryItems.copy(p.getInventory().getStorageContents()), after;
        try { after = buy ? InventoryItems.add(before, entry.item(), requested, 36) : InventoryItems.remove(before, entry.item(), requested); }
        catch (IllegalArgumentException e) { plugin.messages().send(p, buy ? "inventory-full" : "store-not-owned"); return; }
        long price = buy ? entry.price() : plugin.features().shop().sellPrice(entry.price());
        long cost = Math.multiplyExact(price, requested);
        var exchange = new CommerceRepository.Exchange(UUID.randomUUID(), p.getUniqueId(), p.getWorld().getUID(), "store",
                InventoryItems.encode(before), InventoryItems.encode(after), buy ? -cost : cost, entry.id(), quota.period(), buy ? "buy" : "sell", requested, null, null, 0);
        FeatureSettings config = plugin.features(); screen.busy = true;
        var future = plugin.exchanges().execute(p, exchange, before, after,
                () -> p.hasPermission("nekocore.store") && plugin.features() == config && config.shop().enabled(),
                () -> plugin.commerce().prepareStore(exchange, price, buy ? entry.buyLimit() : entry.sellLimit(), entry.batch() != null));
        future.whenComplete((none, error) -> plugin.onMain(() -> screen.busy = false));
        plugin.finish(p, future, none -> {
            plugin.messages().send(p, buy ? "store-bought" : "store-sold", variables(p, entry, buy, requested, quota));
            if (plugin.featureGui().current(p, screen)) quantity(p, entry, buy, 1);
        });
    }
    private void beginInput(Player p, Entry entry, boolean buy, CommerceRepository.Quota quota) {
        if (!allowed(p)) return;
        int max = Math.min(plugin.features().shop().maximumInput(), maximum(p, entry, buy, quota));
        if (max < 1) { plugin.messages().send(p, "store-range", Map.of("maximum", "0")); return; }
        cancelInput(p.getUniqueId()); p.closeInventory();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Input current = inputs.remove(p.getUniqueId());
            if (current != null && current.player() == p && NekoCorePlugin.present(p)) {
                plugin.messages().send(p, "store-input-timeout"); quantity(p, entry, buy, 1);
            }
        }, plugin.features().shop().inputTimeout() * 20L);
        inputs.put(p.getUniqueId(), new Input(p, entry, buy, max, task));
        plugin.messages().send(p, "store-input-prompt", Map.of("maximum", "" + max, "seconds", "" + plugin.features().shop().inputTimeout()));
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void chat(ChatEvent event) {
        Player p = event.getPlayer(); Input input = inputs.get(p.getUniqueId());
        if (input == null || input.player() != p) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.onMain(() -> {
            if (inputs.get(p.getUniqueId()) != input || !NekoCorePlugin.present(p)) return;
            if (text.equalsIgnoreCase("cancel") || text.equals("取消")) {
                cancelInput(p.getUniqueId()); plugin.messages().send(p, "store-input-cancelled"); quantity(p, input.entry(), input.buy(), 1); return;
            }
            int amount = parseQuantity(text, input.maximum());
            if (amount < 1) { plugin.messages().send(p, "store-input-invalid", Map.of("maximum", "" + input.maximum())); return; }
            cancelInput(p.getUniqueId()); quantity(p, input.entry(), input.buy(), amount);
        });
    }
    public static int parseQuantity(String input, int maximum) {
        if (!input.matches("[0-9]{1,7}")) return -1;
        int value = Integer.parseInt(input); return value >= 1 && value <= maximum ? value : -1;
    }
    public void cancelInput(UUID id) { Input input = inputs.remove(id); if (input != null) input.timeout().cancel(); }
    @EventHandler public void quit(PlayerQuitEvent e) { cancelInput(e.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent e) { cancelInput(e.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent e) { cancelInput(e.getEntity().getUniqueId()); }
    @EventHandler public void inventory(InventoryOpenEvent e) { cancelInput(e.getPlayer().getUniqueId()); }
    public void stop() { for (UUID id : new ArrayList<>(inputs.keySet())) cancelInput(id); }
}
