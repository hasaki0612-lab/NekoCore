package land.momo.nekocore.task;

import land.momo.nekocore.config.DailyTaskSettings;

import java.util.*;
import java.util.random.RandomGenerator;

public final class DailyTaskSelector {
    private DailyTaskSelector() {}
    public static List<DailyTaskRotation.Entry> draw(DailyTaskSettings settings, RandomGenerator random) {
        List<DailyTaskRotation.Entry> result = new ArrayList<>();
        for (DailyTaskDifficulty difficulty : DailyTaskDifficulty.values()) {
            List<String> values = new ArrayList<>(settings.pools().get(difficulty));
            for (int i = values.size() - 1; i > 0; i--) {
                int other = random.nextInt(i + 1); String swap = values.get(i); values.set(i, values.get(other)); values.set(other, swap);
            }
            int count = settings.drawCounts().get(difficulty);
            for (int i = 0; i < count; i++) result.add(new DailyTaskRotation.Entry(difficulty, i, values.get(i)));
        }
        return List.copyOf(result);
    }
}
