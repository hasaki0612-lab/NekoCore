package land.momo.nekocore.task;

import java.time.LocalDate;
import java.util.*;

public record DailyTaskRotation(LocalDate date, UUID id, List<Entry> entries) {
    public record Entry(DailyTaskDifficulty difficulty, int index, String taskId) {}
    public DailyTaskRotation {
        entries = List.copyOf(entries);
        if (entries.stream().map(Entry::taskId).distinct().count() != entries.size())
            throw new IllegalArgumentException("Daily rotation contains duplicate task IDs");
    }
    public boolean contains(String taskId) { return entries.stream().anyMatch(entry -> entry.taskId().equals(taskId)); }
    public Entry entry(String taskId) { return entries.stream().filter(entry -> entry.taskId().equals(taskId)).findFirst().orElse(null); }
}
