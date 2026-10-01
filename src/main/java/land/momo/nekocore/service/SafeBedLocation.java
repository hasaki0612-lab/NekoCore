package land.momo.nekocore.service;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import java.util.*;

/** Conservative, bounded main-thread search in already loaded chunks; no terrain changes. */
public final class SafeBedLocation {
    private SafeBedLocation() {}
    private static final Set<Material> HAZARDS = EnumSet.of(Material.LAVA, Material.WATER,
            Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POWDER_SNOW, Material.POINTED_DRIPSTONE,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY);

    public static Optional<Location> find(Location bed, float yaw) {
        World world = Objects.requireNonNull(bed.getWorld());
        List<Location> candidates = new ArrayList<>();
        // Radius 3 around the sleeping (head) block also covers both sides of the foot block.
        for (int y : new int[]{0, 1, -1}) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            // Never stand on or directly above either bed block.
            if (x == 0 && z == 0) continue;
            candidates.add(new Location(world, bed.getBlockX() + x + 0.5, bed.getBlockY() + y,
                    bed.getBlockZ() + z + 0.5, yaw, 0));
        }
        Location center = new Location(world, bed.getBlockX() + 0.5, bed.getBlockY(), bed.getBlockZ() + 0.5);
        candidates.sort(Comparator.comparingDouble(at -> at.distanceSquared(center)));
        return candidates.stream().filter(SafeBedLocation::safe).findFirst();
    }

    private static boolean safe(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) return false;
        // Include the standing player's width in world-border validation.
        for (double dx : new double[]{-0.31, 0.31}) for (double dz : new double[]{-0.31, 0.31})
            if (!world.getWorldBorder().isInside(at.clone().add(dx, 0, dz))) return false;
        // Do not accidentally synchronously load a chunk during neighbour inspection.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
        if (!world.getBlockAt(x, y, z).isEmpty() || !world.getBlockAt(x, y + 1, z).isEmpty()) return false;
        var floor = world.getBlockAt(x, y - 1, z);
        // FULL top support excludes beds, lower slabs, fences and open trapdoors.
        if (!floor.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL)) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++)
            if (HAZARDS.contains(world.getBlockAt(x + dx, y + dy, z + dz).getType())) return false;
        return true;
    }
}
