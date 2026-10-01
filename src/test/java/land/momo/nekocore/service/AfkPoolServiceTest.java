package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.*;
import land.momo.nekocore.data.SqliteStore;
import land.momo.nekocore.integration.PrefixService;
import land.momo.nekocore.model.Profile;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfkPoolServiceTest {
    NekoCorePlugin plugin; Settings settings; PrefixService prefixes; SqliteStore store;
    Player player; World lobby; UUID id; AtomicLong clock; MockedStatic<Bukkit> bukkit;

    @BeforeEach void setup() {
        plugin=mock(NekoCorePlugin.class); settings=mock(Settings.class); prefixes=mock(PrefixService.class); store=mock(SqliteStore.class);
        var messages=mock(Messages.class); when(plugin.settings()).thenReturn(settings); when(plugin.prefixes()).thenReturn(prefixes);
        when(plugin.store()).thenReturn(store); when(plugin.messages()).thenReturn(messages); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(messages.raw(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "afk-pool.title" -> "你正在挂机～"; case "afk-pool.subtitle" -> "下一份奖励 · {time}";
            case "afk-pool.reward-exp" -> "收到 {exp} EXP 啦～"; case "afk-pool.reward-exp-coins" -> "{exp} EXP · +{coins} 金币 ✦";
            default -> call.getArgument(0);
        });
        when(messages.lines("afk-pool.coin-messages")).thenReturn(List.of("捞到 {coins} 枚金币 · {balance}"));
        id=UUID.randomUUID(); player=mock(Player.class); lobby=mock(World.class); clock=new AtomicLong(1_000_000_000L);
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.isDead()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.getWorld()).thenReturn(lobby); when(lobby.getName()).thenReturn("lobby");
        when(player.isInWater()).thenReturn(true); when(prefixes.afkExpMultiplier(id)).thenReturn(1.10);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(plugin).onMain(any());
        when(store.afkReward(eq(id),anyLong(),anyLong())).thenAnswer(call -> {
            long exp=call.getArgument(1), coins=call.getArgument(2);
            return CompletableFuture.completedFuture(new SqliteStore.Reward(new Profile(id,"Momo",coins,1,exp,0,1,1,false),1,exp,coins));
        });
        bukkit=mockStatic(Bukkit.class); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
    }
    @AfterEach void close() { if (bukkit != null) bukkit.close(); }

    @Test void lobbyWaterStartsOnePrivateTitleAndDefaultRewardOnlyOnce() {
        when(settings.afkPool()).thenReturn(config(10,300,2,true));
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->0.40,(min,max)->4);
        Player other=mock(Player.class); when(other.getUniqueId()).thenReturn(UUID.randomUUID()); when(other.isOnline()).thenReturn(true);
        when(other.isDead()).thenReturn(false); when(other.getGameMode()).thenReturn(GameMode.SURVIVAL); when(other.getWorld()).thenReturn(lobby);
        when(other.isInWater()).thenReturn(false);
        service.tick(List.of(player,other)); assertTrue(service.active(id)); assertEquals(1,service.sessionCount());
        var title=org.mockito.ArgumentCaptor.forClass(Title.class); verify(player).showTitle(title.capture());
        assertEquals("你正在挂机～",PlainTextComponentSerializer.plainText().serialize(title.getValue().title()));
        assertEquals("下一份奖励 · 05:00",PlainTextComponentSerializer.plainText().serialize(title.getValue().subtitle()));
        verify(other,never()).showTitle(any(Title.class));
        clock.addAndGet(299_000_000_000L); service.tick(List.of(player,other)); verify(store,never()).afkReward(any(),anyLong(),anyLong());
        clock.addAndGet(1_000_000_000L); service.tick(List.of(player,other));
        verify(store,times(1)).afkReward(id,11,4);
        service.tick(List.of(player,other)); verify(store,times(1)).afkReward(any(),anyLong(),anyLong());
        verify(player,times(1)).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(other,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        verify(player,never()).clearTitle();
    }

    @Test void noCoinRollSendsNoChatMessage() {
        when(settings.afkPool()).thenReturn(config(10,1,2,true));
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->0.99,(min,max)->4);
        service.tick(List.of(player)); clock.addAndGet(1_000_000_000L); service.tick(List.of(player));
        verify(store).afkReward(id,11,0); verify(player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test void onlyLobbyWaterAndNonSpectatorCanEnterAndGraceDoesNotResetBriefExit() {
        when(settings.afkPool()).thenReturn(config(10,300,2,false));
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->1.0,(a,b)->a);
        when(player.isInWater()).thenReturn(false); service.tick(List.of(player)); assertFalse(service.active(id));
        when(player.isInWater()).thenReturn(true); when(lobby.getName()).thenReturn("world"); service.tick(List.of(player)); assertFalse(service.active(id));
        when(lobby.getName()).thenReturn("lobby"); when(player.getGameMode()).thenReturn(GameMode.SPECTATOR); service.tick(List.of(player)); assertFalse(service.active(id));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); service.tick(List.of(player)); assertTrue(service.active(id));
        when(player.isInWater()).thenReturn(false); clock.addAndGet(100_000_000L); service.tick(List.of(player));
        clock.addAndGet(1_900_000_000L); service.tick(List.of(player)); assertTrue(service.active(id));
        when(player.isInWater()).thenReturn(true); clock.addAndGet(50_000_000L); service.tick(List.of(player)); assertTrue(service.active(id));
        when(player.isInWater()).thenReturn(false); clock.addAndGet(100_000_000L); service.tick(List.of(player));
        clock.addAndGet(2_000_000_000L); service.tick(List.of(player)); assertFalse(service.active(id));
    }

    @Test void inventoryTemporarilyHidesTitleWithoutStoppingSessionThenRestores() {
        when(settings.afkPool()).thenReturn(config(10,300,2,true));
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->1.0,(a,b)->a); service.tick(List.of(player));
        clearInvocations(player);
        InventoryOpenEvent open=mock(InventoryOpenEvent.class); when(open.getPlayer()).thenReturn(player); service.inventoryOpen(open);
        clock.addAndGet(1_000_000_000L); service.tick(List.of(player)); verify(player,never()).showTitle(any(Title.class)); assertTrue(service.active(id));
        InventoryCloseEvent close=mock(InventoryCloseEvent.class); when(close.getPlayer()).thenReturn(player);
        BukkitScheduler scheduler=mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        BukkitTask task=mock(BukkitTask.class);
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call -> { ((Runnable)call.getArgument(1)).run(); return task; });
        service.inventoryClose(close); clock.addAndGet(1_000_000_000L); service.tick(List.of(player));
        verify(player).showTitle(any(Title.class)); assertTrue(service.active(id));
    }

    @Test void fractionalMultiplierAccumulatesInsteadOfFlooringEveryCycleAndStopClearsSessions() {
        when(settings.afkPool()).thenReturn(config(1,1,2,false));
        List<Long> awarded=new ArrayList<>();
        when(store.afkReward(eq(id),anyLong(),eq(0L))).thenAnswer(call -> {
            long exp=call.getArgument(1); awarded.add(exp);
            return CompletableFuture.completedFuture(new SqliteStore.Reward(new Profile(id,"Momo",0,1,exp,0,1,1,false),1,exp,0));
        });
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->1.0,(a,b)->a); service.tick(List.of(player));
        for(int i=0;i<10;i++){clock.addAndGet(1_000_000_000L);service.tick(List.of(player));}
        assertEquals(11,awarded.stream().mapToLong(Long::longValue).sum()); assertEquals(10,awarded.size());
        service.stop(); assertEquals(0,service.sessionCount()); verify(player,never()).clearTitle();
    }

    @Test void quitWorldChangeDeathAndOfflineImmediatelyDiscardSession() {
        when(settings.afkPool()).thenReturn(config(10,300,2,false));
        AfkPoolService service=new AfkPoolService(plugin,clock::get,()->1.0,(a,b)->a);

        service.tick(List.of(player)); assertTrue(service.active(id));
        PlayerQuitEvent quit=mock(PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(player);
        service.quit(quit); assertFalse(service.active(id));

        service.tick(List.of(player)); assertTrue(service.active(id));
        PlayerChangedWorldEvent world=mock(PlayerChangedWorldEvent.class); when(world.getPlayer()).thenReturn(player);
        service.world(world); assertFalse(service.active(id));

        service.tick(List.of(player)); assertTrue(service.active(id));
        PlayerDeathEvent death=mock(PlayerDeathEvent.class); when(death.getEntity()).thenReturn(player);
        service.death(death); assertFalse(service.active(id));

        service.tick(List.of(player)); assertTrue(service.active(id));
        service.tick(List.of()); assertFalse(service.active(id));
    }

    private Settings.AfkPool config(long base,int interval,int grace,boolean title) {
        return new Settings.AfkPool(true,new Settings.Destination("lobby",-10.5,54,33.5,180,0),grace,
                new Settings.AfkReward(interval,base,1.10,0.45,1,4),new Settings.AfkTitle(title,true,40,10));
    }
}
