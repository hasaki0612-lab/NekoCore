package land.momo.nekocore.gui;

import land.momo.nekocore.*;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.data.*;
import land.momo.nekocore.integration.*;
import land.momo.nekocore.model.Profile;
import land.momo.nekocore.service.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises real GUI routing; mocks Paper's registry/rendered items and player inventory boundary. */
final class UiHarness implements AutoCloseable {
    final NekoCorePlugin plugin = mock(NekoCorePlugin.class);
    final Player player = mock(Player.class);
    final PlayerInventory inventory = mock(PlayerInventory.class);
    final InventoryView view = mock(InventoryView.class);
    final World world = mock(World.class);
    final Messages messages = mock(Messages.class);
    final CommerceRepository commerce = mock(CommerceRepository.class);
    final TitleRepository titles = mock(TitleRepository.class);
    final InventoryExchangeService exchanges = mock(InventoryExchangeService.class);
    final PlayerDataService data = mock(PlayerDataService.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final UUID id = UUID.randomUUID();
    final Queue<Runnable> main = new ArrayDeque<>();
    final List<Runnable> timers = new ArrayList<>();
    final Map<Inventory,ItemStack[]> contents = new IdentityHashMap<>();
    final FakeItems items = new FakeItems();
    final FeatureGui ui;
    final MockedStatic<Bukkit> bukkit;
    final MockedStatic<FeatureGui> rendering;
    Inventory top = mock(Inventory.class);
    UiHarness() throws Exception {
        var yaml = TestDefaults.yaml();
        yaml.set("levelshop.worlds", List.of("lobby"));
        yaml.set("bag.writable-worlds", List.of("world", "world_new"));
        var features = TestDefaults.features(yaml); when(plugin.features()).thenReturn(features);
        when(plugin.available(any())).thenReturn(true); when(plugin.featuresAvailable(any())).thenReturn(true);
        when(plugin.permission(any(),anyString())).thenReturn(true); when(player.hasPermission(anyString())).thenReturn(true);
        when(plugin.messages()).thenReturn(messages); when(messages.raw(anyString())).thenAnswer(c -> c.getArgument(0)); when(messages.lines(anyString())).thenReturn(List.of());
        var messagesField = NekoCorePlugin.class.getDeclaredField("messages"); messagesField.setAccessible(true); messagesField.set(plugin,messages);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.commerce()).thenReturn(commerce); when(plugin.titles()).thenReturn(titles); when(plugin.exchanges()).thenReturn(exchanges);
        when(plugin.data()).thenReturn(data); when(exchanges.idle(player)).thenReturn(true);
        when(plugin.nameTags()).thenReturn(mock(NameTagService.class));
        when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.getName()).thenReturn("Momo");
        when(player.getWorld()).thenReturn(world); when(world.getName()).thenReturn("lobby"); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory); when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(data.view(id)).thenReturn(new Profile(id,"Momo",10000,1,0,0,1,1,false));
        when(titles.cached(id)).thenReturn(new TitleRepository.Titles(Set.of(),""));
        when(player.getOpenInventory()).thenReturn(view); when(view.getTopInventory()).thenAnswer(ignored -> top);
        when(player.openInventory(any(Inventory.class))).thenAnswer(c -> { top = c.getArgument(0); return view; });
        doAnswer(c -> { top = mock(Inventory.class); return null; }).when(player).closeInventory();
        doAnswer(c -> { main.add(c.getArgument(0)); return null; }).when(plugin).onMain(any());
        doCallRealMethod().when(plugin).finish(any(),any(),any());
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(c -> { main.add(c.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(c -> { timers.add(c.getArgument(1)); return mock(BukkitTask.class); });
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class),anyInt(),any(Component.class))).thenAnswer(c -> {
            Inventory result = mock(Inventory.class); int size = c.getArgument(1); InventoryHolder holder = c.getArgument(0);
            when(result.getSize()).thenReturn(size); when(result.getHolder()).thenReturn(holder); contents.put(result,new ItemStack[size]);
            doAnswer(a -> { contents.get(result)[a.getArgument(0)] = a.getArgument(1); return null; }).when(result).setItem(anyInt(),any());
            return result;
        });
        rendering = mockStatic(FeatureGui.class,CALLS_REAL_METHODS);
        rendering.when(() -> FeatureGui.icon(any(),anyString(),anyList(),anyMap())).thenAnswer(c -> items.item("icon:"+c.getArgument(1),1,64));
        rendering.when(() -> FeatureGui.decorate(any(),anyString(),anyList(),anyMap())).thenAnswer(c -> ((ItemStack)c.getArgument(0)).clone());
        ui = new FeatureGui(plugin); when(plugin.featureGui()).thenReturn(ui);
    }
    FeatureGui.Screen screen() { return (FeatureGui.Screen) top.getHolder(); }
    void pump() { int n=0; while(!main.isEmpty()) { if(++n>100) throw new AssertionError("callback cycle"); main.remove().run(); } }
    @Override public void close() { rendering.close(); bukkit.close(); }
}
