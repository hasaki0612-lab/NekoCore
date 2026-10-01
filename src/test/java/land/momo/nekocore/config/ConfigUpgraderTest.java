package land.momo.nekocore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigUpgraderTest {
    @TempDir Path directory;
    private void copyOld() throws Exception {
        for (String name : List.of("config", "messages")) {
            try (var in = getClass().getResourceAsStream("/migration/v1-" + name + ".yml")) {
                Files.copy(in, directory.resolve(name + ".yml"));
            }
        }
    }
    private YamlConfiguration read(String name) throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(directory.resolve(name + ".yml").toFile()); return yaml;
    }
    @Test void defaultUpgradeChangesThemeAndAddsSevenUniqueButtonsWithBackup() throws Exception {
        copyOld(); String original = Files.readString(directory.resolve("messages.yml"));
        ConfigUpgrader.upgrade(directory);
        var config = read("config"); var messages = read("messages");
        assertEquals(8, config.getInt("config-version")); assertEquals(7, messages.getInt("messages-version")); assertEquals(5, config.getInt("gui.rows"));
        assertEquals(300, config.getInt("tips.interval-seconds")); assertEquals(100, config.getInt("checkin.coins"));
        assertEquals(50, config.getInt("checkin.exp")); assertEquals("world_secondary", config.getString("survival-new.world"));
        assertEquals(0.5, config.getDouble("minigames.x")); assertEquals(64, config.getDouble("minigames.y"));
        assertEquals(0.5, config.getDouble("minigames.z")); assertTrue(config.getBoolean("chat.level-prefix-enabled"));
        assertTrue(config.getString("gui.title").contains("&#9FD9F6服务器面板"));
        assertEquals("&#9FD9F6[NekoCore] &r", messages.getString("prefix"));
        assertTrue(messages.getStringList("profile").getFirst().startsWith("&#9FD9F6——"));
        Set<Integer> slots = new HashSet<>();
        for (String action : config.getConfigurationSection("gui.items").getKeys(false)) assertTrue(slots.add(config.getInt("gui.items." + action + ".slot")));
        assertEquals(8, slots.size());
        try (var files = Files.list(directory)) {
            Path backup = files.filter(p -> p.getFileName().toString().startsWith("messages.yml.pre-")).findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
        String upgraded = Files.readString(directory.resolve("config.yml"));
        ConfigUpgrader.upgrade(directory);
        assertEquals(upgraded, Files.readString(directory.resolve("config.yml"))); // idempotent
    }
    @Test void customGameplayValuesAndTextSurviveWhileNewSlotsAvoidCollisions() throws Exception {
        copyOld(); var yaml = read("config");
        yaml.set("cleanup.interval-seconds", 1200); yaml.set("database.filename", "momoland.db");
        yaml.set("survival.world", "old_survival"); yaml.set("gui.rows", 2);
        yaml.set("gui.items.profile.slot", 1); yaml.set("gui.items.daily.slot", 3);
        yaml.set("gui.items.survival.slot", 5); yaml.set("gui.items.privacy.slot", 7);
        yaml.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("home-success", "&a欢迎回小窝喵！"); messages.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory); var after = read("config");
        assertEquals(1200, after.getInt("cleanup.interval-seconds")); assertEquals("momoland.db", after.getString("database.filename"));
        assertEquals("old_survival", after.getString("survival.world")); assertEquals(1, after.getInt("gui.items.profile.slot"));
        Set<Integer> slots = new HashSet<>();
        for (String action : after.getConfigurationSection("gui.items").getKeys(false)) {
            int slot = after.getInt("gui.items." + action + ".slot");
            assertTrue(slot >= 0 && slot < after.getInt("gui.rows") * 9); assertTrue(slots.add(slot));
        }
        assertEquals("&#9FD9F6欢迎回小窝喵！", read("messages").getString("home-success"));
    }
    @Test void invalidYamlIsNotOverwritten() throws Exception {
        copyOld(); String broken = "config: [unterminated";
        Files.writeString(directory.resolve("config.yml"), broken);
        assertThrows(Exception.class, () -> ConfigUpgrader.upgrade(directory));
        assertEquals(broken, Files.readString(directory.resolve("config.yml")));
    }

    private void copyV2() throws Exception {
        for (String name : List.of("config", "messages")) try (var in = getClass().getResourceAsStream("/migration/v2-" + name + ".yml")) {
            Files.copy(in, directory.resolve(name + ".yml"));
        }
    }
    @Test void upgrade111AddsFeaturesPreservesCustomValuesAndBacksUpOnce() throws Exception {
        for (String name : List.of("config", "messages")) try (var in = getClass().getResourceAsStream("/migration/v3-" + name + ".yml")) {
            Files.copy(in, directory.resolve(name + ".yml"));
        }
        var config = read("config"); config.set("checkin.coins", 137); config.set("home.bed-auto-set.enabled", false);
        config.set("database.filename", "existing.db"); config.set("tips.messages", List.of("custom"));
        config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("prefix", "&#AABBCC自定义 "); messages.save(directory.resolve("messages.yml").toFile());
        byte[] original = Files.readAllBytes(directory.resolve("config.yml")); List<String> logs = new ArrayList<>();
        ConfigUpgrader.upgrade(directory, logs::add);
        var next = read("config"); assertEquals(8, next.getInt("config-version"));
        assertEquals(137, next.getInt("checkin.coins")); assertFalse(next.getBoolean("home.bed-auto-set.enabled"));
        assertEquals("existing.db", next.getString("database.filename")); assertEquals(List.of("custom"), next.getStringList("tips.messages"));
        assertEquals(List.of("world"), next.getStringList("bag.writable-worlds"));
        assertEquals(20000, next.getInt("levelshop.titles.sora.price")); assertTrue(next.contains("store.products.diamond_chestplate"));
        assertEquals("&#AABBCC自定义 ", read("messages").getString("prefix")); assertEquals(2, logs.size());
        try (var files = Files.list(directory)) {
            assertArrayEquals(original, Files.readAllBytes(files.filter(p -> p.getFileName().toString().startsWith("config.yml.pre-")).findFirst().orElseThrow()));
        }
        byte[] upgraded = Files.readAllBytes(directory.resolve("config.yml")); ConfigUpgrader.upgrade(directory, logs::add);
        assertArrayEquals(upgraded, Files.readAllBytes(directory.resolve("config.yml"))); assertEquals(2, logs.size());
    }
    @Test void upgrade110BacksUpBothFilesAndChangesBothEntrancesWithoutTouchingDatabase() throws Exception {
        copyV2(); byte[] database = {1, 8, 7, 6}; Files.write(directory.resolve("nekocore.db"), database);
        Map<String, byte[]> originals = new HashMap<>();
        for (String name : List.of("config", "messages")) originals.put(name, Files.readAllBytes(directory.resolve(name + ".yml")));
        ConfigUpgrader.upgrade(directory);
        var config = read("config");
        assertEquals(8, config.getInt("config-version")); assertEquals(7, read("messages").getInt("messages-version"));
        for (String key : List.of("survival.command", "survival-new.command")) assertEquals("mvtp {player} {world}", config.getString(key));
        assertTrue(config.getBoolean("home.bed-auto-set.enabled"));
        assertEquals(List.of("world"), config.getStringList("home.bed-auto-set.worlds"));
        for (String key : List.of("survival", "survival-new")) assertTrue(config.getStringList("gui.items." + key + ".lore").stream().anyMatch(s -> s.contains("公共出生点")));
        assertTrue(read("messages").getString("usage.home").contains("<worldName>"));
        assertTrue(read("messages").contains("home-bed-saved"));
        for (String name : originals.keySet()) try (var files = Files.list(directory)) {
            Path backup = files.filter(p -> p.getFileName().toString().startsWith(name + ".yml.pre-1.4.0-")).findFirst().orElseThrow();
            assertArrayEquals(originals.get(name), Files.readAllBytes(backup));
        }
        assertArrayEquals(database, Files.readAllBytes(directory.resolve("nekocore.db")));
        byte[] once = Files.readAllBytes(directory.resolve("config.yml"));
        ConfigUpgrader.upgrade(directory);
        assertArrayEquals(once, Files.readAllBytes(directory.resolve("config.yml")));
        try (var files = Files.list(directory)) { assertEquals(2, files.filter(p -> p.toString().endsWith(".bak")).count()); }
    }
    @Test void upgrade110PreservesCustomSettingsMessagesAndSlotsAndOnlyChangesLlDestinationToken() throws Exception {
        copyV2(); var config = read("config");
        config.set("database.filename", "custom.db"); config.set("cleanup.interval-seconds", 1200);
        config.set("checkin.coins", 37); config.set("tips.messages", List.of("custom tip"));
        config.set("survival.command", "multiverse-core:mvtp {player} ll:{world} --unsafe");
        config.set("survival-new.command", "customtp {player} {world}");
        config.set("gui.items.survival.lore", List.of("自定义说明")); config.set("gui.items.survival.slot", 40);
        config.set("home.bed-auto-set.enabled", false); config.set("home.bed-auto-set.worlds", List.of("only_this"));
        config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("prefix", "&#123456小窝 "); messages.set("home-success", "&a自定义颜色也不变");
        messages.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory); var after = read("config");
        for (String key : List.of("database.filename", "cleanup.interval-seconds", "checkin.coins", "tips.messages",
                "survival-new.command", "gui.items.survival.lore", "gui.items.survival.slot", "home.bed-auto-set.enabled", "home.bed-auto-set.worlds"))
            assertEquals(config.get(key), after.get(key), key);
        assertEquals("multiverse-core:mvtp {player} {world} --unsafe", after.getString("survival.command"));
        assertEquals(messages.getString("prefix"), read("messages").getString("prefix"));
        assertEquals(messages.getString("home-success"), read("messages").getString("home-success"));
    }

    @Test void upgrade120To121KeepsCustomValuesStableIdsAndAddsAfkGeoAndProducts() throws Exception {
        for (String name : List.of("config", "messages")) try (var in = getClass().getResourceAsStream("/" + name + ".yml")) {
            Files.copy(in, directory.resolve(name + ".yml"));
        }
        var config = read("config"); config.set("config-version", 4);
        config.set("location-prefix", null); config.set("afk-pool", null); config.set("gui.items.afk-pool", null);
        config.set("store.products.diamond_chestplate", null); config.set("cleanup.interval-seconds", 1337);
        config.set("gui.items.minigames.slot", 34);
        config.set("gui.items.privacy.name", "自定义隐私按钮");
        config.set("levelshop.titles.mame.name", "Mame"); config.set("levelshop.titles.mame.prefix", "&#B6E3D0[Mame] &r");
        config.set("levelshop.titles.mame.material", "WHEAT_SEEDS"); config.set("levelshop.titles.mame.price", 1500);
        config.set("levelshop.titles.mame.description", List.of("&f一颗小小的豆子", "&#B2C3CF上线自动签到", "&#B2C3CF传送冷却 120 秒"));
        config.set("levelshop.titles.mame.afk-exp-multiplier", null);
        config.set("levelshop.titles.momo.price", 9999); // Explicit admin customization must survive.
        config.set("levelshop.titles.momo.afk-exp-multiplier", null);
        config.set("levelshop.titles.sora.name", "Sora"); config.set("levelshop.titles.sora.prefix", "&#CBBFEF[Sora] &r");
        config.set("levelshop.titles.sora.material", "AMETHYST_SHARD"); config.set("levelshop.titles.sora.price", 12000);
        config.set("levelshop.titles.sora.description", List.of("&f把旅程写进天空", "&#B2C3CF自动签到 · 安全聊天颜色", "&#B2C3CF传送冷却 30 秒"));
        config.set("levelshop.titles.sora.afk-exp-multiplier", null); config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("messages-version", 4); messages.set("home-success", "&#123456自定义欢迎");
        messages.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory); var next = read("config");
        assertEquals(8, next.getInt("config-version")); assertEquals(7, read("messages").getInt("messages-version"));
        assertEquals(1337, next.getInt("cleanup.interval-seconds")); assertEquals("自定义隐私按钮", next.getString("gui.items.privacy.name"));
        assertEquals(34, next.getInt("gui.items.minigames.slot")); assertNotEquals(34, next.getInt("gui.items.afk-pool.slot"));
        assertEquals(List.of("mame", "momo", "sora"), new ArrayList<>(next.getConfigurationSection("levelshop.titles").getKeys(false)));
        assertEquals("Yuki", next.getString("levelshop.titles.mame.name")); assertEquals(2500, next.getInt("levelshop.titles.mame.price"));
        assertEquals(9999, next.getInt("levelshop.titles.momo.price")); assertEquals("Neko", next.getString("levelshop.titles.sora.name"));
        assertEquals(1.20, next.getDouble("levelshop.titles.sora.afk-exp-multiplier"));
        assertTrue(next.contains("location-prefix.database-file")); assertTrue(next.contains("afk-pool.reward.interval-seconds"));
        assertEquals(4800, next.getInt("store.products.diamond_chestplate.price"));
        assertEquals("&#123456自定义欢迎", read("messages").getString("home-success"));
        try (var files = Files.list(directory)) {
            assertEquals(2, files.filter(path -> path.getFileName().toString().contains(".pre-1.4.0-")).count());
        }
    }

    @Test void upgrade121To130AddsTasksAndTabWhilePreservingOperationalCoinRangeAndCustomText() throws Exception {
        for (String name : List.of("config", "messages")) try (var in = getClass().getResourceAsStream("/" + name + ".yml")) {
            Files.copy(in, directory.resolve(name + ".yml"));
        }
        var config = read("config"); config.set("config-version", 5); config.set("daily-tasks", null); config.set("tab", null);
        config.set("afk-pool.title.stay-ticks", null); config.set("afk-pool.title.fade-out-ticks", null);
        config.set("afk-pool.reward.coin-min", 1); config.set("afk-pool.reward.coin-max", 4);
        config.set("tips.messages", List.of("管理员自己的提示")); config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("messages-version", 5); messages.set("title-menu-title", "&#123456自定义标题");
        messages.set("tab", null); messages.set("daily-tasks", null); messages.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory); var next = read("config"); var nextMessages = read("messages");
        assertEquals(8, next.getInt("config-version")); assertEquals(7, nextMessages.getInt("messages-version"));
        assertEquals(1, next.getInt("afk-pool.reward.coin-min")); assertEquals(4, next.getInt("afk-pool.reward.coin-max"));
        assertEquals(List.of("管理员自己的提示"), next.getStringList("tips.messages"));
        assertTrue(next.getBoolean("daily-tasks.enabled")); assertTrue(next.getBoolean("tab.enabled"));
        assertEquals("&#123456自定义标题", nextMessages.getString("title-menu-title"));
        assertTrue(nextMessages.contains("daily-tasks.completion-messages"));
    }

    @Test void stock121AfkRangeMigratesFromFourToEightIntoConfirmedOneToFour() throws Exception {
        for (String name : List.of("config", "messages")) try (var in = getClass().getResourceAsStream("/" + name + ".yml")) {
            Files.copy(in, directory.resolve(name + ".yml"));
        }
        var config = read("config"); config.set("config-version", 5);
        config.set("afk-pool.reward.coin-min", 4); config.set("afk-pool.reward.coin-max", 8);
        config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("messages-version", 5); messages.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory); var next = read("config");
        assertEquals(1, next.getInt("afk-pool.reward.coin-min")); assertEquals(4, next.getInt("afk-pool.reward.coin-max"));
    }

    @Test void upgrade131To140AddsNewModulesUpdatesOldDefaultsAndPreservesCustomSettings() throws Exception {
        try (var in = getClass().getResourceAsStream("/migration/v7-config.yml")) {
            Files.copy(Objects.requireNonNull(in), directory.resolve("config.yml"));
        }
        try (var in = getClass().getResourceAsStream("/migration/v6-messages.yml")) {
            Files.copy(Objects.requireNonNull(in), directory.resolve("messages.yml"));
        }
        var config = read("config"); config.set("tab.enabled", false); config.set("tab.refresh-seconds", 7);
        config.set("location-prefix.show-china-province", false); config.set("daily-tasks.rewards.easy.coins", 123);
        config.set("afk-pool.reward.coin-min", 2); config.set("store.categories.plants.name", "自定义花园");
        config.save(directory.resolve("config.yml").toFile());
        var messages = read("messages"); messages.set("title-menu-title", "&#123456自定义标题");
        messages.save(directory.resolve("messages.yml").toFile());

        ConfigUpgrader.upgrade(directory); var next = read("config"); var nextMessages = read("messages");

        assertEquals(8, next.getInt("config-version")); assertEquals(7, nextMessages.getInt("messages-version"));
        assertFalse(next.getBoolean("tab.enabled")); assertEquals(7, next.getInt("tab.refresh-seconds"));
        assertFalse(next.getBoolean("location-prefix.show-china-province"));
        assertEquals(123, next.getInt("daily-tasks.rewards.easy.coins")); assertEquals(2, next.getInt("afk-pool.reward.coin-min"));
        assertEquals("自定义花园", next.getString("store.categories.plants.name"));
        assertEquals(2, next.getInt("afk-pool.exit-grace-seconds")); assertEquals(5, next.getInt("store.category-rows"));
        assertEquals(List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34), next.getIntegerList("store.product-layout.slots"));
        assertTrue(next.getBoolean("welcome-title.enabled")); assertEquals(-1, next.getInt("mascot.npc-id"));
        assertFalse(next.getBoolean("weekly-coin-leaderboard.position-configured"));
        assertEquals("&#123456自定义标题", nextMessages.getString("title-menu-title"));
        assertTrue(nextMessages.contains("welcome-title.title")); assertEquals(16, nextMessages.getStringList("mascot.replies").size());
        try (var files = Files.list(directory)) {
            var names = files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".bak")).toList();
            assertEquals(2, names.size()); assertTrue(names.stream().allMatch(name -> name.contains(".pre-1.4.0-")));
        }
    }
}
