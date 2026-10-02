package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DestinationServiceTest {
    @Test void bothSurvivalEntrancesUsePublicSpawnCommandsWithoutInventoriesDependency() {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class); var features = mock(Settings.Features.class);
        when(plugin.settings()).thenReturn(settings); when(settings.features()).thenReturn(features);
        when(features.survivalEnabled()).thenReturn(true); when(features.secondWorldEnabled()).thenReturn(true);
        when(settings.survivalWorld()).thenReturn("world"); when(settings.survivalCommand()).thenReturn("mvtp {player} {world}");
        when(features.newWorld()).thenReturn("world_new"); when(features.newCommand()).thenReturn("mvtp {player} {world}");
        Player player = mock(Player.class); when(player.getName()).thenReturn("Momo");
        PluginManager manager = mock(PluginManager.class); when(manager.isPluginEnabled("Multiverse-Core")).thenReturn(true);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager); bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(mock(World.class));
            bukkit.when(() -> Bukkit.dispatchCommand(eq(console), anyString())).thenReturn(true);
            var service = new DestinationService(plugin); service.survival(player, false); service.survival(player, true);
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "mvtp Momo world"));
            bukkit.verify(() -> Bukkit.dispatchCommand(console, "mvtp Momo world_new"));
            verify(manager, never()).isPluginEnabled("Multiverse-Inventories");
        }
    }
    @Test void minigamesLoadsChunkThenTeleportsToExactLobbyCoordinates() {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class); var features = mock(Settings.Features.class);
        when(plugin.settings()).thenReturn(settings); when(settings.features()).thenReturn(features);
        when(features.minigamesEnabled()).thenReturn(true);
        when(features.minigames()).thenReturn(new Settings.Destination("lobby", 4.5, 70, 8.5, 0, 0));
        when(plugin.messages()).thenReturn(mock(Messages.class)); when(plugin.permission(any(), anyString())).thenReturn(true);
        Player player = mock(Player.class); UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        World from = mock(World.class), lobby = mock(World.class); when(player.getWorld()).thenReturn(from);
        when(lobby.getMinHeight()).thenReturn(-64); when(lobby.getMaxHeight()).thenReturn(320);
        WorldBorder border = mock(WorldBorder.class); when(lobby.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        CompletableFuture<Chunk> chunk = new CompletableFuture<>(); when(lobby.getChunkAtAsync(any(Location.class))).thenReturn(chunk);
        List<Runnable> callbacks = new ArrayList<>(); doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        when(player.teleport(any(Location.class), eq(TeleportCause.PLUGIN))).thenAnswer(call -> {
            Location at = call.getArgument(0); assertSame(lobby, at.getWorld());
            assertEquals(4.5, at.getX()); assertEquals(70, at.getY()); assertEquals(8.5, at.getZ()); return true;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(lobby); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            new DestinationService(plugin).minigames(player);
            verify(player, never()).teleport(any(Location.class), any(TeleportCause.class));
            chunk.complete(mock(Chunk.class)); callbacks.getFirst().run();
            verify(player).teleport(any(Location.class), eq(TeleportCause.PLUGIN));
            verify(plugin.messages()).send(player, "destination-arrived");
        }
    }
    @Test void afkPoolLoadsChunkAndTeleportsFacingNorth() {
        var plugin=mock(NekoCorePlugin.class); var settings=mock(Settings.class);
        when(plugin.settings()).thenReturn(settings);
        var pool=new Settings.AfkPool(true,new Settings.Destination("lobby",6.5,71,9.5,180,0),3,
                new Settings.AfkReward(300,10,1.10,0.45,1,4),new Settings.AfkTitle(true,true,40,10),true);
        when(settings.afkPool()).thenReturn(pool); when(plugin.messages()).thenReturn(mock(Messages.class));
        when(plugin.permission(any(),anyString())).thenReturn(true);
        Player player=mock(Player.class); UUID id=UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        World from=mock(World.class), lobby=mock(World.class); when(player.getWorld()).thenReturn(from);
        when(lobby.getMinHeight()).thenReturn(-64); when(lobby.getMaxHeight()).thenReturn(320);
        WorldBorder border=mock(WorldBorder.class); when(lobby.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(lobby.getChunkAtAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(mock(Chunk.class)));
        List<Runnable> callbacks=new ArrayList<>(); doAnswer(call->{callbacks.add(call.getArgument(0));return null;}).when(plugin).onMain(any());
        when(player.teleport(any(Location.class),eq(TeleportCause.PLUGIN))).thenAnswer(call->{
            Location at=call.getArgument(0); assertEquals(6.5,at.getX()); assertEquals(71,at.getY()); assertEquals(9.5,at.getZ());
            assertEquals(180,at.getYaw()); assertEquals(0,at.getPitch()); return true;
        });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("lobby")).thenReturn(lobby); bukkit.when(()->Bukkit.getPlayer(id)).thenReturn(player);
            new DestinationService(plugin).afkPool(player); callbacks.getFirst().run();
            verify(player).teleport(any(Location.class),eq(TeleportCause.PLUGIN)); verify(plugin.messages()).send(player,"afk-pool-arrived");
        }
    }
}
