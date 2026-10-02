package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Adds presentation and chat interaction to an existing Citizens NPC; never creates or edits an NPC. */
public final class MascotService implements Listener {
    static final String HOLOGRAM_ID = "mascot";
    interface CitizensLookup { int id(Entity entity); Entity entity(int id); }
    static final class Clicks {
        final ArrayDeque<Long> normal = new ArrayDeque<>();
        long nextOverLimitChat;
    }
    private final NekoCorePlugin plugin;
    private final CitizensLookup citizens;
    private final LongSupplier clock;
    private final Map<UUID, Clicks> clicks = new HashMap<>();
    private BukkitTask reconcileTask;
    private boolean warned;
    public MascotService(NekoCorePlugin plugin) { this(plugin, new ReflectionCitizens(), System::currentTimeMillis); }
    MascotService(NekoCorePlugin plugin, CitizensLookup citizens, LongSupplier clock) {
        this.plugin = plugin; this.citizens = citizens; this.clock = clock;
    }
    public void restart() {
        stop(); if (!plugin.settings().mascot().enabled()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reconcile();
        reconcileTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 100L, 100L);
    }
    void reconcile() {
        var config = plugin.settings().mascot(); Entity npc = citizens.entity(config.npcId());
        if (npc == null || !npc.isValid()) {
            plugin.holograms().remove(HOLOGRAM_ID);
            if (!warned) { warned = true; plugin.getLogger().warning("未找到已生成的 Citizens NPC #" + config.npcId() + "；不会创建替代 NPC。"); }
            return;
        }
        warned = false;
        if (!config.hologramEnabled()) { plugin.holograms().remove(HOLOGRAM_ID); return; }
        Component title = Component.empty();
        for (String line : config.hologramLines()) {
            if (!title.equals(Component.empty())) title = title.append(Component.newline());
            title = title.append(Messages.text(line, Map.of("server", plugin.settings().serverName())).decoration(TextDecoration.STRIKETHROUGH, false));
        }
        plugin.holograms().show(HOLOGRAM_ID, npc.getLocation().add(0, config.hologramYOffset(), 0),
                title);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void interact(PlayerInteractEntityEvent event) {
        var config = plugin.settings().mascot();
        if (event.getHand() != EquipmentSlot.HAND || !config.enabled()
                || citizens.id(event.getRightClicked()) != config.npcId()) return;
        Player player = event.getPlayer(); long now = clock.getAsLong();
        Clicks state = clicks.computeIfAbsent(player.getUniqueId(), ignored -> new Clicks());
        long cutoff = now - config.windowSeconds() * 1000L;
        while (!state.normal.isEmpty() && state.normal.peekFirst() <= cutoff) state.normal.removeFirst();
        if (state.normal.size() < config.normalClickLimit()) {
            state.normal.addLast(now);
            particles(event.getRightClicked(), config.normalParticle(), config.normalParticleCount());
            reply(player, plugin.messages().lines("mascot.replies"));
            return;
        }
        particles(event.getRightClicked(), config.overLimitParticle(), config.overLimitParticleCount());
        if (now >= state.nextOverLimitChat) {
            state.nextOverLimitChat = now + config.overLimitCooldownSeconds() * 1000L;
            reply(player, plugin.messages().lines("mascot.over-limit-replies"));
        }
    }
    private void particles(Entity npc, String configured, int count) {
        if (count <= 0) return;
        try {
            Particle particle = Particle.valueOf(configured.substring(configured.indexOf(':') + 1).toUpperCase(Locale.ROOT));
            npc.getWorld().spawnParticle(particle, npc.getLocation().add(0, 1.1, 0), count, .35, .45, .35, .01);
        } catch (IllegalArgumentException ignored) { }
    }
    private static void reply(Player player, List<String> replies) {
        if (!replies.isEmpty()) player.sendMessage(Messages.text(replies.get(ThreadLocalRandom.current().nextInt(replies.size())), Map.of()));
    }
    public void quit(UUID player) { clicks.remove(player); }
    public void stop() {
        HandlerList.unregisterAll(this);
        if (reconcileTask != null) { reconcileTask.cancel(); reconcileTask = null; }
        clicks.clear(); if (plugin.holograms() != null) plugin.holograms().remove(HOLOGRAM_ID);
    }

    /** Reflection keeps Citizens optional while still resolving by the authoritative NPC registry ID. */
    static final class ReflectionCitizens implements CitizensLookup {
        private Object registry() throws ReflectiveOperationException {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            return api.getMethod("getNPCRegistry").invoke(null);
        }
        @Override public int id(Entity entity) {
            try {
                Object registry = registry(); Method get = registry.getClass().getMethod("getNPC", Entity.class);
                Object npc = get.invoke(registry, entity); return npc == null ? -1 : (Integer) npc.getClass().getMethod("getId").invoke(npc);
            } catch (ReflectiveOperationException | LinkageError e) { return -1; }
        }
        @Override public Entity entity(int id) {
            try {
                Object registry = registry(); Object npc = registry.getClass().getMethod("getById", int.class).invoke(registry, id);
                if (npc == null || !((Boolean) npc.getClass().getMethod("isSpawned").invoke(npc))) return null;
                return (Entity) npc.getClass().getMethod("getEntity").invoke(npc);
            } catch (ReflectiveOperationException | LinkageError e) { return null; }
        }
    }
}
