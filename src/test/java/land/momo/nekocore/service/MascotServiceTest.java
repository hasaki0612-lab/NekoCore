package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.config.Settings;
import net.kyori.adventure.text.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MascotServiceTest {
    @Test void disabledOrMissingCitizensRegistersNoListenerAndNoTask() {
        var plugin=mock(NekoCorePlugin.class);var settings=mock(Settings.class);
        var config=mock(Settings.Mascot.class);var manager=mock(org.bukkit.plugin.PluginManager.class);
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.settings()).thenReturn(settings);when(settings.mascot()).thenReturn(config);
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var service=new MascotService(plugin);service.restart();
            when(config.enabled()).thenReturn(true);service.restart();
            verify(manager,never()).registerEvents(any(),any());verifyNoInteractions(scheduler);
        }
    }
    @Test void overallOffsetChangesOnlyDisplayPositionAndPreservesNpcLocation() {
        var plugin=mock(NekoCorePlugin.class);var settings=mock(Settings.class);var holograms=mock(HologramService.class);
        var npc=mock(Entity.class);var world=mock(World.class);var location=new Location(world,2.5,65,4.5);
        when(plugin.settings()).thenReturn(settings);when(plugin.holograms()).thenReturn(holograms);
        when(settings.serverName()).thenReturn("My Server");
        var custom=new Settings.Mascot(true,3,true,List.of("One","Two"),1.5f,15,5,2,"minecraft:end_rod",8,"minecraft:smoke",18);
        when(settings.mascot()).thenReturn(custom);when(npc.isValid()).thenReturn(true);when(npc.getLocation()).thenAnswer(call->location.clone());
        var lookup=new MascotService.CitizensLookup(){public int id(Entity entity){return 3;}public Entity entity(int id){return npc;}};
        new MascotService(plugin,lookup,()->0).reconcile();
        verify(holograms).show(eq("mascot"),argThat(at->at.getY()==66.5),argThat((Component c)->contains(c,"One")&&contains(c,"Two")));
        org.junit.jupiter.api.Assertions.assertEquals(65,location.getY());verify(npc,never()).teleport(any(Location.class));
    }
    @Test void onlyConfiguredNpcGetsHologramAndFiveNormalClicksThenThrottledSmokeReplies() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class); Messages messages=mock(Messages.class);
        HologramService holograms=mock(HologramService.class); Entity npc=mock(Entity.class), other=mock(Entity.class);
        World world=mock(World.class); Player player=mock(Player.class); PlayerInteractEntityEvent event=mock(PlayerInteractEntityEvent.class);
        AtomicLong now=new AtomicLong(1_000); UUID playerId=UUID.randomUUID(); Location npcLocation=new Location(world,6.5,65,4.5);
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.holograms()).thenReturn(holograms);
        when(settings.serverName()).thenReturn("My Server"); when(settings.mascot()).thenReturn(config()); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(messages.lines("mascot.replies")).thenReturn(List.of("Mascot: Welcome!"));
        when(messages.lines("mascot.over-limit-replies")).thenReturn(List.of("Mascot: Please slow down."));
        when(npc.isValid()).thenReturn(true); when(npc.getWorld()).thenReturn(world); when(npc.getLocation()).thenReturn(npcLocation);
        when(player.getUniqueId()).thenReturn(playerId); when(event.getPlayer()).thenReturn(player); when(event.getRightClicked()).thenReturn(npc);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        MascotService.CitizensLookup lookup=new MascotService.CitizensLookup() {
            public int id(Entity entity) { return entity==npc?3:99; }
            public Entity entity(int id) { return id==3?npc:null; }
        };
        MascotService service=new MascotService(plugin,lookup,now::get); service.reconcile();
        var component=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(holograms).show(eq("mascot"),argThat(at -> Math.abs(at.getY()-67.25)<0.001),component.capture());
        assertTrue(contains(component.getValue(),"Mascot"));
        assertTrue(contains(component.getValue(),"Hello"));

        for(int i=0;i<5;i++) service.interact(event);
        verify(player,times(5)).sendMessage(any(Component.class));
        verify(world,times(5)).spawnParticle(eq(Particle.END_ROD),any(Location.class),eq(8),anyDouble(),anyDouble(),anyDouble(),anyDouble());
        service.interact(event); service.interact(event);
        verify(player,times(6)).sendMessage(any(Component.class));
        verify(world,times(2)).spawnParticle(eq(Particle.SMOKE),any(Location.class),eq(18),anyDouble(),anyDouble(),anyDouble(),anyDouble());
        now.addAndGet(2_000); service.interact(event); verify(player,times(7)).sendMessage(any(Component.class));
        now.set(16_001); service.interact(event); verify(player,times(8)).sendMessage(any(Component.class));

        when(event.getRightClicked()).thenReturn(other); service.interact(event); verify(player,times(8)).sendMessage(any(Component.class));
        when(event.getRightClicked()).thenReturn(npc); when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        service.interact(event); verify(player,times(8)).sendMessage(any(Component.class));
    }

    @Test void missingNpcOnlyRemovesItsHologramAndNeverCreatesReplacement() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class); HologramService holograms=mock(HologramService.class);
        when(plugin.settings()).thenReturn(settings); when(settings.mascot()).thenReturn(config()); when(plugin.holograms()).thenReturn(holograms);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        MascotService.CitizensLookup missing=new MascotService.CitizensLookup(){ public int id(Entity entity){return -1;} public Entity entity(int id){return null;} };
        new MascotService(plugin,missing,()->0).reconcile();
        verify(holograms).remove("mascot"); verify(holograms,never()).show(anyString(),any(),any());
    }

    private static Settings.Mascot config() {
        return new Settings.Mascot(true,3,true,List.of("Mascot", "Hello"),2.25f,15,5,2,"minecraft:end_rod",8,"minecraft:smoke",18);
    }
    private static boolean contains(Component component,String text) {
        if(component instanceof TextComponent value && value.content().contains(text)) return true;
        return component.children().stream().anyMatch(child -> contains(child,text));
    }
}
