package land.momo.nekocore.service;

import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Mocks only the Paper item boundary, retaining independent amount, metadata identity and stack size. */
public final class FakeItems {
    private final Map<ItemStack,String> keys = new IdentityHashMap<>();
    public ItemStack item(String metadata, int amount, int stackSize) {
        return configure(mock(ItemStack.class), metadata, amount, stackSize);
    }
    public ItemStack configure(ItemStack item, String metadata, int amount, int stackSize) {
        AtomicInteger count = new AtomicInteger(amount); keys.put(item, metadata);
        when(item.isEmpty()).thenAnswer(ignored -> count.get() == 0);
        when(item.getAmount()).thenAnswer(ignored -> count.get()); when(item.getMaxStackSize()).thenReturn(stackSize);
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(ignored -> item(metadata, count.get(), stackSize));
        when(item.isSimilar(any())).thenAnswer(call -> metadata.equals(keys.get(call.getArgument(0))));
        return item;
    }
}
