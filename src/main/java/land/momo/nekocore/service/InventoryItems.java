package land.momo.nekocore.service;

import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Main-thread-only ItemStack handling. The database only receives the serialized snapshots. */
public final class InventoryItems {
    private InventoryItems() {}
    public static boolean empty(ItemStack item) { return item == null || item.isEmpty() || item.getAmount() <= 0; }
    public static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items).map(i -> empty(i) ? null : i.clone()).toArray(ItemStack[]::new);
    }
    public static byte[] encode(ItemStack[] items) { return ItemStack.serializeItemsAsBytes(copy(items)); }
    public static ItemStack[] decode(byte[] bytes, int slots) {
        if (bytes.length == 0) return new ItemStack[slots];
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
        if (items.length != slots) throw new IllegalArgumentException("物品快照格数不匹配，保留原始数据等待核对");
        for (ItemStack item : items) if (!empty(item) && item.getAmount() > item.getMaxStackSize())
            throw new IllegalArgumentException("物品快照含有超量堆叠，保留原始数据等待核对");
        return copy(items);
    }
    public static boolean same(ItemStack[] first, ItemStack[] second) {
        if (first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            if (empty(first[i]) && empty(second[i])) continue;
            if (empty(first[i]) || empty(second[i]) || first[i].getAmount() != second[i].getAmount() || !first[i].isSimilar(second[i])) return false;
        }
        return true;
    }
    public static int count(ItemStack[] items, ItemStack template) {
        int count = 0;
        for (ItemStack item : items) if (!empty(item) && item.isSimilar(template)) count = Math.addExact(count, item.getAmount());
        return count;
    }
    public static int room(ItemStack[] items, ItemStack template, int slots) {
        int room = 0;
        for (int i = 0; i < slots; i++) {
            if (empty(items[i])) room += template.getMaxStackSize();
            else if (items[i].isSimilar(template)) room += Math.max(0, template.getMaxStackSize() - items[i].getAmount());
        }
        return room;
    }
    public static ItemStack[] add(ItemStack[] original, ItemStack template, int amount, int slots) {
        if (amount <= 0 || slots > original.length || room(original, template, slots) < amount) throw new IllegalArgumentException("inventory-full");
        ItemStack[] result = copy(original); int remaining = amount;
        for (int i = 0; i < slots && remaining > 0; i++) if (!empty(result[i]) && result[i].isSimilar(template)) {
            int moved = Math.min(remaining, Math.max(0, template.getMaxStackSize() - result[i].getAmount()));
            result[i].setAmount(result[i].getAmount() + moved); remaining -= moved;
        }
        for (int i = 0; i < slots && remaining > 0; i++) if (empty(result[i])) {
            int moved = Math.min(remaining, template.getMaxStackSize()); result[i] = template.clone(); result[i].setAmount(moved); remaining -= moved;
        }
        return result;
    }
    public static ItemStack[] remove(ItemStack[] original, ItemStack template, int amount) {
        if (amount <= 0 || count(original, template) < amount) throw new IllegalArgumentException("store-not-owned");
        ItemStack[] result = copy(original); int remaining = amount;
        for (int i = 0; i < result.length && remaining > 0; i++) if (!empty(result[i]) && result[i].isSimilar(template)) {
            int moved = Math.min(remaining, result[i].getAmount());
            if (moved == result[i].getAmount()) result[i] = null; else result[i].setAmount(result[i].getAmount() - moved);
            remaining -= moved;
        }
        return result;
    }
    public static ItemStack[] removeSlot(ItemStack[] original, int slot, int amount) {
        ItemStack[] result = copy(original);
        if (slot < 0 || slot >= result.length || empty(result[slot]) || amount < 1 || result[slot].getAmount() < amount)
            throw new IllegalArgumentException("exchange-stale");
        if (result[slot].getAmount() == amount) result[slot] = null; else result[slot].setAmount(result[slot].getAmount() - amount);
        return result;
    }
}
