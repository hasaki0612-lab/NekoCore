package land.momo.nekocore.config;

import land.momo.nekocore.task.*;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.*;

/** Validated configuration for the event-driven global daily-task rotation. */
public record DailyTaskSettings(boolean enabled, ZoneId zone,
                                Map<DailyTaskDifficulty, Integer> drawCounts,
                                Map<DailyTaskDifficulty, Reward> rewards,
                                Map<DailyTaskDifficulty, List<String>> pools,
                                Map<String, Integer> targets,
                                Set<Material> gardenerBlocks, Set<Material> plantItems,
                                Set<Material> matureCrops, Set<Material> deepslateOres,
                                Set<Material> ironTools, Set<Material> fish,
                                Set<EntityType> flyingTargets, int mlgHeight,
                                Gui gui, Feedback feedback) {
    public record Reward(long coins, long exp) {}
    public record Gui(List<Integer> easySlots, List<Integer> normalSlots, List<Integer> hardSlots,
                      int backSlot, int infoSlot, int closeSlot,
                      Material filler, Material easyFiller, Material normalFiller, Material hardFiller) {
        public Gui {
            easySlots = List.copyOf(easySlots); normalSlots = List.copyOf(normalSlots); hardSlots = List.copyOf(hardSlots);
        }
        public List<Integer> slots(DailyTaskDifficulty difficulty) {
            return switch (difficulty) { case EASY -> easySlots; case NORMAL -> normalSlots; case HARD -> hardSlots; };
        }
    }
    public record Feedback(String sound, float volume, float pitch, int particles) {}

    public DailyTaskSettings {
        drawCounts = Map.copyOf(drawCounts); rewards = Map.copyOf(rewards);
        Map<DailyTaskDifficulty,List<String>> copy = new EnumMap<>(DailyTaskDifficulty.class);
        pools.forEach((key, value) -> copy.put(key, List.copyOf(value))); pools = Map.copyOf(copy);
        targets = Map.copyOf(targets); gardenerBlocks = Set.copyOf(gardenerBlocks); plantItems = Set.copyOf(plantItems);
        matureCrops = Set.copyOf(matureCrops); deepslateOres = Set.copyOf(deepslateOres);
        ironTools = Set.copyOf(ironTools); fish = Set.copyOf(fish); flyingTargets = Set.copyOf(flyingTargets);
    }

    public int target(String id) {
        Integer value = targets.get(id);
        if (value == null) throw new IllegalArgumentException("任务没有配置目标值：" + id);
        return value;
    }
    public Reward reward(DailyTaskDifficulty difficulty) { return rewards.get(difficulty); }

    public static DailyTaskSettings load(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file); return load(yaml);
    }

    public static DailyTaskSettings load(ConfigurationSection c) {
        boolean enabled = bool(c, "daily-tasks.enabled");
        ZoneId zone = ZoneId.of(string(c, "daily-tasks.timezone"));
        Map<DailyTaskDifficulty,Integer> draws = new EnumMap<>(DailyTaskDifficulty.class);
        Map<DailyTaskDifficulty,Reward> rewards = new EnumMap<>(DailyTaskDifficulty.class);
        Map<DailyTaskDifficulty,List<String>> pools = new EnumMap<>(DailyTaskDifficulty.class);
        Set<String> allPoolIds = new HashSet<>();
        for (DailyTaskDifficulty difficulty : DailyTaskDifficulty.values()) {
            String root = "daily-tasks.";
            int count = integer(c, root + "draw-count." + difficulty.key(), 1, 9);
            draws.put(difficulty, count);
            rewards.put(difficulty, new Reward(number(c, root + "rewards." + difficulty.key() + ".coins", 0, Long.MAX_VALUE / 4),
                    number(c, root + "rewards." + difficulty.key() + ".exp", 0, Long.MAX_VALUE / 4)));
            List<String> pool = strings(c, root + "pools." + difficulty.key());
            if (pool.size() < count || new HashSet<>(pool).size() != pool.size()) bad(root + "pools." + difficulty.key(), "题库不可重复且数量必须足够抽取");
            for (String id : pool) {
                DailyTaskDefinition definition = DailyTaskDefinition.byId(id);
                if (definition == null || definition.difficulty() != difficulty) bad(root + "pools." + difficulty.key(), "任务 ID 不存在或难度不符：" + id);
                if (!allPoolIds.add(id)) bad(root + "pools", "任务 ID 跨题库重复：" + id);
            }
            pools.put(difficulty, pool);
        }
        Map<String,Integer> targets = new HashMap<>();
        for (DailyTaskDefinition definition : DailyTaskDefinition.values()) {
            String id = definition.id();
            String key = id.equals("simple_good_morning") ? "target-minutes" : "target";
            int value = integer(c, "daily-tasks.rules." + id + "." + key, 1, 1_000_000);
            targets.put(id, id.equals("simple_good_morning") ? Math.multiplyExact(value, 60) : value);
        }
        Set<Material> gardener = materials(c, "daily-tasks.rules.simple_gardener.blocks");
        Set<Material> plants = materials(c, "daily-tasks.rules.simple_pastoral.materials");
        Set<Material> crops = materials(c, "daily-tasks.rules.normal_harvest.blocks");
        Set<Material> ores = materials(c, "daily-tasks.rules.normal_deepslate_worker.blocks");
        Set<Material> tools = materials(c, "daily-tasks.rules.normal_blacksmith.materials");
        Set<Material> fish = materials(c, "daily-tasks.rules.normal_fishing.materials");
        Set<EntityType> flying = entities(c, "daily-tasks.rules.hard_marksman.entities");
        if (flying.contains(EntityType.HAPPY_GHAST)) bad("daily-tasks.rules.hard_marksman.entities", "HAPPY_GHAST（乐魂）永远不能成为任务目标");
        Gui gui = new Gui(slots(c, "daily-tasks.gui.slots.easy"), slots(c, "daily-tasks.gui.slots.normal"),
                slots(c, "daily-tasks.gui.slots.hard"), integer(c, "daily-tasks.gui.slots.back", 0, 44),
                integer(c, "daily-tasks.gui.slots.info", 0, 44), integer(c, "daily-tasks.gui.slots.close", 0, 44),
                material(c, "daily-tasks.gui.filler"), material(c, "daily-tasks.gui.easy-filler"),
                material(c, "daily-tasks.gui.normal-filler"), material(c, "daily-tasks.gui.hard-filler"));
        Set<Integer> guiSlots = new HashSet<>();
        for (DailyTaskDifficulty difficulty : DailyTaskDifficulty.values()) {
            List<Integer> slots = gui.slots(difficulty);
            if (slots.size() != draws.get(difficulty)) bad("daily-tasks.gui.slots." + difficulty.key(), "槽位数量必须等于抽取数量");
            for (int slot : slots) if (!guiSlots.add(slot)) bad("daily-tasks.gui.slots", "槽位不可重复：" + slot);
        }
        for (int slot : List.of(gui.backSlot(), gui.infoSlot(), gui.closeSlot())) if (!guiSlots.add(slot)) bad("daily-tasks.gui.slots", "槽位不可重复：" + slot);
        String sound = string(c, "daily-tasks.feedback.sound.name");
        if (!sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) bad("daily-tasks.feedback.sound.name", "音效需使用命名空间");
        Feedback feedback = new Feedback(sound, decimal(c, "daily-tasks.feedback.sound.volume", 0, 2),
                decimal(c, "daily-tasks.feedback.sound.pitch", 0.5f, 2), integer(c, "daily-tasks.feedback.particle-count", 0, 40));
        int mlgHeight = integer(c, "daily-tasks.rules.hard_mlg_water.minimum-height", 1, 1_000_000);
        return new DailyTaskSettings(enabled, zone, draws, rewards, pools, targets, gardener, plants, crops, ores, tools, fish,
                flying, mlgHeight, gui, feedback);
    }

    private static List<Integer> slots(ConfigurationSection c, String path) {
        if (!c.isList(path)) bad(path, "必须是槽位列表");
        List<Integer> values = new ArrayList<>();
        for (Object value : Objects.requireNonNull(c.getList(path))) {
            if (!(value instanceof Number) || ((Number) value).intValue() < 0 || ((Number) value).intValue() > 44) bad(path, "槽位范围为 0..44");
            Number number = (Number) value;
            values.add(number.intValue());
        }
        return List.copyOf(values);
    }
    private static List<String> strings(ConfigurationSection c, String path) {
        if (!c.isList(path) || Objects.requireNonNull(c.getList(path)).stream().anyMatch(value -> !(value instanceof String))) bad(path, "必须是文本列表");
        return List.copyOf(c.getStringList(path));
    }
    private static Set<Material> materials(ConfigurationSection c, String path) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String value : strings(c, path)) {
            Material material = Material.matchMaterial(value);
            if (material == null) bad(path, "未知 Material：" + value);
            result.add(material);
        }
        if (result.isEmpty()) bad(path, "列表不能为空");
        return result;
    }
    private static Set<EntityType> entities(ConfigurationSection c, String path) {
        Set<EntityType> result = EnumSet.noneOf(EntityType.class);
        for (String value : strings(c, path)) try { result.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { bad(path, "未知 EntityType：" + value); }
        if (result.isEmpty()) bad(path, "列表不能为空");
        return result;
    }
    static Material material(ConfigurationSection c, String path) {
        Material material = Material.matchMaterial(string(c, path));
        // Material#isItem consults Paper's live registry in 26.2. Configuration
        // parsing also runs in migration tools and unit tests where that registry
        // deliberately does not exist, so keep validation independent of a
        // running server. ItemStack creation remains the final runtime guard.
        if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)
            bad(path, "不是有效物品");
        return material;
    }
    private static String string(ConfigurationSection c, String path) {
        if (!c.isString(path)) bad(path, "必须是文本"); return c.getString(path);
    }
    private static boolean bool(ConfigurationSection c, String path) {
        if (!c.isBoolean(path)) bad(path, "必须是 true/false"); return c.getBoolean(path);
    }
    private static int integer(ConfigurationSection c, String path, int min, int max) {
        long value = number(c, path, min, max); return (int) value;
    }
    private static long number(ConfigurationSection c, String path, long min, long max) {
        Object value = c.get(path);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < min || ((Number) value).longValue() > max)
            bad(path, "整数范围为 " + min + ".." + max);
        return ((Number) value).longValue();
    }
    private static float decimal(ConfigurationSection c, String path, float min, float max) {
        Object value = c.get(path);
        if (!(value instanceof Number) || !Float.isFinite(((Number) value).floatValue())
                || ((Number) value).floatValue() < min || ((Number) value).floatValue() > max) bad(path, "数值范围无效");
        return ((Number) value).floatValue();
    }
    private static void bad(String path, String message) { throw new IllegalArgumentException(path + ": " + message); }
}
