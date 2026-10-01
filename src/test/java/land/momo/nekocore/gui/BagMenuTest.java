package land.momo.nekocore.gui;

import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.service.InventoryItems;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BagMenuTest {
    @Test void lobbyAndUnknownWorldAllowViewingButEveryTransferAndSortAttemptIsReadonly() throws Exception {
        for(String world : new String[]{"lobby","future_world"}) try(var h = new UiHarness(); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            when(h.world.getName()).thenReturn(world);
            ItemStack[] bag = new ItemStack[36]; bag[0] = h.items.item("stone",12,64);
            codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenReturn(bag);
            when(h.commerce.bag(h.id,18)).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Bag(18,0,new byte[]{1})));
            var menu = new BagMenu(h.plugin); menu.open(h.player); h.pump();
            assertEquals(27,h.top.getSize()); assertNotNull(h.contents.get(h.top)[0]);
            h.screen().actions.get(0).accept(ClickType.LEFT); h.screen().bottom.accept(0,ClickType.SHIFT_LEFT);
            h.screen().actions.get(21).accept(ClickType.LEFT); h.screen().rejected.run();
            verify(h.messages).send(h.player,"bag-readonly"); // throttled, not four messages
            verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any());
            verify(h.commerce,never()).prepareBag(any());
        }
    }
    @Test void bothSurvivalWorldsUseCommittedBagRevisionAndKeepControlsOutOfData() throws Exception {
        for(String world : new String[]{"world","world_new"}) try(var h = new UiHarness(); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            when(h.world.getName()).thenReturn(world);
            ItemStack[] bag = new ItemStack[36]; bag[0] = h.items.item("stone",12,64);
            codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenReturn(bag);
            codec.when(() -> InventoryItems.encode(any(ItemStack[].class))).thenAnswer(call -> {
                ItemStack[] items = call.getArgument(0); assertEquals(36,items.length); return new byte[]{2};
            });
            when(h.commerce.bag(h.id,18)).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Bag(27,7,new byte[]{1})));
            when(h.exchanges.execute(any(),any(),any(),any(),any(),any())).thenReturn(new CompletableFuture<>());
            var menu = new BagMenu(h.plugin); menu.open(h.player); h.pump(); assertEquals(36,h.top.getSize());
            h.screen().actions.get(0).accept(ClickType.RIGHT);
            var exchange = org.mockito.ArgumentCaptor.forClass(CommerceRepository.Exchange.class);
            verify(h.exchanges).execute(eq(h.player),exchange.capture(),any(),any(),any(),any());
            assertEquals("bag",exchange.getValue().kind()); assertEquals(7,exchange.getValue().bagRevision()); assertEquals(0,exchange.getValue().coinsDelta());
            assertEquals(h.world.getUID(),exchange.getValue().world()); assertArrayEquals(new byte[]{1},exchange.getValue().bagBefore());
        }
    }
    @Test void changingToReadonlyWorldBeforeClickCannotSaveStaleWritableView() throws Exception {
        try(var h = new UiHarness(); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            when(h.world.getName()).thenReturn("world");
            ItemStack[] bag = new ItemStack[36]; bag[0] = h.items.item("stone",12,64);
            codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenReturn(bag);
            when(h.commerce.bag(h.id,18)).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Bag(18,0,new byte[]{1})));
            new BagMenu(h.plugin).open(h.player); h.pump(); when(h.world.getName()).thenReturn("lobby");
            h.screen().actions.get(0).accept(ClickType.LEFT);
            verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any()); verify(h.messages).send(h.player,"bag-readonly");
        }
    }
    @Test void bothSurvivalWorldsCanDepositAndReadonlyBlocksEveryClickAndDrag() throws Exception {
        for(String world : new String[]{"world","world_new"}) try(var h = new UiHarness(); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            when(h.world.getName()).thenReturn(world); ItemStack[] held = new ItemStack[36]; held[0]=h.items.item("named-diamond",12,64);
            when(h.inventory.getStorageContents()).thenReturn(held);
            codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenReturn(new ItemStack[36]);
            codec.when(() -> InventoryItems.encode(any(ItemStack[].class))).thenReturn(new byte[]{2});
            when(h.commerce.bag(h.id,18)).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Bag(18,0,new byte[0])));
            when(h.exchanges.execute(any(),any(),any(),any(),any(),any())).thenReturn(new CompletableFuture<>());
            new BagMenu(h.plugin).open(h.player); h.pump(); h.screen().bottom.accept(0,ClickType.SHIFT_LEFT);
            var after=org.mockito.ArgumentCaptor.forClass(ItemStack[].class);
            verify(h.exchanges).execute(eq(h.player),any(),any(),after.capture(),any(),any());
            assertNull(after.getValue()[0]); assertEquals(12,held[0].getAmount()); // no early live mutation
        }
        try(var h = new UiHarness(); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            when(h.world.getName()).thenReturn("lobby"); codec.when(() -> InventoryItems.decode(any(byte[].class),eq(36))).thenReturn(new ItemStack[36]);
            when(h.commerce.bag(h.id,18)).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Bag(18,0,new byte[0])));
            new BagMenu(h.plugin).open(h.player); h.pump();
            for(ClickType type:ClickType.values()) {
                var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
                when(event.getWhoClicked()).thenReturn(h.player); when(event.getView()).thenReturn(h.view);
                when(event.getRawSlot()).thenReturn(0); when(event.getClick()).thenReturn(type);
                h.ui.click(event); h.pump(); verify(event).setCancelled(true);
            }
            var drag=mock(org.bukkit.event.inventory.InventoryDragEvent.class);
            when(drag.getWhoClicked()).thenReturn(h.player); when(drag.getView()).thenReturn(h.view);
            h.ui.drag(drag); verify(drag).setCancelled(true);
            verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any());
            verify(h.messages).send(h.player,"bag-readonly");
        }
    }
}
