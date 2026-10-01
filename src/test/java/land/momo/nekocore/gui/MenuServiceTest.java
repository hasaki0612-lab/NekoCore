package land.momo.nekocore.gui;

import land.momo.nekocore.NekoCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuServiceTest {
    @Test void autoLayoutCentersOneToEightVisibleButtonsWithoutCollisions() {
        for (int count = 1; count <= 8; count++) {
            var slots = MenuService.centeredSlots(45, count);
            assertEquals(count, slots.size());
            assertEquals(count, new HashSet<>(slots).size());
            assertTrue(slots.stream().allMatch(slot -> slot >= 0 && slot < 45));
            assertTrue(slots.stream().allMatch(slot -> slot % 9 != 0 && slot % 9 != 8));
        }
        assertEquals(java.util.List.of(22), MenuService.centeredSlots(45, 1));
        assertEquals(java.util.List.of(19, 21, 23, 25), MenuService.centeredSlots(45, 4));
    }

    @Test void cancelsEveryTransferModeIncludingBottomInventory() throws Exception {
        NekoCorePlugin plugin = mock(NekoCorePlugin.class);
        MenuService menus = new MenuService(plugin);
        UUID owner = UUID.randomUUID();
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(owner);
        Inventory top = mock(Inventory.class); when(top.getSize()).thenReturn(27);
        MenuService.MenuHolder holder = new MenuService.MenuHolder(owner);
        var inventoryField = MenuService.MenuHolder.class.getDeclaredField("inventory");
        inventoryField.setAccessible(true); inventoryField.set(holder, top);
        when(top.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top);
        // Cancellation is unconditional, independently of click type / shift / hotbar / offhand.
        for (int slot : new int[]{-999, 0, 10, 26, 27, 45}) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getView()).thenReturn(view); when(event.getWhoClicked()).thenReturn(player);
            when(event.getRawSlot()).thenReturn(slot);
            menus.click(event); verify(event).setCancelled(true);
        }
        InventoryDragEvent drag = mock(InventoryDragEvent.class); when(drag.getView()).thenReturn(view);
        menus.drag(drag); verify(drag).setCancelled(true);
        verifyNoInteractions(plugin);
    }

    @Test void leavesOtherPluginInventoriesAlone() {
        Inventory top = mock(Inventory.class); when(top.getHolder()).thenReturn(mock(InventoryHolder.class));
        InventoryView view = mock(InventoryView.class); when(view.getTopInventory()).thenReturn(top);
        InventoryClickEvent event = mock(InventoryClickEvent.class); when(event.getView()).thenReturn(view);
        new MenuService(mock(NekoCorePlugin.class)).click(event);
        verify(event, never()).setCancelled(anyBoolean());
    }
}
