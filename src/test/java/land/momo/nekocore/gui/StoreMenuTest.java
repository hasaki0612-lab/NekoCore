package land.momo.nekocore.gui;

import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.service.InventoryItems;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StoreMenuTest {
    MockedConstruction<ItemStack> commodities(UiHarness h) {
        return mockConstruction(ItemStack.class,(item,context) -> h.items.configure(item,"commodity",1,64));
    }
    StoreMenu quantity(UiHarness h,boolean buy) {
        when(h.commerce.quota(eq(h.id),anyString(),isNull())).thenReturn(CompletableFuture.completedFuture(new CommerceRepository.Quota("2026-09-29",0,0)));
        var menu = new StoreMenu(h.plugin); menu.open(h.player);
        assertEquals(45,h.top.getSize()); h.screen().actions.get(10).accept(ClickType.LEFT); assertEquals(54,h.top.getSize());
        h.screen().actions.get(10).accept(buy?ClickType.RIGHT:ClickType.LEFT); h.pump(); assertEquals(36,h.top.getSize()); return menu;
    }
    ChatEvent chat(UiHarness h,String text) {
        ChatEvent event = mock(ChatEvent.class); when(event.getPlayer()).thenReturn(h.player); when(event.message()).thenReturn(Component.text(text)); return event;
    }
    @Test void threeLevelNavigationAndRightBuyLeftSellAreConsistent() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            quantity(h,true); assertTrue(h.screen().actions.containsKey(35));
            h.screen().actions.get(35).accept(ClickType.LEFT); assertEquals(54,h.top.getSize());
            h.screen().actions.get(53).accept(ClickType.LEFT); assertEquals(45,h.top.getSize());
            quantity(h,false); h.rendering.verify(() -> FeatureGui.decorate(any(),anyString(),anyList(),argThat(vars -> "store-action-sell".equals(vars.get("action")))),atLeastOnce());
        }
    }
    @Test void validCustomInputIsPrivateAndRequiresExplicitConfirmation() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            var menu = quantity(h,true); h.screen().actions.get(21).accept(ClickType.LEFT);
            var event = chat(h,"16"); menu.chat(event); verify(event).setCancelled(true); h.pump();
            assertEquals(36,h.top.getSize()); verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any());
            h.rendering.verify(() -> FeatureGui.decorate(any(),anyString(),anyList(),argThat(vars -> "16".equals(vars.get("quantity")))),atLeastOnce());
        }
    }
    @Test void invalidInputStaysPrivateAndCancelAndTimeoutReturnToQuantity() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            var menu = quantity(h,true); h.screen().actions.get(21).accept(ClickType.LEFT);
            for(String invalid:List.of("0","-1","1.5","9999999999999999999999","65","hello")) {
                var event = chat(h,invalid); menu.chat(event); verify(event).setCancelled(true); h.pump();
            }
            verify(h.messages,times(6)).send(eq(h.player),eq("store-input-invalid"),anyMap());
            var cancelled = chat(h,"取消"); menu.chat(cancelled); h.pump(); assertEquals(36,h.top.getSize());
            h.screen().actions.get(21).accept(ClickType.LEFT); h.timers.getLast().run(); h.pump();
            verify(h.messages).send(h.player,"store-input-timeout"); assertEquals(36,h.top.getSize());
        }
    }
    @Test void stoppingInputLeavesNormalChatUntouched() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            var menu = quantity(h,true); h.screen().actions.get(21).accept(ClickType.LEFT); menu.stop();
            var event = chat(h,"hello"); menu.chat(event); verify(event,never()).setCancelled(true);
        }
    }
    @Test void fullInventoryRefusesBeforeChargingOrPreparingDatabase() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            ItemStack[] full = new ItemStack[36]; for(int i=0;i<36;i++) full[i]=h.items.item("unrelated",64,64);
            when(h.inventory.getStorageContents()).thenReturn(full); quantity(h,true); h.screen().actions.get(23).accept(ClickType.LEFT);
            verify(h.messages).send(h.player,"inventory-full"); verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any());
            verify(h.commerce,never()).prepareStore(any(),anyLong(),anyInt(),anyBoolean());
        }
    }
    @Test void buyCreatesServerCalculatedDeltaAndLimitAndDoesNotDropItems() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h); var codec = mockStatic(InventoryItems.class,CALLS_REAL_METHODS)) {
            codec.when(() -> InventoryItems.encode(any(ItemStack[].class))).thenReturn(new byte[]{1});
            when(h.exchanges.execute(any(),any(),any(),any(),any(),any())).thenReturn(new CompletableFuture<>());
            quantity(h,true); h.screen().actions.get(23).accept(ClickType.LEFT);
            var exchange = org.mockito.ArgumentCaptor.forClass(CommerceRepository.Exchange.class);
            @SuppressWarnings("unchecked") var prepare = org.mockito.ArgumentCaptor.forClass(Supplier.class);
            verify(h.exchanges).execute(eq(h.player),exchange.capture(),any(),any(),any(),prepare.capture());
            assertEquals(-3,exchange.getValue().coinsDelta()); assertEquals(1,exchange.getValue().quantity()); assertEquals("buy",exchange.getValue().direction());
            prepare.getValue().get(); verify(h.commerce).prepareStore(exchange.getValue(),3,64,false);
        }
    }
    @Test void sellQuantityIsLimitedByMatchedInventoryAndRemainingDailyQuota() throws Exception {
        try(var h = new UiHarness(); var construction = commodities(h)) {
            ItemStack[] held = new ItemStack[36]; held[0]=h.items.item("commodity",3,64); when(h.inventory.getStorageContents()).thenReturn(held);
            quantity(h,false); h.screen().actions.get(11).accept(ClickType.LEFT); h.pump(); // choose eight
            h.screen().actions.get(23).accept(ClickType.LEFT); h.pump();
            verify(h.messages).send(eq(h.player),eq("store-range"),eq(Map.of("maximum","3")));
            verify(h.exchanges,never()).execute(any(),any(),any(),any(),any(),any()); // smaller amount still needs confirmation
            h.rendering.verify(() -> FeatureGui.decorate(any(),anyString(),anyList(),argThat(vars -> "3".equals(vars.get("quantity")))),atLeastOnce());
        }
    }
    @Test void quantityParserRejectsNonIntegersOverflowAndZero() {
        for(String text:List.of("0","-1","+1","1.0","2147483648","1e2"," 2","cancel")) assertEquals(-1,StoreMenu.parseQuantity(text,64));
        assertEquals(64,StoreMenu.parseQuantity("64",64)); assertEquals(-1,StoreMenu.parseQuantity("65",64));
    }
    @Test void productsFillThreeRegularRowsThenContinueOnNextPageWithoutDroppingLegacySlots() {
        List<Integer> layout=List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34);
        List<StoreMenu.Entry> entries=new ArrayList<>();
        entries.add(new StoreMenu.Entry("legacy","plants","Legacy",null,1,true,64,200,0,null));
        for(int i=1;i<23;i++) entries.add(new StoreMenu.Entry("p"+i,"plants","P"+i,null,1,true,64,200,-1,null));
        var placed=StoreMenu.positions(entries,layout);
        assertEquals(23,placed.size()); assertEquals(List.of(10,11,12,13,14,15,16),placed.keySet().stream().limit(7).toList());
        assertEquals("legacy",placed.get(10).id()); assertEquals("p21",placed.get(64).id()); assertEquals("p22",placed.get(65).id());
    }
}
