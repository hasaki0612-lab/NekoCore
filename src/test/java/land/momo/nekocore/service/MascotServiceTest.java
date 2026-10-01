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
    @Test void onlyConfiguredNpcGetsHologramAndFiveNormalClicksThenThrottledSmokeReplies() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class); Messages messages=mock(Messages.class);
        HologramService holograms=mock(HologramService.class); Entity npc=mock(Entity.class), other=mock(Entity.class);
        World world=mock(World.class); Player player=mock(Player.class); PlayerInteractEntityEvent event=mock(PlayerInteractEntityEvent.class);
        AtomicLong now=new AtomicLong(1_000); UUID playerId=UUID.randomUUID(); Location npcLocation=new Location(world,6.5,65,4.5);
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.holograms()).thenReturn(holograms);
        when(settings.mascot()).thenReturn(config()); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(messages.lines("mascot.replies")).thenReturn(List.of("Mascot: Welcome!"));
        when(messages.lines("mascot.over-limit-replies")).thenReturn(List.of("Mascot: Please slow down."));
        when(npc.isValid()).thenReturn(true); when(npc.getWorld()).thenReturn(world); when(npc.getLocation()).thenReturn(npcLocation);
        when(player.getUniqueId()).thenReturn(playerId); when(event.getPlayer()).thenReturn(player); when(event.getRightClicked()).thenReturn(npc);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        MascotService.CitizensLookup lookup=new MascotService.CitizensLookup() {
            public int id(Entity entity) { return entity==npc?12:99; }
            public Entity entity(int id) { return id==12?npc:null; }
        };
        MascotService service=new MascotService(plugin,lookup,now::get); service.reconcile();
        var component=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(holograms).show(eq("mascot"),argThat(at -> Math.abs(at.getY()-67.85)<0.001),component.capture());
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
        return new Settings.Mascot(true,12,true,List.of("Mascot", "Hello"),2.85f,15,5,2,"minecraft:end_rod",8,"minecraft:smoke",18);
    }
    private static boolean contains(Component component,String text) {
        if(component instanceof TextComponent value && value.content().contains(text)) return true;
        return component.children().stream().anyMatch(child -> contains(child,text));
    }
}
