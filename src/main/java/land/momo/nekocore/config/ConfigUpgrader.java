package land.momo.nekocore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned merge with byte-for-byte backups; database files are never touched. */
public final class ConfigUpgrader {
    private ConfigUpgrader() {}
    public static void upgrade(Path directory) throws Exception {
        upgrade(directory, ignored -> {});
    }
    public static void upgrade(Path directory, java.util.function.Consumer<String> log) throws Exception {
        for (String name : List.of("config", "messages")) {
            Path file = directory.resolve(name + ".yml");
            YamlConfiguration current = new YamlConfiguration(); current.load(file.toFile());
            String versionKey = name + "-version";
            int targetVersion = name.equals("config") ? 9 : 8;
            int version = current.getInt(versionKey, 1);
            if (version > targetVersion) throw new IllegalArgumentException(name + ".yml 版本高于当前插件支持的版本");
            if (version == targetVersion) continue;
            YamlConfiguration old = resource("/migration/v" + Math.max(1, version) + "-" + name + ".yml");
            YamlConfiguration next = resource("/" + name + ".yml");
            Set<String> pinned = new HashSet<>();
            if (name.equals("config")) {
                List<String> oldActions = List.of("profile", "daily", "survival", "privacy", "checkin", "survival-new", "minigames");
                for (String action : oldActions) {
                    String path = "gui.items." + action + ".slot";
                    if (current.contains(path) && (version >= 4 || !Objects.equals(current.get(path), old.get(path)))) pinned.add(action);
                }
            }
            for (String key : next.getKeys(true)) {
                if (next.isConfigurationSection(key)) continue;
                Object value = current.get(key);
                if (value == null || Objects.equals(value, old.get(key))) current.set(key, next.get(key));
                else if (version <= 1 && (name.equals("messages") || key.startsWith("gui."))) current.set(key, recolor(value));
            }
            if (name.equals("config")) {
                if (version <= 1 || version == 4) arrangeNewButtons(current, pinned);
                // Only the explicit destination token changes; custom command names/options survive.
                if (version < 3) for (String key : List.of("survival.command", "survival-new.command")) {
                    if (current.isString(key)) current.set(key, current.getString(key)
                            .replaceAll("(?<=\\s)ll:\\{world\\}(?=\\s|$)", "{world}"));
                }
            }
            current.set(versionKey, targetVersion);
            Path backup = directory.resolve(name + ".yml.pre-public-1.1.0-" + UUID.randomUUID() + ".bak");
            Files.copy(file, backup);
            Path temporary = Files.createTempFile(directory, name + "-upgrade-", ".yml");
            try {
                current.save(temporary.toFile());
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            log.accept(name + ".yml 已从版本 " + version + " 升级到 " + targetVersion + "；原文件备份：" + backup.getFileName());
        }
    }

    private static void arrangeNewButtons(YamlConfiguration yaml, Set<String> pinned) {
        int size = yaml.getInt("gui.rows") * 9;
        if (size < 9 || size > 54) return; // Settings reports invalid user configuration.
        Set<Integer> used = new HashSet<>();
        for (String action : pinned) used.add(yaml.getInt("gui.items." + action + ".slot"));
        for (String action : List.of("profile", "daily", "survival", "privacy", "checkin", "survival-new", "minigames", "afk-pool")) {
            if (pinned.contains(action)) continue;
            String key = "gui.items." + action + ".slot";
            int slot = yaml.getInt(key);
            if (slot < 0 || slot >= size || used.contains(slot)) {
                slot = 0;
                while (slot < size && used.contains(slot)) slot++;
                if (slot == size) { size += 9; yaml.set("gui.rows", size / 9); }
                yaml.set(key, slot);
            }
            used.add(slot);
        }
    }

    private static Object recolor(Object value) {
        if (value instanceof List<?> list) return list.stream().map(ConfigUpgrader::recolor).toList();
        if (!(value instanceof String text)) return value;
        return text.replaceAll("(?i)&[ad235]", "&#9FD9F6")
                .replaceAll("(?i)&b", "&#C5E9FA").replaceAll("(?i)&[ce64]", "&#FFE49A")
                .replaceAll("(?i)&[78]", "&#B2C3CF");
    }
    private static YamlConfiguration resource(String name) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(ConfigUpgrader.class.getResourceAsStream(name)), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }
}
