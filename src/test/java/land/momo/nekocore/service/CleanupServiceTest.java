package land.momo.nekocore.service;

import org.bukkit.World;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CleanupServiceTest {
    @Test void removesOnlyValidDroppedItemEntitiesAndCountsEntitiesNotStacks() {
        World world = mock(World.class);
        Item stackOf64 = mock(Item.class), stackOf1 = mock(Item.class), dead = mock(Item.class), invalid = mock(Item.class);
        when(stackOf64.isValid()).thenReturn(true); when(stackOf1.isValid()).thenReturn(true);
        when(dead.isValid()).thenReturn(true); when(dead.isDead()).thenReturn(true);
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(stackOf64, stackOf1, dead, invalid));
        assertEquals(2, CleanupService.removeItems(world));
        verify(stackOf64).remove(); verify(stackOf1).remove();
        verify(dead, never()).remove(); verify(invalid, never()).remove();
        verify(world).getEntitiesByClass(Item.class);
        verifyNoMoreInteractions(world); // No broad entity selection or chunk loading.
    }
}
