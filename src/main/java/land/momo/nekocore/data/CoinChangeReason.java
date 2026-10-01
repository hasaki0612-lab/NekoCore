package land.momo.nekocore.data;

/** Only committed positive gameplay income participates in weekly rankings. */
public enum CoinChangeReason {
    CHECKIN(true), DAILY_TASK(true), AFK_POOL(true), STORE_SELL(true), GAMEPLAY_REWARD(true),
    ADMIN(false), PURCHASE(false), RECOVERY(false), ROLLBACK(false), DEBUG(false), MIGRATION(false), COMPENSATION(false);
    private final boolean weekly;
    CoinChangeReason(boolean weekly) { this.weekly = weekly; }
    public boolean countsForWeekly() { return weekly; }
}
