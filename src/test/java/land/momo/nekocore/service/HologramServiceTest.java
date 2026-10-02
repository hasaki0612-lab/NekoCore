package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HologramServiceTest {
    @Test void reloadRemovesOnlyMarkedDisplaysAndRepeatedShowReusesOneTextDisplay() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class);
        World world=mock(World.class); TextDisplay stale=mock(TextDisplay.class), foreign=mock(TextDisplay.class), display=mock(TextDisplay.class);
        PersistentDataContainer staleData=mock(PersistentDataContainer.class), foreignData=mock(PersistentDataContainer.class), data=mock(PersistentDataContainer.class);
        UUID uuid=UUID.randomUUID(); AtomicReference<String> marker=new AtomicReference<>(); Location location=new Location(world,4.5,67,-2.5,20,0);
        when(plugin.namespace()).thenReturn("nekocore"); when(plugin.settings()).thenReturn(settings);
        when(settings.holograms()).thenReturn(new Settings.Holograms(240,true));
        when(stale.getPersistentDataContainer()).thenReturn(staleData); when(foreign.getPersistentDataContainer()).thenReturn(foreignData);
        when(staleData.has(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenReturn(true);
        when(foreignData.has(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenReturn(false);
        when(world.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of(stale,foreign));
        when(display.getUniqueId()).thenReturn(uuid); when(display.getWorld()).thenReturn(world); when(display.isValid()).thenReturn(true);
        when(display.getLocation()).thenReturn(location); when(display.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenAnswer(ignored -> marker.get());
        doAnswer(call -> { marker.set(call.getArgument(2)); return null; }).when(data)
                .set(any(NamespacedKey.class),eq(PersistentDataType.STRING),anyString());
        when(world.spawn(same(location),eq(TextDisplay.class),org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any()))
                .thenAnswer(call -> { Consumer<TextDisplay> initializer=call.getArgument(2); initializer.accept(display); return display; });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world)); bukkit.when(() -> Bukkit.getEntity(uuid)).thenReturn(display);
            HologramService service=new HologramService(plugin); service.start();
            verify(stale).remove(); verify(foreign,never()).remove();
            assertSame(display,service.show("weekly-coins",location,Component.text("one")));
            assertSame(display,service.show("weekly-coins",location,Component.text("two")));
            verify(display,atLeastOnce()).setBillboard(Display.Billboard.CENTER);
            Location turned=location.clone();turned.setYaw(90);
            assertSame(display,service.show("weekly-coins",turned,Component.text("three"),Display.Billboard.FIXED));
            verify(display).teleport(turned);verify(display).setBillboard(Display.Billboard.FIXED);
            verify(world,times(1)).spawn(same(location),eq(TextDisplay.class),org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any());
            verify(display,times(3)).text(any(Component.class)); verify(display).setPersistent(false);
            service.stop(); verify(display).remove();
        }
    }
}
