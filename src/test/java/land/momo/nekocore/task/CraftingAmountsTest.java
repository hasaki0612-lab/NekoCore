package land.momo.nekocore.task;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CraftingAmountsTest {
    @Test void ordinaryClickCountsRecipeOutputStack() {
        CraftItemEvent event=mock(CraftItemEvent.class); Recipe recipe=mock(Recipe.class); ItemStack result=mock(ItemStack.class);
        when(event.getRecipe()).thenReturn(recipe); when(recipe.getResult()).thenReturn(result);
        when(result.getAmount()).thenReturn(8); when(event.isShiftClick()).thenReturn(false);
        assertEquals(8,CraftingAmounts.output(event));
    }

    @Test void shiftClickIsLimitedByIngredientsAndInventoryCapacity() {
        CraftItemEvent event=mock(CraftItemEvent.class); Recipe recipe=mock(Recipe.class); ItemStack result=mock(ItemStack.class);
        CraftingInventory crafting=mock(CraftingInventory.class); Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class);
        ItemStack ingredientA=stack(Material.WHEAT,3), ingredientB=stack(Material.COCOA_BEANS,5);
        ItemStack similar=stack(Material.COOKIE,60); ItemStack empty=null;
        when(event.getRecipe()).thenReturn(recipe); when(recipe.getResult()).thenReturn(result); when(event.isShiftClick()).thenReturn(true);
        when(result.getAmount()).thenReturn(4); when(result.getMaxStackSize()).thenReturn(64);
        when(event.getInventory()).thenReturn(crafting); when(crafting.getMatrix()).thenReturn(new ItemStack[]{ingredientA,ingredientB});
        when(event.getWhoClicked()).thenReturn(player); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{similar,empty});
        when(similar.isSimilar(result)).thenReturn(true); when(similar.getMaxStackSize()).thenReturn(64);
        assertEquals(12,CraftingAmounts.output(event));
    }

    @Test void fullInventoryMakesShiftCraftOutputZero() {
        CraftItemEvent event=mock(CraftItemEvent.class); Recipe recipe=mock(Recipe.class); ItemStack result=mock(ItemStack.class);
        CraftingInventory crafting=mock(CraftingInventory.class); Player player=mock(Player.class); PlayerInventory inventory=mock(PlayerInventory.class);
        ItemStack ingredient=stack(Material.WHEAT,12), unrelated=stack(Material.STONE,64);
        when(event.getRecipe()).thenReturn(recipe); when(recipe.getResult()).thenReturn(result); when(event.isShiftClick()).thenReturn(true);
        when(result.getAmount()).thenReturn(4); when(result.getMaxStackSize()).thenReturn(64);
        when(event.getInventory()).thenReturn(crafting); when(crafting.getMatrix()).thenReturn(new ItemStack[]{ingredient});
        when(event.getWhoClicked()).thenReturn(player); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{unrelated}); when(unrelated.isSimilar(result)).thenReturn(false);
        assertEquals(0,CraftingAmounts.output(event));
    }

    private static ItemStack stack(Material material,int amount) {
        ItemStack item=mock(ItemStack.class); when(item.getType()).thenReturn(material); when(item.getAmount()).thenReturn(amount); return item;
    }
}
