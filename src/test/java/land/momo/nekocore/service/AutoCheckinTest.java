package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.gui.MenuService;
import land.momo.nekocore.model.Profile;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AutoCheckinTest {
    @Test void alreadyClaimedOrDisabledOrPermissionDeniedLoginIsSilent() throws Exception {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class,RETURNS_DEEP_STUBS); var store = mock(SqliteStore.class);
        var messages = mock(Messages.class); var player = mock(Player.class); UUID id=UUID.randomUUID();
        when(plugin.settings()).thenReturn(settings); when(plugin.store()).thenReturn(store); when(plugin.messages()).thenReturn(messages);
        when(player.getUniqueId()).thenReturn(id); when(player.hasPermission("nekocore.checkin")).thenReturn(true);
        var checkin = new Settings.Checkin(true,ZoneId.of("Asia/Shanghai"),100,50,"minecraft:block.amethyst_block.chime",.6f,1.4f,16);
        when(settings.features().checkin()).thenReturn(checkin); when(store.cachedCheckin(id)).thenReturn(LocalDate.now(checkin.zone()).toString());
        var service = new CheckinService(plugin); service.claimAutomatic(player); service.claimAutomatic(player);
        when(store.cachedCheckin(id)).thenReturn(""); when(player.hasPermission("nekocore.checkin")).thenReturn(false); service.claimAutomatic(player);
        verifyNoInteractions(messages); verify(store,never()).checkin(any(),any(),anyLong(),anyLong());
    }
    @Test void automaticClaimUsesSameAtomicRewardAndOnlyShowsSuccessAfterCommit() throws Exception {
        var plugin = mock(NekoCorePlugin.class); var settings = mock(Settings.class,RETURNS_DEEP_STUBS); var store = mock(SqliteStore.class);
        var messages = mock(Messages.class); var player = mock(Player.class); var menus = mock(MenuService.class); UUID id=UUID.randomUUID();
        var queue = new ArrayDeque<Runnable>(); var future = new CompletableFuture<SqliteStore.DailyClaim>();
        var checkin = new Settings.Checkin(true,ZoneId.of("Asia/Shanghai"),100,50,"minecraft:block.amethyst_block.chime",.6f,1.4f,16);
        when(plugin.settings()).thenReturn(settings); when(settings.features().checkin()).thenReturn(checkin); when(plugin.store()).thenReturn(store);
        when(plugin.messages()).thenReturn(messages); when(plugin.menus()).thenReturn(menus); when(plugin.available(any())).thenReturn(true); when(plugin.permission(any(),anyString())).thenReturn(true);
        var field = NekoCorePlugin.class.getDeclaredField("messages"); field.setAccessible(true); field.set(plugin,messages);
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.hasPermission("nekocore.checkin")).thenReturn(true);
        when(player.getLocation()).thenAnswer(c -> new Location(mock(World.class),0,64,0)); when(store.cachedCheckin(id)).thenReturn("");
        when(store.checkin(id,checkin.zone(),100,50)).thenReturn(future); when(plugin.variables(any())).thenAnswer(c -> new HashMap<>(Map.of("level","2")));
        doAnswer(c -> { queue.add(c.getArgument(0)); return null; }).when(plugin).onMain(any()); doCallRealMethod().when(plugin).finish(any(),any(),any());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); var service = new CheckinService(plugin);
            service.claimAutomatic(player); service.claimAutomatic(player); verify(store).checkin(id,checkin.zone(),100,50); verifyNoInteractions(messages);
            future.complete(new SqliteStore.DailyClaim(new Profile(id,"Momo",100,2,100,0,1,1,false),true,"2026-09-29",1));
            while(!queue.isEmpty()) queue.remove().run();
            verify(messages).send(eq(player),eq("checkin-auto-success"),argThat(vars -> "100".equals(vars.get("reward_coins")) && "50".equals(vars.get("reward_exp"))));
            verify(player).playSound(any(Location.class),eq(checkin.sound()),eq(SoundCategory.MASTER),anyFloat(),anyFloat());
            verify(player).spawnParticle(eq(Particle.END_ROD),any(Location.class),eq(16),anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }
}
