package land.momo.nekocore.task;

import java.util.*;

/** In-memory only state machine. A persisted daily task stores only its final 0/1 progress. */
public final class MlgTracker {
    public record BlockKey(UUID world, int x, int y, int z) {}
    private static final long WATER_WINDOW_NANOS = 3_000_000_000L;
    private static final class Fall {
        final UUID world;
        double highestY;
        double lastY;
        BlockKey water;
        long waterPlacedAt;
        Fall(UUID world, double highestY, double lastY) { this.world = world; this.highestY = highestY; this.lastY = lastY; }
    }
    private final Map<UUID,Fall> falls = new HashMap<>();

    public boolean move(UUID player, UUID world, double fromY, double toY, boolean onGround,
                        boolean inExactWater, BlockKey feet, long now, double minimumHeight) {
        Fall fall = falls.get(player);
        if (fall != null && !fall.world.equals(world)) { falls.remove(player); fall = null; }
        boolean descending = toY < fromY - 0.01;
        if (fall == null && descending && !onGround) {
            fall = new Fall(world, Math.max(fromY, toY), toY); falls.put(player, fall);
        }
        if (fall == null) return false;
        fall.highestY = Math.max(fall.highestY, fromY); fall.lastY = toY;
        if (fall.water != null && now - fall.waterPlacedAt <= WATER_WINDOW_NANOS
                && inExactWater && fall.water.equals(feet) && fall.highestY - toY >= minimumHeight) {
            falls.remove(player); return true;
        }
        if (onGround || fall.water != null && now - fall.waterPlacedAt > WATER_WINDOW_NANOS) falls.remove(player);
        return false;
    }

    public boolean placedWater(UUID player, BlockKey block, long now) {
        Fall fall = falls.get(player);
        if (fall == null || !fall.world.equals(block.world()) || fall.highestY <= fall.lastY + 0.01) return false;
        fall.water = block; fall.waterPlacedAt = now; return true;
    }
    public void cancel(UUID player) { falls.remove(player); }
    public void clear() { falls.clear(); }
    public boolean active(UUID player) { return falls.containsKey(player); }
}
