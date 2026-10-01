package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.config.Settings;
import land.momo.nekocore.data.WeeklyCoinRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeeklyCoinLeaderboardServiceTest {
    @Test void missingCoordinatesWarnOnceAndNeverSpawnsAtZero() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class);
        WeeklyCoinRepository repository=mock(WeeklyCoinRepository.class); HologramService holograms=mock(HologramService.class);
        when(plugin.settings()).thenReturn(settings); when(plugin.holograms()).thenReturn(holograms); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(settings.weeklyLeaderboard()).thenReturn(new Settings.WeeklyLeaderboard(true,false,"lobby",0,0,0,0,30));
        try(var bukkit=mockStatic(Bukkit.class)) {
            var service=new WeeklyCoinLeaderboardService(plugin,repository); service.restart(); service.restart();
            verify(repository,never()).current(); verify(holograms,never()).show(anyString(),any(),any());
            bukkit.verify(() -> Bukkit.getWorld(anyString()),never());
        }
    }

    @Test void configuredBoardRendersRankStylesNamesAndCommaSeparatedEarnedCoins() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class); Messages messages=mock(Messages.class);
        WeeklyCoinRepository repository=mock(WeeklyCoinRepository.class); HologramService holograms=mock(HologramService.class); World world=mock(World.class);
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.holograms()).thenReturn(holograms);
        when(settings.weeklyLeaderboard()).thenReturn(new Settings.WeeklyLeaderboard(true,true,"lobby",1.5,66,2.5,45,30));
        when(messages.raw("weekly-coins.title")).thenReturn("金币周榜 · 前10"); when(messages.raw("weekly-coins.first")).thenReturn("1. {player} · {coins}");
        when(messages.raw("weekly-coins.second")).thenReturn("2. {player} · {coins}");
        var board=new WeeklyCoinRepository.Board("2026-09-28",List.of(
                new WeeklyCoinRepository.Entry(UUID.randomUUID(),"Player_A",12580),new WeeklyCoinRepository.Entry(UUID.randomUUID(),"Player_B",9340)));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(world);
            new WeeklyCoinLeaderboardService(plugin,repository).render(board);
        }
        var location=org.mockito.ArgumentCaptor.forClass(Location.class); var text=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(holograms).show(eq("weekly-coins"),location.capture(),text.capture());
        assertEquals(1.5,location.getValue().getX()); assertEquals(66,location.getValue().getY()); assertEquals(45,location.getValue().getYaw());
        String plain=PlainTextComponentSerializer.plainText().serialize(text.getValue());
        assertTrue(plain.contains("Player_A · 12,580")); assertTrue(plain.contains("Player_B · 9,340"));
    }
}
