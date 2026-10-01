package land.momo.nekocore.command;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.SqliteStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoreCommandsTest {
    NekoCorePlugin plugin;
    SqliteStore store;
    Messages messages;
    CommandSender sender;
    CoreCommands commands;
    Command command;

    @BeforeEach void setup() {
        plugin = mock(NekoCorePlugin.class); store = mock(SqliteStore.class); messages = mock(Messages.class);
        sender = mock(CommandSender.class); command = mock(Command.class);
        when(command.getName()).thenReturn("nekocore");
        when(plugin.store()).thenReturn(store); when(plugin.messages()).thenReturn(messages);
        when(plugin.permission(any(), anyString())).thenReturn(true); when(plugin.available(any())).thenReturn(true);
        when(store.coins(anyString(), anyString(), anyLong())).thenReturn(new CompletableFuture<>());
        commands = new CoreCommands(plugin);
    }

    @Test void adminPermissionDeniesMutation() {
        when(plugin.permission(sender, "nekocore.admin")).thenReturn(false);
        commands.onCommand(sender, command, "nekocore", new String[]{"coins", "add", "Momo", "5"});
        verifyNoInteractions(store);
    }

    @Test void invalidAmountsAndInvalidExpTakeAreRejectedBeforeStorage() {
        for (String value : new String[]{"-1", "1.2", "NaN", "+5", "9223372036854775808", "1;op"})
            commands.onCommand(sender, command, "nekocore", new String[]{"coins", "set", "Momo", value});
        commands.onCommand(sender, command, "nekocore", new String[]{"exp", "take", "Momo", "5"});
        verifyNoInteractions(store);
        verify(messages, times(6)).send(sender, "invalid-amount");
        verify(messages).send(sender, "usage.admin");
    }

    @Test void acceptsOfflineNameWithoutNetworkLookupAndHandlesLongMaximum() {
        commands.onCommand(sender, command, "nekocore", new String[]{"coins", "set", "OfflineMomo", "9223372036854775807"});
        verify(store).coins("OfflineMomo", "set", Long.MAX_VALUE);
    }

    @Test void homeCommandIsPlayerOnlyAndHasItsOwnPermission() {
        when(command.getName()).thenReturn("home");
        commands.onCommand(sender, command, "home", new String[0]);
        verify(plugin).permission(sender, "nekocore.home.use");
        verify(messages).send(sender, "players-only"); verifyNoInteractions(store);
    }

    @Test void playerCommandsCannotRunWhileDataLoads() {
        Player player = mock(Player.class);
        when(command.getName()).thenReturn("sethome"); when(plugin.available(player)).thenReturn(false);
        commands.onCommand(player, command, "sethome", new String[0]);
        verifyNoInteractions(store); verify(player, never()).getLocation();
    }
    @Test void publicStoreAndBagAndNamespacedRequestCommandsDispatchWithTheirOwnPermissions() {
        var player=mock(Player.class); var shop=mock(land.momo.nekocore.gui.StoreMenu.class);
        var bag=mock(land.momo.nekocore.gui.BagMenu.class); var tpn=mock(land.momo.nekocore.service.TeleportRequestService.class);
        when(plugin.storeMenu()).thenReturn(shop); when(plugin.bagMenu()).thenReturn(bag); when(plugin.teleportRequests()).thenReturn(tpn);
        for(String name:new String[]{"store","bag","yes","no"}) {
            when(command.getName()).thenReturn(name); commands.onCommand(player,command,"nekocore:"+name,new String[0]);
        }
        verify(shop).open(player); verify(bag).open(player); verify(tpn).answer(player,true); verify(tpn).answer(player,false);
        verify(plugin).permission(player,"nekocore.store"); verify(plugin).permission(player,"nekocore.bag");
        when(command.getName()).thenReturn("tpn"); commands.onCommand(player,command,"nekocore:tpn",new String[]{"Momo"});
        verify(tpn).request(player,"Momo");
    }
    @Test void internalTitleOpeningRequiresAdminAndOnlyResolvesAnOnlineTarget() {
        var player=mock(Player.class); var titles=mock(land.momo.nekocore.gui.TitleMenu.class); when(plugin.titleMenu()).thenReturn(titles);
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Momo")).thenReturn(player);
            when(plugin.permission(sender,"nekocore.admin")).thenReturn(false);
            commands.onCommand(sender,command,"nekocore",new String[]{"levelshop","open","Momo"}); verifyNoInteractions(titles);
            when(plugin.permission(sender,"nekocore.admin")).thenReturn(true);
            commands.onCommand(sender,command,"nekocore",new String[]{"levelshop","open","Momo"}); verify(titles).open(player);
        }
    }
}
