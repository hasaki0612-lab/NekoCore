package land.momo.nekocore.service;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryItemsTest {
    final FakeItems items = new FakeItems();
    @Test void purchasesMergeStacksAndRefuseFullInventoryWithoutMutation() {
        ItemStack[] before = {items.item("stone",60,64), items.item("other",64,64)};
        var template = items.item("stone",1,64);
        assertEquals(4, InventoryItems.room(before,template,2));
        var after = InventoryItems.add(before,template,4,2); assertEquals(64,after[0].getAmount()); assertEquals(60,before[0].getAmount());
        assertThrows(IllegalArgumentException.class, () -> InventoryItems.add(before,template,5,2)); assertEquals(60,before[0].getAmount());
    }
    @Test void metadataEnchantsDamageAndNamesAreNotSoldAsPlainMaterial() {
        ItemStack[] before = {items.item("sword:plain",1,1),items.item("sword:named",1,1),items.item("sword:enchanted",1,1),items.item("sword:damaged",1,1)};
        var plain = items.item("sword:plain",1,1);
        assertEquals(1, InventoryItems.count(before,plain));
        var after = InventoryItems.remove(before,plain,1); assertNull(after[0]);
        for(int i=1;i<4;i++) assertTrue(before[i].isSimilar(after[i]));
        assertThrows(IllegalArgumentException.class, () -> InventoryItems.remove(before,plain,2));
    }
    @Test void actualItemStackMaximumAndBagCapacityAreRespected() {
        ItemStack[] before = new ItemStack[36]; var pearls = items.item("pearl",1,16);
        var after = InventoryItems.add(before,pearls,32,18);
        assertEquals(16,after[0].getAmount()); assertEquals(16,after[1].getAmount()); assertNull(before[0]);
        for(int i=0;i<18;i++) before[i]=items.item("sword",1,1);
        assertThrows(IllegalArgumentException.class, () -> InventoryItems.add(before,pearls,1,18));
        assertDoesNotThrow(() -> InventoryItems.add(before,pearls,1,27));
    }
    @Test void snapshotsTreatEmptySlotsConsistentlyAndDetectAllChanges() {
        ItemStack[] before = {null,items.item("stone",12,64)};
        assertTrue(InventoryItems.same(before,InventoryItems.copy(before)));
        assertTrue(InventoryItems.same(new ItemStack[]{null},new ItemStack[]{items.item("empty",0,64)}));
        var after = InventoryItems.removeSlot(before,1,1); assertEquals(11,after[1].getAmount()); assertEquals(12,before[1].getAmount());
        assertFalse(InventoryItems.same(before,after));
        assertFalse(InventoryItems.same(before,new ItemStack[]{null,items.item("namedstone",12,64)}));
    }
}
