package land.momo.nekocore.config;

import land.momo.nekocore.TestDefaults;
import land.momo.nekocore.task.DailyTaskDifficulty;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DailyTaskSettingsTest {
    @Test void defaultsUseBeijingNineTasksConservativeRewardsAndConfiguredRules() throws Exception {
        var settings = DailyTaskSettings.load(TestDefaults.yaml());
        assertTrue(settings.enabled()); assertEquals("Asia/Shanghai", settings.zone().getId());
        for (var difficulty : DailyTaskDifficulty.values()) assertEquals(3, settings.drawCounts().get(difficulty));
        assertEquals(20, settings.reward(DailyTaskDifficulty.EASY).coins());
        assertEquals(35, settings.reward(DailyTaskDifficulty.NORMAL).coins());
        assertEquals(60, settings.reward(DailyTaskDifficulty.HARD).coins());
        assertEquals(1200, settings.target("simple_good_morning")); assertEquals(16, settings.mlgHeight());
        assertTrue(settings.plantItems().stream().anyMatch(material -> material.name().equals("CARROT")));
        assertTrue(settings.flyingTargets().contains(EntityType.GHAST));
        assertFalse(settings.flyingTargets().contains(EntityType.HAPPY_GHAST));
    }

    @Test void happyGhastIsRejectedEvenWhenAnAdministratorAddsIt() throws Exception {
        var yaml = TestDefaults.yaml(); yaml.set("daily-tasks.rules.hard_marksman.entities", java.util.List.of("GHAST", "HAPPY_GHAST"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> DailyTaskSettings.load(yaml));
        assertTrue(error.getMessage().contains("HAPPY_GHAST"));
    }
}
