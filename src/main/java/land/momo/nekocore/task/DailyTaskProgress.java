package land.momo.nekocore.task;

public record DailyTaskProgress(String taskId, long progress, boolean completed, boolean rewardGiven, String extraState) {}
