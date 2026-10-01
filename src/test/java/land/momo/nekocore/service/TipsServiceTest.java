package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TipsServiceTest {
    @Test void broadcastsOneTipPerFiveMinutesLoopsAndReloadCancelsPreviousTask() {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class); var features = mock(Settings.Features.class);
        when(plugin.settings()).thenReturn(settings); when(settings.features()).thenReturn(features);
        when(features.tips()).thenReturn(new Settings.Tips(true, 300, "&#9FD9F6tips >> ", List.of("第一条", "第二条")));
        BukkitScheduler scheduler = mock(BukkitScheduler.class); BukkitTask first = mock(BukkitTask.class), second = mock(BukkitTask.class);
        List<Runnable> ticks = new ArrayList<>(); List<String> broadcasts = new ArrayList<>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(6000L), eq(6000L))).thenAnswer(call -> {
            ticks.add(call.getArgument(1)); return ticks.size() == 1 ? first : second;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(mock(Player.class)));
            bukkit.when(() -> Bukkit.broadcast(any(Component.class))).thenAnswer(call -> {
                broadcasts.add(PlainTextComponentSerializer.plainText().serialize(call.getArgument(0))); return 1;
            });
            TipsService tips = new TipsService(plugin); tips.restart();
            assertTrue(broadcasts.isEmpty());
            ticks.getFirst().run(); ticks.getFirst().run(); ticks.getFirst().run();
            assertEquals(List.of("tips >> 第一条", "tips >> 第二条", "tips >> 第一条"), broadcasts);
            tips.restart(); verify(first).cancel(); ticks.get(1).run(); assertEquals("tips >> 第一条", broadcasts.getLast());
            tips.stop(); tips.stop(); verify(second, times(1)).cancel();
        }
    }
}
