package land.momo.nekocore.task;

import land.momo.nekocore.TestDefaults;
import land.momo.nekocore.config.DailyTaskSettings;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class DailyTaskSelectorTest {
    @Test void drawsExactlyThreeUniqueTasksPerDifficultyAndNineTotal() throws Exception {
        DailyTaskSettings settings = DailyTaskSettings.load(TestDefaults.yaml());
        var random = RandomGeneratorFactory.<java.util.random.RandomGenerator>of("L64X128MixRandom").create(42);
        var rotation = DailyTaskSelector.draw(settings, random);
        assertEquals(9, rotation.size()); assertEquals(9, rotation.stream().map(DailyTaskRotation.Entry::taskId).distinct().count());
        for (DailyTaskDifficulty difficulty : DailyTaskDifficulty.values()) {
            var selected = rotation.stream().filter(entry -> entry.difficulty() == difficulty).toList();
            assertEquals(3, selected.size()); assertEquals(Set.of(0,1,2), new HashSet<>(selected.stream().map(DailyTaskRotation.Entry::index).toList()));
            assertTrue(selected.stream().allMatch(entry -> settings.pools().get(difficulty).contains(entry.taskId())));
        }
    }

    @Test void stableIdsCoverTheCompleteSevenEightFiveCatalog() {
        assertEquals(7, Arrays.stream(DailyTaskDefinition.values()).filter(d -> d.difficulty() == DailyTaskDifficulty.EASY).count());
        assertEquals(8, Arrays.stream(DailyTaskDefinition.values()).filter(d -> d.difficulty() == DailyTaskDifficulty.NORMAL).count());
        assertEquals(5, Arrays.stream(DailyTaskDefinition.values()).filter(d -> d.difficulty() == DailyTaskDifficulty.HARD).count());
        assertNotNull(DailyTaskDefinition.byId("hard_mlg_water")); assertNotNull(DailyTaskDefinition.byId("simple_pastoral"));
    }
}
