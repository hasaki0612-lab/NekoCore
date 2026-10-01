package land.momo.nekocore.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CleanupClockTest {
    @Test void exactRequiredWarningsRepeatWithoutOverlap() {
        CleanupClock clock = new CleanupClock(600);
        for (int cycle = 0; cycle < 3; cycle++) {
            List<Integer> warnings = new ArrayList<>();
            int cleans = 0;
            for (int second = 1; second <= 600; second++) {
                int left = clock.tick();
                if (CleanupClock.warning(left)) warnings.add(left);
                if (left == 0) { cleans++; clock.reset(); }
            }
            assertEquals(List.of(60, 20, 5, 4, 3, 2, 1), warnings); assertEquals(1, cleans);
        }
    }
    @Test void minimumIntervalAndManualResetKeepFullCountdown() {
        CleanupClock clock = new CleanupClock(61);
        assertEquals(60, clock.tick());
        for (int i = 0; i < 59; i++) clock.tick();
        clock.reset(); assertEquals(60, clock.tick());
        assertThrows(IllegalArgumentException.class, () -> new CleanupClock(60));
    }
}
