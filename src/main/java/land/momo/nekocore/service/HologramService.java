package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Owns only TextDisplays carrying NekoCore's private marker. */
public final class HologramService {
    private final NekoCorePlugin plugin;
    private final NamespacedKey marker;
    private final Map<String, UUID> displays = new HashMap<>();
    public HologramService(NekoCorePlugin plugin) {
        this.plugin = plugin; marker = new NamespacedKey(plugin, "managed_hologram");
    }
    public void start() {
        for (World world : Bukkit.getWorlds()) for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class))
            if (display.getPersistentDataContainer().has(marker, PersistentDataType.STRING)) display.remove();
        displays.clear();
    }
    public TextDisplay show(String id, Location location, Component text) {
        Objects.requireNonNull(id); Objects.requireNonNull(location.getWorld());
        TextDisplay display = current(id);
        if (display == null || display.getWorld() != location.getWorld()) {
            if (display != null) display.remove();
            display = location.getWorld().spawn(location, TextDisplay.class, value -> {
                value.setPersistent(false);
                value.setBillboard(Display.Billboard.CENTER);
                value.setAlignment(TextDisplay.TextAlignment.CENTER);
                value.setDefaultBackground(false);
                value.setSeeThrough(false);
                value.setShadowed(plugin.settings().holograms().shadowed());
                value.setLineWidth(plugin.settings().holograms().lineWidth());
                value.getPersistentDataContainer().set(marker, PersistentDataType.STRING, id);
            });
            displays.put(id, display.getUniqueId());
        } else if (!display.getLocation().equals(location)) display.teleport(location);
        display.text(text);
        return display;
    }
    public void remove(String id) {
        UUID uuid = displays.remove(id); Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity != null && marker(id, entity)) entity.remove();
    }
    TextDisplay current(String id) {
        UUID uuid = displays.get(id); Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity instanceof TextDisplay display && display.isValid() && marker(id, display)) return display;
        displays.remove(id); return null;
    }
    private boolean marker(String id, Entity entity) {
        return id.equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING));
    }
    public void stop() {
        for (String id : new ArrayList<>(displays.keySet())) remove(id);
        displays.clear();
    }
}
