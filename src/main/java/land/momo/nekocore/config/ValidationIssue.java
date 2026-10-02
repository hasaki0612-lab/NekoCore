package land.momo.nekocore.config;

import java.util.*;
import java.util.regex.*;

/** 面向服主的错误信息，保留原验证原因和相近名称建议。 */
public record ValidationIssue(String file, String path, String purpose, String current,
                              String problem, String recommendation, OptionalInt line) {
    public static ValidationIssue of(String file, String path, Object value, String problem,
                                     YamlSourceIndex index) {
        Matcher suggestion = Pattern.compile("是否想填 \"([^\"]+)\"").matcher(problem);
        String recommended = suggestion.find() ? suggestion.group(1) : "";
        return new ValidationIssue(file, path, purpose(path), Objects.toString(value, "（缺失）"),
                problem.replace("Material", "Material（Minecraft 物品/方块类型）")
                       .replace("EntityType", "EntityType（Minecraft 生物类型）"),
                recommended, index.line(path));
    }
    public static String purpose(String path) {
        if (path.startsWith("store.products.")) {
            String[] pieces = path.split("\\.");
            return "商店商品「" + (pieces.length > 2 ? pieces[2] : "") + "」的" + label(path);
        }
        if (path.startsWith("store.categories.")) return "商店分类图标或布局";
        if (path.startsWith("levelshop.titles.")) return "头衔外观、价格或权益";
        if (path.startsWith("daily-tasks.rules.")) return "每日任务目标或允许的物品 / 生物";
        if (path.startsWith("daily-tasks.")) return "每日任务奖励、题库或菜单";
        if (path.startsWith("branding.")) return "服务器显示名字";
        if (path.startsWith("checkin.")) return "每日签到奖励或提示";
        if (path.startsWith("gui.")) return "服务器面板的按钮、图标或布局";
        if (path.startsWith("afk-pool.")) return "挂机池开关、位置或奖励";
        if (path.startsWith("weekly-coin-leaderboard.")) return "金币周榜开关、位置或朝向";
        if (path.startsWith("join-info.")) return "进服个人信息和网址入口";
        if (path.startsWith("survival")) return "已加载世界的入口";
        return path.isEmpty() ? "配置文件格式" : "配置项「" + path + "」";
    }
    private static String label(String path) {
        if (path.endsWith(".material")) return "物品类型";
        if (path.endsWith(".price")) return "价格";
        if (path.endsWith(".slot")) return "菜单位置";
        return "设置";
    }
    public String format() {
        StringBuilder text = new StringBuilder("[配置错误] ").append(purpose).append("需要修改\n")
                .append("  文件：").append(file).append("\n  配置项：")
                .append(path.isEmpty() ? "（请检查 YAML 缩进和格式）" : path).append('\n');
        if (line.isPresent()) text.append("  位置：第 ").append(line.getAsInt()).append(" 行\n");
        else text.append("  附近内容：").append(purpose).append("（无法可靠定位行号，未猜测）\n");
        text.append("  当前值：").append(current).append("\n  问题：").append(problem).append('\n');
        if (!recommendation.isEmpty()) {
            String leaf = path.substring(path.lastIndexOf('.') + 1);
            text.append("  建议值：").append(recommendation).append('\n');
            if (Set.of("blocks", "materials", "entities").contains(leaf))
                text.append("  只把列表中的 ").append(current).append(" 换成 ").append(recommendation).append("，保留其他正确项。\n");
            else text.append("  直接把：\n    ").append(leaf).append(": ").append(current)
                    .append("\n  改成：\n    ").append(leaf).append(": ").append(recommendation).append('\n');
        }
        if (problem.contains("Material") || problem.contains("EntityType"))
            text.append("  不知道英文名？拿着物品执行 /nekocore lookup；生物用 /nekocore lookup entity <英文名>。\n");
        return text.toString().stripTrailing();
    }
}
