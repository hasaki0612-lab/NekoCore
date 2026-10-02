package land.momo.nekocore.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** 只复制缺失预设，不覆盖管理员文件；提示不额外持久化。 */
public final class FirstRunGuide {
    private FirstRunGuide() {}
    public static final List<String> PRESETS = List.of("survival-only.yml", "lobby-survival.yml", "friends-server.yml");
    public static void installPresets(Path directory, Function<String, InputStream> resource) throws IOException {
        Path presets = directory.resolve("presets"); Files.createDirectories(presets);
        for (String name : PRESETS) {
            Path destination = presets.resolve(name);
            if (Files.exists(destination)) continue;
            try (InputStream input = Objects.requireNonNull(resource.apply("presets/" + name))) {
                try { Files.copy(input, destination); }
                catch (FileAlreadyExistsException ignored) { /* 并发创建也不覆盖。 */ }
            }
        }
    }
    public static void show(boolean generatedConfig, Consumer<String> log) {
        if (!generatedConfig) return;
        log.accept("NekoCore 已经可以直接使用：默认配置能运行基础小型生存服，不必配置所有模块。");
        log.accept("建议先改 branding.server-name；只有主世界不叫 world 时才需改 survival.world。");
        log.accept("进游戏输入 /menu；修改后先 /nekocore config check，再 /nekocore reload。");
        log.accept("场景预设在 plugins/NekoCore/presets/；老服务器请对照修改，不要整份覆盖配置。");
        log.accept("说明见 docs/zh-CN/QUICKSTART.md 和 docs/zh-CN/PRESETS.md。");
    }
}
