package land.momo.nekocore.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FeatureGuiTest {
    InventoryClickEvent click(UiHarness h, ClickType type, int raw) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(h.view); when(event.getWhoClicked()).thenReturn(h.player);
        when(event.getClick()).thenReturn(type); when(event.getRawSlot()).thenReturn(raw); return event;
    }
    @Test void navigationAlwaysUsesLowerRightArrowAndClicksAreDeferredAndDeduplicated() throws Exception {
        try(var h = new UiHarness()) {
            AtomicInteger actions = new AtomicInteger();
            var screen = h.ui.screen(h.player,36,"title",Map.of(),"store",actions::incrementAndGet); h.ui.open(h.player,screen);
            assertTrue(screen.actions.containsKey(35)); assertFalse(screen.actions.containsKey(27));
            h.rendering.verify(() -> FeatureGui.icon(eq(Material.ARROW),eq("ui-back"),anyList(),anyMap()));
            var event = click(h,ClickType.LEFT,35); h.ui.click(event); h.ui.click(event);
            verify(event,times(2)).setCancelled(true); assertEquals(0,actions.get()); h.pump(); assertEquals(1,actions.get());
        }
    }
    @Test void numbersOffhandDropDoubleCreativeAndDragCannotMoveIcons() throws Exception {
        try(var h = new UiHarness()) {
            var screen = h.ui.screen(h.player,27,"title",Map.of(),"bag",null); h.ui.open(h.player,screen);
            AtomicInteger actions = new AtomicInteger(); screen.action(0,c -> actions.incrementAndGet());
            for(ClickType type : List.of(ClickType.NUMBER_KEY,ClickType.SWAP_OFFHAND,ClickType.DOUBLE_CLICK,ClickType.DROP,ClickType.CONTROL_DROP,ClickType.MIDDLE,ClickType.CREATIVE)) {
                var event = click(h,type,0); h.ui.click(event); verify(event).setCancelled(true);
            }
            var drag = mock(InventoryDragEvent.class); when(drag.getView()).thenReturn(h.view); h.ui.drag(drag); verify(drag).setCancelled(true);
            h.pump(); assertEquals(0,actions.get());
        }
    }
    @Test void closedOrReloadedScreensCannotRunQueuedActions() throws Exception {
        try(var h = new UiHarness()) {
            var screen = h.ui.screen(h.player,27,"title",Map.of(),"store",null); h.ui.open(h.player,screen);
            AtomicInteger actions = new AtomicInteger(); screen.action(0,c -> actions.incrementAndGet());
            h.ui.click(click(h,ClickType.LEFT,0)); h.ui.closeAll(); h.pump(); assertEquals(0,actions.get());
        }
    }
    @Test void bottomShiftClickIsCancelledBeforeExplicitBagTransferAndForeignViewerIsRejected() throws Exception {
        try(var h = new UiHarness()) {
            var screen = h.ui.screen(h.player,27,"title",Map.of(),"bag",null); h.ui.open(h.player,screen);
            AtomicInteger actions = new AtomicInteger(); screen.bottom = (slot,type) -> { assertEquals(2,slot); actions.incrementAndGet(); };
            var event = click(h,ClickType.SHIFT_LEFT,29); when(event.getClickedInventory()).thenReturn(h.inventory); when(event.getSlot()).thenReturn(2);
            h.ui.click(event); verify(event).setCancelled(true); h.pump(); assertEquals(1,actions.get());
            Player foreign = mock(Player.class); when(foreign.isOnline()).thenReturn(true); when(foreign.getUniqueId()).thenReturn(UUID.randomUUID());
            when(event.getWhoClicked()).thenReturn(foreign); h.ui.click(event); h.pump(); assertEquals(1,actions.get());
        }
    }
}
