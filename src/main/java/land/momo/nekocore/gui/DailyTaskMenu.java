package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.service.DailyTaskService;
import land.momo.nekocore.task.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.format.DateTimeFormatter;
import java.util.*;

/** One read-only page: three easy, three normal and three hard tasks. */
public final class DailyTaskMenu {
    private static final DateTimeFormatter REFRESH = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private final NekoCorePlugin plugin;
    public DailyTaskMenu(NekoCorePlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        if (!plugin.permission(player, "nekocore.menu") || !plugin.available(player)) return;
        if (!plugin.dailyTaskSettings().enabled()) { plugin.messages().send(player, "daily-tasks.disabled"); return; }
        plugin.finish(player, plugin.dailyTasks().view(player.getUniqueId()), view -> render(player, view));
    }

    private void render(Player player, DailyTaskService.View view) {
        if (!NekoCorePlugin.present(player)) return;
        var ui = plugin.featureGui(); var config = plugin.dailyTaskSettings();
        Map<String,String> topVars = Map.of("date", view.rotation().date().toString(),
                "refresh", REFRESH.format(plugin.dailyTasks().nextRefresh()));
        var screen = ui.screen(player, 45, "daily-tasks.gui-title", topVars, "daily-tasks", () -> plugin.menus().open(player));
        fill(screen, 9, 17, config.gui().easyFiller()); fill(screen, 18, 26, config.gui().normalFiller());
        fill(screen, 27, 35, config.gui().hardFiller());
        for (var entry : view.rotation().entries()) {
            DailyTaskDefinition definition = DailyTaskDefinition.byId(entry.taskId());
            var progress = view.progress().get(entry.taskId());
            int slot = config.gui().slots(entry.difficulty()).get(entry.index());
            Map<String,String> vars = variables(definition, progress);
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().raw("daily-tasks.item-difficulty")); lore.add("");
            lore.add(plugin.messages().raw("daily-tasks.item-task"));
            lore.addAll(plugin.messages().lines("daily-tasks.tasks." + entry.taskId() + ".description"));
            lore.add(""); lore.add(plugin.messages().raw("daily-tasks.item-progress"));
            lore.add(plugin.messages().raw("daily-tasks.item-progress-value")); lore.add("");
            lore.add(plugin.messages().raw("daily-tasks.item-reward"));
            lore.add(plugin.messages().raw("daily-tasks.item-reward-coins"));
            lore.add(plugin.messages().raw("daily-tasks.item-reward-exp")); lore.add("");
            lore.add(plugin.messages().raw("daily-tasks.item-state"));
            ItemStack item = FeatureGui.icon(definition.icon(), plugin.messages().raw("daily-tasks.tasks." + entry.taskId() + ".name"), lore, vars);
            if (progress.completed()) {
                var meta = item.getItemMeta(); meta.setEnchantmentGlintOverride(true); item.setItemMeta(meta);
            }
            screen.set(slot, item);
            screen.action(slot, click -> player.playSound(player.getLocation(), "minecraft:block.amethyst_block.chime", 0.25f, 1.8f));
        }
        ui.button(screen, config.gui().backSlot(), Material.ARROW, "ui-back", "ui-back-lore", Map.of(), click -> plugin.menus().open(player));
        ui.button(screen, config.gui().infoSlot(), Material.CLOCK, "daily-tasks.info-name", "daily-tasks.info-lore", topVars, click -> {});
        ui.button(screen, config.gui().closeSlot(), Material.BARRIER, "ui-close", "ui-empty-lore", Map.of(), click -> player.closeInventory());
        ui.open(player, screen);
    }

    private Map<String,String> variables(DailyTaskDefinition definition, DailyTaskProgress progress) {
        long current = progress.progress(), target = plugin.dailyTaskSettings().target(definition.id());
        if (definition == DailyTaskDefinition.SIMPLE_GOOD_MORNING) { current /= 60; target /= 60; }
        var reward = plugin.dailyTaskSettings().reward(definition.difficulty());
        return Map.of("difficulty", plugin.messages().raw("daily-tasks.difficulty." + definition.difficulty().key()),
                "progress", "" + Math.min(current, target), "target", "" + target,
                "coins", "" + reward.coins(), "exp", "" + reward.exp(),
                "state", plugin.messages().raw(progress.completed() ? "daily-tasks.state-complete" : "daily-tasks.state-progress"));
    }

    private static void fill(FeatureGui.Screen screen, int from, int to, Material material) {
        ItemStack filler = FeatureGui.icon(material, " ", List.of(), Map.of());
        for (int slot = from; slot <= to; slot++) screen.set(slot, filler);
    }
}
