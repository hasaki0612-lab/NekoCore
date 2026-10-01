package land.momo.nekocore.integration;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.FeatureSettings;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.TitleRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import java.util.*;

/** Primary title/level is shared by chat and NameTag; the coarse location label is chat-only. */
public final class PrefixService {
    private final NekoCorePlugin plugin;
    public PrefixService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public FeatureSettings.Title equipped(UUID id) {
        TitleRepository.Titles state = plugin.titles().cached(id);
        return state == null ? null : plugin.features().levelShop().title(state.equipped());
    }
    public FeatureSettings.Title highest(UUID id) {
        TitleRepository.Titles state = plugin.titles().cached(id);
        if (state == null) return null;
        return plugin.features().levelShop().titles().stream().filter(t -> state.owned().contains(t.id()))
                .max(Comparator.comparingInt(FeatureSettings.Title::rank)).orElse(null);
    }
    public String primaryTemplate(UUID id) {
        var title = equipped(id);
        return title == null ? plugin.settings().features().chat().prefix() : title.prefix();
    }
    public Map<String, String> variables(UUID id) {
        var profile = plugin.store().cached(id);
        return Map.of("level", profile == null ? "1" : "" + profile.level());
    }
    public Component primary(UUID id) { return Messages.text(primaryTemplate(id), variables(id)); }
    public String legacyPrimary(UUID id) { return Messages.legacy(primaryTemplate(id), variables(id)); }
    public String location(UUID id) {
        var profile = plugin.store().cached(id);
        return profile == null || !profile.showIp() ? "" : plugin.locations().location(id);
    }
    public Component chatLocation(UUID id) {
        String location = location(id);
        return location.isEmpty() ? Component.empty() : Messages.text(plugin.messages().raw("location-prefix-format"), Map.of("location", location));
    }
    public String legacyLocation(UUID id) {
        String location = location(id);
        return location.isEmpty() ? "" : Messages.legacy(plugin.messages().raw("location-prefix-format"), Map.of("location", location));
    }
    public Component chat(UUID id) { return chatLocation(id).append(primary(id)); }
    public boolean autoCheckin(Player player) {
        var highest = highest(player.getUniqueId());
        return player.hasPermission(plugin.features().levelShop().autoPermission()) || highest != null && highest.autoCheckin();
    }
    public boolean coloredChat(Player player) {
        var highest = highest(player.getUniqueId());
        return player.hasPermission(plugin.features().levelShop().colorPermission()) || highest != null && highest.coloredChat();
    }
    public int cooldown(Player player) {
        var highest = highest(player.getUniqueId());
        int base = plugin.features().tpn().cooldown();
        if (highest != null) base = Math.min(base, highest.cooldown());
        if (player.hasPermission(plugin.features().levelShop().fastPermission())) base = Math.min(base, plugin.features().tpn().fastCooldown());
        return base;
    }
    public double afkExpMultiplier(UUID player) {
        var highest = highest(player);
        return highest == null ? plugin.settings().afkPool().reward().normalMultiplier() : highest.afkExpMultiplier();
    }

    /** Only sixteen legacy color codes and six-digit hex; every other character is plain text.
     * No MiniMessage, click/hover events, URLs, obfuscation, or arbitrary decorations are parsed. */
    public static Component safeChatColors(String input) {
        String palette = "0123456789abcdef";
        int[] colors = {0x000000,0x0000aa,0x00aa00,0x00aaaa,0xaa0000,0xaa00aa,0xffaa00,0xaaaaaa,
                0x555555,0x5555ff,0x55ff55,0x55ffff,0xff5555,0xff55ff,0xffff55,0xffffff};
        Component result = Component.empty(); StringBuilder text = new StringBuilder(); TextColor color = null;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                TextColor next = null; int length = 0;
                int index = palette.indexOf(code);
                if (index >= 0) { next = TextColor.color(colors[index]); length = 2; }
                else if (code == '#' && i + 8 <= input.length() && input.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    next = TextColor.fromHexString(input.substring(i + 1, i + 8)); length = 8;
                }
                if (length > 0 || code == 'r') {
                    result = result.append(Component.text(text.toString(), color)); text.setLength(0);
                    color = code == 'r' ? null : next; i += code == 'r' ? 1 : length - 1; continue;
                }
            }
            // A literal section sign must not become a client-side formatting escape.
            if (input.charAt(i) != '\u00a7') text.append(input.charAt(i));
        }
        return result.append(Component.text(text.toString(), color));
    }
}
