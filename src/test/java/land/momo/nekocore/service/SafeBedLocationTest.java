package land.momo.nekocore.service;

import org.bukkit.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafeBedLocationTest {
    BedWorldFixture grid;
    @BeforeEach void setup() { grid = new BedWorldFixture(); }
    @Test void prefersNearestClearStandingSpaceNotEitherBedAndCentersPlayer() {
        Location at = SafeBedLocation.find(grid.bed, 42).orElseThrow();
        assertEquals(72, at.getY()); assertEquals(0.5, at.getX() - at.getBlockX()); assertEquals(0.5, at.getZ() - at.getBlockZ());
        assertEquals(42, at.getYaw()); assertEquals(0, at.getPitch());
        assertEquals(Material.AIR, grid.world.getBlockAt(at.getBlockX(), 72, at.getBlockZ()).getType());
        assertTrue(at.distanceSquared(grid.bed.clone().add(0.5, 0, 0.5)) <= 1);
    }
    @ParameterizedTest @EnumSource(value = Material.class, names = {"LAVA", "WATER", "MAGMA_BLOCK", "CACTUS", "FIRE", "POWDER_SNOW", "CAMPFIRE", "NETHER_PORTAL"})
    void refusesDangerousGroundOrNearbyHazards(Material hazard) {
        grid.layer(71, hazard); assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
    }
    @Test void refusesLowCeilingVoidAndPartialSupport() {
        grid.layer(73, Material.STONE); assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
        grid.layer(73, Material.AIR); grid.layer(71, Material.AIR); assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
        grid.layer(71, Material.OAK_SLAB); assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
    }
    @Test void refusesUnloadedChunksWithoutReadingTheirBlocks() {
        when(grid.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty()); verify(grid.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }
    @Test void respectsWorldBorderAndHeight() {
        when(grid.border.isInside(any())).thenReturn(false); assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
        when(grid.border.isInside(any())).thenReturn(true); when(grid.world.getMaxHeight()).thenReturn(72);
        assertTrue(SafeBedLocation.find(grid.bed, 0).isEmpty());
    }
}
