package land.momo.nekocore.task;

public enum DailyTaskDifficulty {
    EASY("easy"), NORMAL("normal"), HARD("hard");

    private final String key;
    DailyTaskDifficulty(String key) { this.key = key; }
    public String key() { return key; }

    public static DailyTaskDifficulty parse(String value) {
        for (DailyTaskDifficulty difficulty : values()) if (difficulty.key.equalsIgnoreCase(value)) return difficulty;
        throw new IllegalArgumentException("未知任务难度：" + value);
    }
}
