package land.momo.nekocore.config;

import land.momo.nekocore.service.EnchantmentService;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 检查、启动、重载共用入口；只收集诊断，不修补或切换配置。 */
public final class ConfigurationValidation {
    private ConfigurationValidation() {}
    public record Loaded(Settings settings, FeatureSettings features, DailyTaskSettings dailyTasks, Messages messages) {}
    public static final class Failure extends IllegalArgumentException {
        private final List<ValidationIssue> issues;
        public Failure(List<ValidationIssue> issues) { super(format(issues)); this.issues = List.copyOf(issues); }
        public List<ValidationIssue> issues() { return issues; }
        private static String format(List<ValidationIssue> issues) {
            StringJoiner lines = new StringJoiner("\n\n");
            issues.stream().limit(20).forEach(issue -> lines.add(issue.format()));
            if (issues.size() > 20) lines.add("共有 " + issues.size() + " 项问题，本次显示前 20 项。");
            lines.add("修改完成后执行 /nekocore config check；通过后再 /nekocore reload。");
            return lines.toString();
        }
    }
    public static Loaded load(File config, File messages, InputStream messageDefaults, Consumer<String> warning) throws Exception {
        List<ValidationIssue> issues = new ArrayList<>();
        byte[] defaults;
        try (InputStream stream = Objects.requireNonNull(messageDefaults)) { defaults = stream.readAllBytes(); }
        String configText = Files.readString(config.toPath(), StandardCharsets.UTF_8);
        String messageText = Files.readString(messages.toPath(), StandardCharsets.UTF_8);
        var configIndex = YamlSourceIndex.of(configText);
        var messageIndex = YamlSourceIndex.of(messageText);
        var yaml = parse(configText, config.getName(), configIndex, issues);
        var messageYaml = parse(messageText, messages.getName(), messageIndex, issues);
        Settings settings = null; FeatureSettings features = null; DailyTaskSettings tasks = null; Messages loadedMessages = null;
        if (yaml != null) {
            collectNames(yaml, config.getName(), configIndex, issues);
            try { settings = Settings.load(config, warning); }
            catch (Exception error) { add(issues, issue(config.getName(), yaml, error, configIndex)); }
            try { features = FeatureSettings.load(yaml); }
            catch (Exception error) { add(issues, issue(config.getName(), yaml, error, configIndex)); }
            try { tasks = DailyTaskSettings.load(yaml); }
            catch (Exception error) { add(issues, issue(config.getName(), yaml, error, configIndex)); }
            if (features != null) try { EnchantmentService.validatePool(features.books()); }
            catch (Exception error) { add(issues, ValidationIssue.of(config.getName(), "store.enchantments.pool",
                    yaml.get("store.enchantments.pool"), Objects.toString(error.getMessage(), "附魔池无法使用"), configIndex)); }
        }
        if (messageYaml != null) {
            var fallback = new YamlConfiguration();
            fallback.loadFromString(new String(defaults, StandardCharsets.UTF_8));
            for (String key : fallback.getKeys(true)) {
                if (!messageYaml.contains(key) || fallback.isConfigurationSection(key)) continue;
                if (fallback.isString(key) && !messageYaml.isString(key) || fallback.isList(key) && !messageYaml.isList(key))
                    add(issues, ValidationIssue.of(messages.getName(), key, messageYaml.get(key), "文字应为文本，分行消息应为列表；不要把它写成数字或配置分组。", messageIndex));
            }
            try { loadedMessages = Messages.load(messages, new ByteArrayInputStream(defaults)); }
            catch (Exception error) { add(issues, issue(messages.getName(), messageYaml, error, messageIndex)); }
        }
        if (!issues.isEmpty()) throw new Failure(issues);
        return new Loaded(settings, features, tasks, loadedMessages.globals(Map.of("server", settings.serverName())));
    }
    private static YamlConfiguration parse(String source, String file, YamlSourceIndex index, List<ValidationIssue> issues) {
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(source); return yaml; }
        catch (Exception error) {
            add(issues, ValidationIssue.of(file, "", "无法解析", "YAML 格式错误，请检查缩进、引号和冒号。\n" + error.getMessage(), index));
            return null;
        }
    }
    private static ValidationIssue issue(String file, YamlConfiguration yaml, Exception error, YamlSourceIndex index) {
        String reason = Objects.toString(error.getMessage(), error.getClass().getSimpleName());
        String search = reason.startsWith("messages.yml: ") ? reason.substring("messages.yml: ".length()) : reason;
        var matcher = Pattern.compile("^([a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)+)").matcher(search);
        String path = matcher.find() ? matcher.group(1) : "";
        return ValidationIssue.of(file, path, path.isEmpty() ? "请看问题说明" : yaml.get(path), reason, index);
    }
    private static void add(List<ValidationIssue> issues, ValidationIssue issue) {
        // 列表预检已经逐项解释坏名称，不重复加入加载器的整份列表错误。
        if (issue.current().startsWith("[") && issue.path().matches(".*\\.(blocks|materials|entities)")
                && issues.stream().anyMatch(old -> old.file().equals(issue.file()) && old.path().equals(issue.path()))) return;
        if (issues.stream().noneMatch(old -> old.file().equals(issue.file()) && old.path().equals(issue.path())
                && (issue.path().isEmpty() ? old.problem().equals(issue.problem()) : old.current().equals(issue.current())))) issues.add(issue);
    }
    private static void collectNames(YamlConfiguration yaml, String file, YamlSourceIndex index, List<ValidationIssue> issues) {
        for (String path : yaml.getKeys(true)) {
            if (path.matches("(store\\.(products|categories)|levelshop\\.titles)\\.[a-z0-9_-]+\\.material") && yaml.isString(path)) {
                try { FeatureSettings.material(yaml, path); }
                catch (Exception error) { add(issues, ValidationIssue.of(file, path, yaml.get(path), error.getMessage(), index)); }
            } else if ((path.equals("gui.filler.material") || path.matches("gui\\.items\\.(profile|daily|checkin|privacy|survival|survival-new|minigames|afk-pool)\\.material")) && yaml.isString(path)) {
                try { Settings.material(yaml, path); }
                catch (Exception error) { add(issues, ValidationIssue.of(file, path, yaml.get(path), error.getMessage(), index)); }
            } else if (Set.of("daily-tasks.gui.filler", "daily-tasks.gui.easy-filler", "daily-tasks.gui.normal-filler", "daily-tasks.gui.hard-filler").contains(path) && yaml.isString(path)) {
                try { DailyTaskSettings.material(yaml, path); }
                catch (Exception error) { add(issues, ValidationIssue.of(file, path, yaml.get(path), error.getMessage(), index)); }
            }
        }
        for (String path : List.of("daily-tasks.rules.simple_gardener.blocks", "daily-tasks.rules.simple_pastoral.materials",
                "daily-tasks.rules.normal_harvest.blocks", "daily-tasks.rules.normal_deepslate_worker.blocks",
                "daily-tasks.rules.normal_blacksmith.materials", "daily-tasks.rules.normal_fishing.materials")) {
            for (String value : yaml.getStringList(path)) if (Material.matchMaterial(value) == null)
                add(issues, ValidationIssue.of(file, path, value, "未知 Material：\"" + value + "\"" + Settings.suggestion(value), index));
        }
        String path = "daily-tasks.rules.hard_marksman.entities";
        for (String value : yaml.getStringList(path)) try { EntityType.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) {
            String best = LookupNames.search(value, Arrays.stream(EntityType.values()).map(Enum::name).toList(), 1).stream().findFirst().orElse("");
            add(issues, ValidationIssue.of(file, path, value, "未知 EntityType：\"" + value + "\"" +
                    (best.isEmpty() ? "" : "；你是否想填 \"" + best + "\"？"), index));
        }
    }
}
