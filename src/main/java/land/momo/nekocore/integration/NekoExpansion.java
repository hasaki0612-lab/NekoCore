package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class NekoExpansion extends PlaceholderExpansion {
    private final NekoCorePlugin plugin;
    public NekoExpansion(NekoCorePlugin plugin) { this.plugin = plugin; }
    @Override public String getIdentifier() { return "nekocore"; }
    @Override public String getAuthor() { return "羽咲"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        // UUID lookup is the only OfflinePlayer access. Never load profiles or query JDBC here.
        var profile = plugin.data().view(player.getUniqueId());
        if (profile == null) return "";
        var progress = plugin.settings().curve().progress(profile.exp());
        var title = plugin.prefixes().equipped(profile.uuid());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "coins" -> "" + profile.coins();
            case "level" -> "" + progress.level();
            case "exp" -> "" + progress.exp();
            case "exp_needed" -> "" + progress.needed();
            case "playtime" -> NekoCorePlugin.formatPlaytime(profile.playtimeSeconds());
            case "level_prefix" -> Messages.legacy(plugin.settings().features().chat().prefix(), java.util.Map.of("level", "" + progress.level()));
            case "title" -> title == null ? "" : title.name();
            case "title_prefix" -> title == null ? "" : Messages.legacy(title.prefix(), java.util.Map.of());
            case "display_prefix" -> plugin.prefixes().legacyPrimary(profile.uuid());
            case "location" -> plugin.prefixes().location(profile.uuid());
            case "location_prefix" -> plugin.prefixes().legacyLocation(profile.uuid());
            default -> null;
        };
    }
}
