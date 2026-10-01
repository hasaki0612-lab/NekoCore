package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.config.Settings;
import land.momo.nekocore.model.Profile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JoinWelcomeServiceTest {
    @Test void titleIsDelayedUntilLoadedAndSentOnlyToJoiningPlayerWithoutChatOutput() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class);
        Messages messages=mock(Messages.class); PlayerDataService data=mock(PlayerDataService.class);
        AfkPoolService afk=mock(AfkPoolService.class); Player player=mock(Player.class), other=mock(Player.class);
        BukkitScheduler scheduler=mock(BukkitScheduler.class); BukkitTask task=mock(BukkitTask.class);
        UUID id=UUID.randomUUID(); Runnable[] delayed={null};
        when(plugin.settings()).thenReturn(settings); when(plugin.messages()).thenReturn(messages); when(plugin.data()).thenReturn(data);
        when(plugin.afkPool()).thenReturn(afk); when(settings.welcomeTitle()).thenReturn(new Settings.WelcomeTitle(true,15,10,60,10));
        when(messages.raw("welcome-title.title")).thenReturn("欢迎！ {player}"); when(messages.raw("welcome-title.subtitle")).thenReturn("My Server");
        when(player.getUniqueId()).thenReturn(id); when(player.getName()).thenReturn("Neko_Izumi"); when(player.isOnline()).thenReturn(true);
        when(data.view(id)).thenReturn(new Profile(id,"Neko_Izumi",0,1,0,0,1,1,false));
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),eq(15L))).thenAnswer(call -> { delayed[0]=call.getArgument(1); return task; });
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            new JoinWelcomeService(plugin).show(player);
            verify(afk).suppressTitle(id,95); verify(player,never()).showTitle(any(Title.class));
            delayed[0].run();
        }
        var title=org.mockito.ArgumentCaptor.forClass(Title.class); verify(player).showTitle(title.capture());
        assertEquals("欢迎！ Neko_Izumi", PlainTextComponentSerializer.plainText().serialize(title.getValue().title()));
        assertEquals("My Server", PlainTextComponentSerializer.plainText().serialize(title.getValue().subtitle()));
        verify(other,never()).showTitle(any(Title.class)); verify(player,never()).sendMessage(any(Component.class));
    }

    @Test void disabledOrUnloadedPlayerNeverReceivesWelcomeTitle() {
        NekoCorePlugin plugin=mock(NekoCorePlugin.class); Settings settings=mock(Settings.class); Player player=mock(Player.class);
        when(plugin.settings()).thenReturn(settings); when(settings.welcomeTitle()).thenReturn(new Settings.WelcomeTitle(false,15,10,60,10));
        try(var bukkit=mockStatic(Bukkit.class)) {
            new JoinWelcomeService(plugin).show(player); bukkit.verify(Bukkit::getScheduler,never());
        }
        verify(player,never()).showTitle(any(Title.class));
    }
}
