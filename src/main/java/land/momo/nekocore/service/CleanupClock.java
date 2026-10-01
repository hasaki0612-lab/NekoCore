package land.momo.nekocore.service;

/** One synchronous tick per second; every required warning occurs once per cycle. */
public final class CleanupClock {
    private final int interval;
    private int remaining;
    public CleanupClock(int interval) {
        if (interval < 61) throw new IllegalArgumentException("Cleanup interval must be >= 61");
        this.interval = interval;
        reset();
    }
    public int tick() { return --remaining; }
    public void reset() { remaining = interval; }
    public static boolean warning(int remaining) { return remaining == 60 || remaining == 20 || remaining >= 1 && remaining <= 5; }
}
