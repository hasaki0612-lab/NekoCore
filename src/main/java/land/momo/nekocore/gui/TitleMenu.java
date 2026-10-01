package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.FeatureSettings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.*;

/** Internal administrator/NPC entry only. Owned titles remain free to switch or unequip. */
public final class TitleMenu {
    private final NekoCorePlugin plugin;
    private final Set<UUID> pending = new HashSet<>();
    public TitleMenu(NekoCorePlugin plugin) { this.plugin = plugin; }
    private boolean allowed(Player player) {
        if (!plugin.featuresAvailable(player) || !plugin.permission(player, "nekocore.levelshop")) return false;
        if (!plugin.features().levelShop().enabled()) { plugin.messages().send(player, "feature-disabled"); return false; }
        if (!plugin.features().levelShop().worlds().contains(player.getWorld().getName())) { plugin.messages().send(player, "title-lobby-only"); return false; }
        if (plugin.titles().cached(player.getUniqueId()) == null) { plugin.messages().send(player, "loading"); return false; }
        if (pending.contains(player.getUniqueId())) { plugin.messages().send(player, "exchange-busy"); return false; }
        return plugin.exchanges().idle(player);
    }
    public void open(Player player) {
        if (!allowed(player)) return;
        var ui = plugin.featureGui(); var screen = ui.screen(player, 27, "title-menu-title", Map.of(), "titles", null);
        for (var title : plugin.features().levelShop().titles()) {
            Map<String,String> vars = variables(player, title);
            List<String> lore = new ArrayList<>(title.description()); lore.addAll(plugin.messages().lines("title-item-lore"));
            screen.set(title.slot(), FeatureGui.icon(title.icon(), plugin.messages().raw("title-item-name"), lore, vars));
            screen.action(title.slot(), click -> detail(player, title));
        }
        ui.button(screen, 22, Material.NAME_TAG, "title-unequip", "title-unequip-lore", Map.of(), click -> change(player, null, screen));
        ui.open(player, screen);
    }
    public boolean hasPending() { return !pending.isEmpty(); }
    private void detail(Player player, FeatureSettings.Title title) {
        if (!allowed(player)) return;
        var ui = plugin.featureGui(); var vars = variables(player, title);
        var screen = ui.screen(player, 27, "title-detail-title", vars, "titles", () -> open(player));
        screen.set(13, FeatureGui.icon(title.icon(), plugin.messages().raw("title-item-name"), title.description(), vars));
        boolean owned = plugin.titles().cached(player.getUniqueId()).owned().contains(title.id());
        ui.button(screen, 22, Material.SUNFLOWER, owned ? "title-equip" : "title-purchase", "title-confirm-lore", vars, click -> change(player, title, screen));
        ui.open(player, screen);
    }
    private void change(Player player, FeatureSettings.Title title, FeatureGui.Screen screen) {
        if (!allowed(player) || !plugin.featureGui().current(player, screen)) return;
        UUID id = player.getUniqueId();
        boolean purchase = title != null && !plugin.titles().cached(id).owned().contains(title.id());
        pending.add(id); screen.busy = true;
        var future = purchase ? plugin.titles().purchase(id, title.id(), title.price()) : plugin.titles().equip(id, title == null ? "" : title.id());
        future.whenComplete((state, error) -> plugin.onMain(() -> { pending.remove(id); screen.busy = false; }));
        plugin.finish(player, future, state -> {
            plugin.nameTags().update(player);
            Map<String,String> vars = title == null ? Map.of() : variables(player, title);
            plugin.messages().send(player, title == null ? "title-removed" : purchase ? "title-purchased" : "title-equipped", vars);
            if (plugin.featureGui().current(player, screen)) open(player);
        });
    }
    private Map<String,String> variables(Player player, FeatureSettings.Title title) {
        var state = plugin.titles().cached(player.getUniqueId());
        return Map.of("title", title.name(), "title_prefix", title.prefix(), "price", "" + title.price(),
                "coins", "" + plugin.data().view(player.getUniqueId()).coins(), "status", plugin.messages().raw(state.equipped().equals(title.id())
                        ? "title-state-equipped" : state.owned().contains(title.id()) ? "title-state-owned" : "title-state-unowned"));
    }
}
