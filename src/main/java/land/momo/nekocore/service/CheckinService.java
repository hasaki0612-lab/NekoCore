package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import java.time.LocalDate;
import java.util.*;

public final class CheckinService {
    private final NekoCorePlugin plugin;
    private final Set<UUID> pending = new HashSet<>();
    public CheckinService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public String status(UUID id) {
        var settings = plugin.settings().features().checkin();
        if (!settings.enabled()) return "checkin-disabled";
        String last = plugin.store().cachedCheckin(id);
        if (last == null) return "checkin-loading";
        return last.compareTo(LocalDate.now(settings.zone()).toString()) >= 0 ? "checkin-done" : "checkin-available";
    }
    public void remind(Player player) {
        if (player.hasPermission("nekocore.checkin") && status(player.getUniqueId()).equals("checkin-available"))
            plugin.messages().send(player, "checkin-reminder");
    }
    public void claim(Player player) {
        claim(player, false);
    }
    public void claimAutomatic(Player player) {
        if (!player.hasPermission("nekocore.checkin")) return;
        if (status(player.getUniqueId()).equals("checkin-available")) claim(player, true);
    }
    private void claim(Player player, boolean automatic) {
        if (!plugin.permission(player, "nekocore.checkin") || !plugin.available(player)) return;
        var settings = plugin.settings().features().checkin();
        if (!settings.enabled()) { plugin.messages().send(player, "checkin-disabled"); return; }
        UUID id = player.getUniqueId();
        if (!pending.add(id)) { if (!automatic) plugin.messages().send(player, "loading"); return; }
        var future = plugin.store().checkin(id, settings.zone(), settings.coins(), settings.exp());
        future.whenComplete((result, error) -> plugin.onMain(() -> pending.remove(id)));
        plugin.finish(player, future, result -> {
            if (!result.claimed()) { if (!automatic) plugin.messages().send(player, "checkin-already"); plugin.menus().refresh(player); return; }
            Map<String, String> vars = plugin.variables(result.profile());
            vars.put("reward_coins", "" + settings.coins()); vars.put("reward_exp", "" + settings.exp());
            plugin.messages().send(player, automatic ? "checkin-auto-success" : "checkin-success", vars);
            if (result.profile().level() > result.previousLevel()) plugin.messages().send(player, "level-up", vars);
            player.playSound(player.getLocation(), settings.sound(), SoundCategory.MASTER, settings.volume(), settings.pitch());
            if (settings.particles() > 0) player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                    settings.particles(), 0.35, 0.4, 0.35, 0.015);
            plugin.menus().refresh(player);
        });
    }
}
