package land.momo.nekocore.model;

import java.math.BigInteger;

/** Integer polynomial curve; binary search keeps huge admin grants bounded. */
public record LevelCurve(long base, long linear, long quadratic, int maxLevel) {
    public LevelCurve {
        if (base < 1 || linear < 0 || quadratic < 0 || maxLevel < 2 || maxLevel > 1_000_000)
            throw new IllegalArgumentException("leveling 参数无效：base>=1，linear/quadratic>=0，max-level 为 2..1000000");
        totalAt(maxLevel, base, linear, quadratic).longValueExact();
    }

    private static BigInteger totalAt(int level, long base, long linear, long quadratic) {
        BigInteger n = BigInteger.valueOf(level - 1L);
        BigInteger previous = n.subtract(BigInteger.ONE);
        return n.multiply(BigInteger.valueOf(base))
                .add(n.multiply(previous).divide(BigInteger.TWO).multiply(BigInteger.valueOf(linear)))
                .add(n.multiply(previous).multiply(n.multiply(BigInteger.TWO).subtract(BigInteger.ONE))
                        .divide(BigInteger.valueOf(6)).multiply(BigInteger.valueOf(quadratic)));
    }

    public long totalAt(int level) {
        return totalAt(level, base, linear, quadratic).longValueExact();
    }

    public Progress progress(long total) {
        if (total < 0) throw new IllegalArgumentException("Experience cannot be negative");
        int low = 1, high = maxLevel;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (totalAt(middle) <= total) low = middle; else high = middle - 1;
        }
        return new Progress(low, total - totalAt(low), low == maxLevel ? 0 : totalAt(low + 1) - totalAt(low));
    }

    public record Progress(int level, long exp, long needed) {}
}
