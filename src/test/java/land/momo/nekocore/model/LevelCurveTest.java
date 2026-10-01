package land.momo.nekocore.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelCurveTest {
    private final LevelCurve curve = new LevelCurve(100, 50, 0, 10_000);

    @Test void boundariesAndRemainder() {
        assertEquals(new LevelCurve.Progress(1, 0, 100), curve.progress(0));
        assertEquals(new LevelCurve.Progress(1, 99, 100), curve.progress(99));
        assertEquals(new LevelCurve.Progress(2, 0, 150), curve.progress(100));
        assertEquals(new LevelCurve.Progress(3, 1, 200), curve.progress(251));
    }

    @Test void quadraticFormulaMatchesIndependentSummation() {
        LevelCurve polynomial = new LevelCurve(17, 9, 3, 150);
        long total = 0;
        for (int level = 1; level < 150; level++) {
            long n = level - 1;
            long cost = 17 + 9 * n + 3 * n * n;
            assertEquals(total, polynomial.totalAt(level));
            assertEquals(new LevelCurve.Progress(level, 0, cost), polynomial.progress(total));
            assertEquals(level, polynomial.progress(total + cost - 1).level());
            total += cost;
        }
        assertEquals(new LevelCurve.Progress(150, 0, 0), polynomial.progress(total));
    }

    @Test void handlesLongMaximumWithoutAnUpgradeLoop() {
        var progress = curve.progress(Long.MAX_VALUE);
        assertEquals(10_000, progress.level());
        assertEquals(0, progress.needed());
        assertEquals(Long.MAX_VALUE - curve.totalAt(10_000), progress.exp());
    }

    @Test void rejectsInvalidOrOverflowingConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(0, 0, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(1, -1, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(1, 0, 0, 1));
        assertThrows(ArithmeticException.class, () -> new LevelCurve(Long.MAX_VALUE, 1, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> curve.progress(-1));
    }
}
