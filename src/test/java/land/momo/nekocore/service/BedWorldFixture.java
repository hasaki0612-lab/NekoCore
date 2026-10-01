package land.momo.nekocore.service;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Small block grid, using actual safety algorithm with mocked Paper block queries. */
final class BedWorldFixture {
    final World world = mock(World.class);
    final WorldBorder border = mock(WorldBorder.class);
    final Map<String, Material> overrides = new HashMap<>();
    final Map<Material, Block> blocks = new EnumMap<>(Material.class);
    final Location bed = new Location(world, 128, 72, -436);
    BedWorldFixture() {
        when(world.getUID()).thenReturn(UUID.randomUUID()); when(world.getName()).thenReturn("world_new");
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        for (Material material : List.of(Material.AIR, Material.STONE, Material.WHITE_BED, Material.LAVA,
                Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE, Material.POWDER_SNOW,
                Material.CAMPFIRE, Material.NETHER_PORTAL, Material.OAK_SLAB)) {
            Block block = mock(Block.class); BlockData data = mock(BlockData.class);
            when(block.getType()).thenReturn(material); when(block.getBlockData()).thenReturn(data);
            when(block.isEmpty()).thenReturn(material == Material.AIR);
            when(data.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)).thenReturn(material == Material.STONE || material == Material.MAGMA_BLOCK);
            blocks.put(material, block);
        }
        put(128, 72, -436, Material.WHITE_BED); put(128, 72, -435, Material.WHITE_BED);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            return blocks.get(overrides.getOrDefault(key(x, y, z), y == 71 ? Material.STONE : Material.AIR));
        });
    }
    void put(int x, int y, int z, Material type) { overrides.put(key(x, y, z), type); }
    void layer(int y, Material type) { for (int x = 124; x <= 132; x++) for (int z = -440; z <= -432; z++) put(x, y, z, type); }
    private String key(int x, int y, int z) { return x + ":" + y + ":" + z; }
}
