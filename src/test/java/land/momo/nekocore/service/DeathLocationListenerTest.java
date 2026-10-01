package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeathLocationListenerTest {
    @Test void capturesRealWorldAndFloorsNegativeCoordinatesAndOnlyMessagesVictim() {
        var plugin = mock(NekoCorePlugin.class); var messages = mock(Messages.class);
        Player player = mock(Player.class); World world = mock(World.class); UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world_new");
        when(player.getLocation()).thenReturn(new Location(world, -0.2, 53.7, -21.1));
        when(plugin.messages()).thenReturn(messages);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class); when(event.getEntity()).thenReturn(player);
        List<Runnable> callbacks = new ArrayList<>(); doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
        new DeathLocationListener(plugin).onDeath(event);
        verifyNoInteractions(messages);
        when(player.getLocation()).thenReturn(new Location(world, 99, 99, 99));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            callbacks.getFirst().run();
        }
        verify(messages).send(player, "death-location", Map.of("world", "world_new", "x", "-1", "y", "53", "z", "-22"));
        verifyNoMoreInteractions(messages);
    }
}
