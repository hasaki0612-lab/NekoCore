package land.momo.nekocore.task;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MlgTrackerTest {
    final UUID player = UUID.randomUUID(), other = UUID.randomUUID(), world = UUID.randomUUID();
    final MlgTracker.BlockKey water = new MlgTracker.BlockKey(world, 1, 64, 1);

    @Test void ownWaterDuringCurrentFallCompletesAtSixteenBlocks() {
        MlgTracker tracker = new MlgTracker(); long now = 1_000_000_000L;
        assertFalse(tracker.move(player, world, 82, 80, false, false, new MlgTracker.BlockKey(world,1,80,1), now, 16));
        assertTrue(tracker.placedWater(player, water, now + 100));
        assertTrue(tracker.move(player, world, 65, 64, false, true, water, now + 200, 16));
        assertFalse(tracker.active(player));
    }

    @Test void preplacedOtherPlayerAndShortFallsNeverCount() {
        MlgTracker tracker = new MlgTracker(); long now = 2_000_000_000L;
        tracker.move(player, world, 82, 80, false, false, new MlgTracker.BlockKey(world,1,80,1), now, 16);
        assertFalse(tracker.move(player, world, 65, 64, false, true, water, now + 10, 16)); // no placement token
        tracker.clear(); tracker.move(player, world, 75, 74, false, false, water, now, 16);
        assertFalse(tracker.placedWater(other, water, now + 1));
        assertFalse(tracker.move(player, world, 65, 64, false, true, water, now + 2, 16));
        tracker.clear(); tracker.move(player, world, 75, 74, false, false, water, now, 16);
        assertTrue(tracker.placedWater(player, water, now + 1));
        assertFalse(tracker.move(player, world, 65, 64, false, true, water, now + 2, 16)); // only 11 blocks
    }

    @Test void groundTeleportDeathWorldChangeAndTimeoutCancelState() {
        MlgTracker tracker = new MlgTracker(); long now = 3_000_000_000L;
        tracker.move(player, world, 90, 88, false, false, water, now, 16);
        tracker.move(player, world, 87, 87, true, false, water, now + 1, 16); assertFalse(tracker.active(player));
        tracker.move(player, world, 90, 88, false, false, water, now, 16); tracker.cancel(player); assertFalse(tracker.active(player));
        tracker.move(player, world, 90, 88, false, false, water, now, 16);
        assertFalse(tracker.move(player, UUID.randomUUID(), 88, 87, false, false, water, now + 1, 16));
        tracker.move(player, world, 90, 88, false, false, water, now, 16); tracker.placedWater(player, water, now + 1);
        assertFalse(tracker.move(player, world, 66, 64, false, true, water, now + 3_000_000_002L, 16));
    }
}
