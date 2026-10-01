package land.momo.nekocore.task;

import org.bukkit.Material;

import java.util.*;

/** Stable task IDs. Display names live in messages.yml and may change independently. */
public enum DailyTaskDefinition {
    SIMPLE_GARDENER("simple_gardener", DailyTaskDifficulty.EASY, Material.OAK_LEAVES),
    SIMPLE_HEALTHY("simple_healthy", DailyTaskDifficulty.EASY, Material.APPLE),
    SIMPLE_SHEAR_SHEEP("simple_shear_sheep", DailyTaskDifficulty.EASY, Material.SHEARS),
    SIMPLE_GOOD_MORNING("simple_good_morning", DailyTaskDifficulty.EASY, Material.CLOCK),
    SIMPLE_CRAFTING("simple_crafting", DailyTaskDifficulty.EASY, Material.CRAFTING_TABLE),
    SIMPLE_EAT_FOOD("simple_eat_food", DailyTaskDifficulty.EASY, Material.COOKED_BEEF),
    SIMPLE_PASTORAL("simple_pastoral", DailyTaskDifficulty.EASY, Material.WHEAT_SEEDS),

    NORMAL_HARVEST("normal_harvest", DailyTaskDifficulty.NORMAL, Material.WHEAT),
    NORMAL_DESSERT("normal_dessert", DailyTaskDifficulty.NORMAL, Material.CAKE),
    NORMAL_TAX_COLLECTOR("normal_tax_collector", DailyTaskDifficulty.NORMAL, Material.EMERALD),
    NORMAL_CLEANUP("normal_cleanup", DailyTaskDifficulty.NORMAL, Material.IRON_SWORD),
    NORMAL_DEEPSLATE_WORKER("normal_deepslate_worker", DailyTaskDifficulty.NORMAL, Material.DEEPSLATE_DIAMOND_ORE),
    NORMAL_SMELT("normal_smelt", DailyTaskDifficulty.NORMAL, Material.FURNACE),
    NORMAL_BLACKSMITH("normal_blacksmith", DailyTaskDifficulty.NORMAL, Material.IRON_PICKAXE),
    NORMAL_FISHING("normal_fishing", DailyTaskDifficulty.NORMAL, Material.FISHING_ROD),

    HARD_ENCHANT("hard_enchant", DailyTaskDifficulty.HARD, Material.ENCHANTING_TABLE),
    HARD_TYCOON("hard_tycoon", DailyTaskDifficulty.HARD, Material.EMERALD_BLOCK),
    HARD_MARKSMAN("hard_marksman", DailyTaskDifficulty.HARD, Material.BOW),
    HARD_MLG_WATER("hard_mlg_water", DailyTaskDifficulty.HARD, Material.WATER_BUCKET),
    HARD_IRON_GOLEM("hard_iron_golem", DailyTaskDifficulty.HARD, Material.IRON_BLOCK);

    private static final Map<String, DailyTaskDefinition> BY_ID;
    static {
        Map<String, DailyTaskDefinition> values = new LinkedHashMap<>();
        for (DailyTaskDefinition definition : DailyTaskDefinition.values()) values.put(definition.id, definition);
        BY_ID = Map.copyOf(values);
    }

    private final String id;
    private final DailyTaskDifficulty difficulty;
    private final Material icon;
    DailyTaskDefinition(String id, DailyTaskDifficulty difficulty, Material icon) {
        this.id = id; this.difficulty = difficulty; this.icon = icon;
    }
    public String id() { return id; }
    public DailyTaskDifficulty difficulty() { return difficulty; }
    public Material icon() { return icon; }
    public static DailyTaskDefinition byId(String id) { return BY_ID.get(id); }
    public static Set<String> ids() { return BY_ID.keySet(); }
}
