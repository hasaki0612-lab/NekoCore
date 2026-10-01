package land.momo.nekocore.gui;

import land.momo.nekocore.data.TitleRepository;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TitleMenuTest {
    @Test void onlyAllowedWorldAndPermissionCanOpenAndPurchase() throws Exception {
        try(var h = new UiHarness()) {
            var menu = new TitleMenu(h.plugin); when(h.world.getName()).thenReturn("world"); menu.open(h.player);
            verify(h.messages).send(h.player,"title-lobby-only"); verify(h.player,never()).openInventory(any(org.bukkit.inventory.Inventory.class));
            when(h.world.getName()).thenReturn("lobby"); menu.open(h.player); h.screen().actions.get(11).accept(ClickType.LEFT);
            when(h.world.getName()).thenReturn("world_new"); h.screen().actions.get(22).accept(ClickType.LEFT);
            verify(h.titles,never()).purchase(any(),anyString(),anyLong());
        }
    }
    @Test void purchaseWaitsForCommitAndRepeatedClicksCannotChargeAgain() throws Exception {
        try(var h = new UiHarness()) {
            var future = new CompletableFuture<TitleRepository.Titles>(); when(h.titles.purchase(h.id,"mame",2500)).thenReturn(future);
            var menu = new TitleMenu(h.plugin); menu.open(h.player); h.screen().actions.get(11).accept(ClickType.LEFT);
            var detail = h.screen(); detail.actions.get(22).accept(ClickType.LEFT); detail.actions.get(22).accept(ClickType.LEFT);
            verify(h.titles).purchase(h.id,"mame",2500); assertTrue(menu.hasPending());
            verify(h.messages,never()).send(eq(h.player),eq("title-purchased"),anyMap());
            var owned = new TitleRepository.Titles(Set.of("mame"),"mame"); when(h.titles.cached(h.id)).thenReturn(owned); future.complete(owned); h.pump();
            assertFalse(menu.hasPending()); verify(h.messages).send(eq(h.player),eq("title-purchased"),anyMap()); verify(h.plugin.nameTags()).update(h.player);
        }
    }
    @Test void owningASecondTierDoesNotRemoveFirstAndEquipAndUnequipAreFree() throws Exception {
        try(var h = new UiHarness()) {
            var owned = new TitleRepository.Titles(Set.of("mame","momo"),"momo"); when(h.titles.cached(h.id)).thenReturn(owned);
            when(h.titles.equip(h.id,"mame")).thenReturn(CompletableFuture.completedFuture(new TitleRepository.Titles(owned.owned(),"mame")));
            when(h.titles.equip(h.id,"")).thenReturn(CompletableFuture.completedFuture(new TitleRepository.Titles(owned.owned(),"")));
            var menu = new TitleMenu(h.plugin); menu.open(h.player); h.screen().actions.get(11).accept(ClickType.LEFT);
            h.screen().actions.get(22).accept(ClickType.LEFT); h.pump(); verify(h.titles).equip(h.id,"mame");
            h.screen().actions.get(22).accept(ClickType.LEFT); h.pump(); verify(h.titles).equip(h.id,"");
            verify(h.titles,never()).purchase(any(),anyString(),anyLong());
        }
    }
    @Test void databaseFailureNeverClaimsPurchaseOrUpdatesNametag() throws Exception {
        try(var h = new UiHarness()) {
            when(h.titles.purchase(h.id,"mame",2500)).thenReturn(CompletableFuture.failedFuture(new land.momo.nekocore.data.StorageException("not-enough-coins")));
            var menu = new TitleMenu(h.plugin); menu.open(h.player); h.screen().actions.get(11).accept(ClickType.LEFT);
            h.screen().actions.get(22).accept(ClickType.LEFT); h.pump();
            verify(h.messages).send(h.player,"not-enough-coins"); verify(h.messages,never()).send(eq(h.player),eq("title-purchased"),anyMap());
            verify(h.plugin.nameTags(),never()).update(any()); assertFalse(menu.hasPending());
        }
    }
}
