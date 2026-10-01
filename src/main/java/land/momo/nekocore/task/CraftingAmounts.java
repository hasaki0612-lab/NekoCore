package land.momo.nekocore.task;

import org.bukkit.Material;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/** Calculates the successful output count represented by one workbench click, including shift-craft. */
public final class CraftingAmounts {
    private CraftingAmounts() {}
    public static int output(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        int each = result.getAmount();
        if (!event.isShiftClick()) return each;
        int crafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : event.getInventory().getMatrix())
            if (ingredient != null && !isAir(ingredient.getType())) crafts = Math.min(crafts, ingredient.getAmount());
        if (crafts == Integer.MAX_VALUE) return each;
        int capacity = 0;
        for (ItemStack item : event.getWhoClicked().getInventory().getStorageContents()) {
            if (item == null || isAir(item.getType())) capacity += result.getMaxStackSize();
            else if (item.isSimilar(result)) capacity += Math.max(0, item.getMaxStackSize() - item.getAmount());
        }
        return Math.max(0, Math.min(crafts, capacity / each)) * each;
    }
    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
