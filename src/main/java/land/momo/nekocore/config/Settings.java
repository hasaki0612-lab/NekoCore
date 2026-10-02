package land.momo.nekocore.config;

import land.momo.nekocore.model.LevelCurve;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.time.ZoneId;

public record Settings(String serverName, String databaseFile, int saveInterval, String survivalWorld, String survivalCommand,
                       LevelCurve curve, Cleanup cleanup, Menu menu, Features features, BedAutoSet bedAutoSet,
                       LocationPrefix locationPrefix, AfkPool afkPool, GlobalTab tab, WelcomeTitle welcomeTitle,
                       Holograms holograms, WeeklyLeaderboard weeklyLeaderboard, Mascot mascot, boolean debug, JoinInfo joinInfo) {
    public static Settings load(File file) throws Exception {
        return load(file, ignored -> {});
    }
    public static Settings load(File file, java.util.function.Consumer<String> warning) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file); // loadConfiguration silently swallows malformed YAML; fail atomically instead.
        String serverName = optionalString(yaml, "branding.server-name", "My Server");
        require(!serverName.isBlank() && serverName.length() <= 64 && serverName.chars().noneMatch(Character::isISOControl),
                "branding.server-name 必须是 1..64 个可显示字符");
        String database = string(yaml, "database.filename");
        require(database.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.db"), "database.filename 必须是插件目录内的 .db 文件名");
        int saveInterval = integer(yaml, "database.save-interval-seconds", 1, 3600);
        String world = string(yaml, "survival.world");
        require(world.matches("[A-Za-z0-9_.-]+"), "survival.world 仅支持字母、数字、下划线、点和连字符");
        String command = string(yaml, "survival.command");
        validateSurvivalCommand(command, "survival.command");
        LevelCurve curve = new LevelCurve(number(yaml, "leveling.base"), number(yaml, "leveling.linear"),
                number(yaml, "leveling.quadratic"), integer(yaml, "leveling.max-level", 2, 1_000_000));
        String sound = string(yaml, "cleanup.sound.name");
        require(sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "cleanup.sound.name 需要形如 minecraft:block.note_block.pling");
        float volume = decimal(yaml, "cleanup.sound.volume", 0, 2);
        float pitch = decimal(yaml, "cleanup.sound.pitch", 0.5f, 2);
        Cleanup cleanup = new Cleanup(bool(yaml, "cleanup.enabled"), integer(yaml, "cleanup.interval-seconds", 61, 31_536_000),
                bool(yaml, "cleanup.sound.enabled"), sound, volume, pitch);
        int size = integer(yaml, "gui.rows", 1, 6) * 9;
        Map<String, Button> buttons = new LinkedHashMap<>();
        Set<Integer> slots = new HashSet<>();
        for (String action : List.of("profile", "daily", "survival", "privacy", "checkin", "survival-new", "minigames", "afk-pool")) {
            String path = "gui.items." + action;
            int slot = integer(yaml, path + ".slot", 0, size - 1);
            require(slots.add(slot), "GUI 槽位不能重复：" + slot);
            require(yaml.isList(path + ".lore"), path + ".lore 必须是列表");
            buttons.put(action, new Button(slot, material(yaml, path + ".material"), string(yaml, path + ".name"),
                    List.copyOf(yaml.getStringList(path + ".lore"))));
        }
        Menu menu = new Menu(optionalBool(yaml, "gui.enabled", true), optionalBool(yaml, "gui.auto-layout", false),
                string(yaml, "gui.title"), size, material(yaml, "gui.filler.material"),
                string(yaml, "gui.filler.name"), Map.copyOf(buttons));
        String newWorld = string(yaml, "survival-new.world");
        require(newWorld.matches("[A-Za-z0-9_.-]+"), "survival-new.world 世界名无效");
        String newCommand = string(yaml, "survival-new.command");
        validateSurvivalCommand(newCommand, "survival-new.command");
        String gameWorld = string(yaml, "minigames.world");
        require(!gameWorld.isBlank(), "minigames.world 不能为空");
        Destination games = new Destination(gameWorld, finite(yaml, "minigames.x"), finite(yaml, "minigames.y"),
                finite(yaml, "minigames.z"), decimal(yaml, "minigames.yaw", -360, 360), decimal(yaml, "minigames.pitch", -90, 90));
        require(yaml.isList("tips.messages") && yaml.getList("tips.messages").stream().allMatch(String.class::isInstance), "tips.messages 必须是文本列表");
        List<String> tipsList = List.copyOf(yaml.getStringList("tips.messages"));
        require(!bool(yaml, "tips.enabled") || !tipsList.isEmpty(), "开启 tips 时列表不能为空");
        Tips tips = new Tips(bool(yaml, "tips.enabled"), integer(yaml, "tips.interval-seconds", 1, 31_536_000), string(yaml, "tips.prefix"), tipsList);
        long coins = number(yaml, "checkin.coins"), exp = number(yaml, "checkin.exp");
        require(coins >= 0 && exp >= 0, "签到奖励不可为负数");
        String chime = string(yaml, "checkin.sound.name");
        require(chime.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "checkin.sound.name 无效");
        Checkin checkin = new Checkin(bool(yaml, "checkin.enabled"), ZoneId.of(string(yaml, "checkin.timezone")), coins, exp,
                chime, decimal(yaml, "checkin.sound.volume", 0, 2), decimal(yaml, "checkin.sound.pitch", 0.5f, 2),
                integer(yaml, "checkin.particle-count", 0, 40));
        Chat chat = new Chat(bool(yaml, "chat.level-prefix-enabled"), string(yaml, "chat.level-prefix"));
        require(yaml.isList("home.bed-auto-set.worlds") && yaml.getList("home.bed-auto-set.worlds").stream()
                .allMatch(value -> value instanceof String name && !name.isBlank()), "home.bed-auto-set.worlds 必须是非空世界名的列表");
        BedAutoSet bed = new BedAutoSet(bool(yaml, "home.bed-auto-set.enabled"), Set.copyOf(yaml.getStringList("home.bed-auto-set.worlds")));
        String geoDatabase = string(yaml, "location-prefix.database-file");
        require(geoDatabase.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.mmdb"),
                "location-prefix.database-file 必须是插件目录内的 .mmdb 文件名");
        LocationPrefix locationPrefix = new LocationPrefix(bool(yaml, "location-prefix.enabled"), geoDatabase,
                bool(yaml, "location-prefix.cache-session"), bool(yaml, "location-prefix.show-china-province"),
                bool(yaml, "location-prefix.show-foreign-country"), bool(yaml, "location-prefix.hide-unknown"));
        String afkWorld = string(yaml, "afk-pool.world");
        require(afkWorld.matches("[A-Za-z0-9_.-]+"), "afk-pool.world 世界名无效");
        Destination afkTeleport = new Destination(afkWorld, finite(yaml, "afk-pool.teleport.x"),
                finite(yaml, "afk-pool.teleport.y"), finite(yaml, "afk-pool.teleport.z"),
                decimal(yaml, "afk-pool.teleport.yaw", -360, 360), decimal(yaml, "afk-pool.teleport.pitch", -90, 90));
        int coinMin = integer(yaml, "afk-pool.reward.coin-min", 0, 1_000_000);
        int coinMax = integer(yaml, "afk-pool.reward.coin-max", 0, 1_000_000);
        require(coinMax >= coinMin, "afk-pool.reward.coin-max 不可小于 coin-min");
        AfkReward afkReward = new AfkReward(afkRewardInterval(yaml, warning),
                number(yaml, "afk-pool.reward.base-exp"), ratio(yaml, "afk-pool.reward.normal-multiplier", 0, 100),
                ratio(yaml, "afk-pool.reward.coin-chance", 0, 1), coinMin, coinMax);
        require(afkReward.baseExp() >= 0, "afk-pool.reward.base-exp 不可为负数");
        AfkTitle afkTitle = new AfkTitle(bool(yaml, "afk-pool.title.enabled"),
                bool(yaml, "afk-pool.title.hide-while-inventory-open"),
                integer(yaml, "afk-pool.title.stay-ticks", 20, 60), integer(yaml, "afk-pool.title.fade-out-ticks", 0, 20));
        boolean afkConfigured = optionalBool(yaml, "afk-pool.position-configured", true);
        AfkPool afkPool = new AfkPool(bool(yaml, "afk-pool.enabled") && afkConfigured, afkTeleport,
                integer(yaml, "afk-pool.exit-grace-seconds", 0, 60), afkReward, afkTitle,
                optionalBool(yaml, "afk-pool.end-message-enabled", true));
        GlobalTab tab = new GlobalTab(bool(yaml, "tab.enabled"), bool(yaml, "tab.show-location-prefix"),
                integer(yaml, "tab.refresh-seconds", 1, 60));
        WelcomeTitle welcome = new WelcomeTitle(bool(yaml, "welcome-title.enabled"),
                integer(yaml, "welcome-title.delay-ticks", 0, 200), integer(yaml, "welcome-title.fade-in-ticks", 0, 200),
                integer(yaml, "welcome-title.stay-ticks", 1, 600), integer(yaml, "welcome-title.fade-out-ticks", 0, 200));
        Holograms holograms = new Holograms(integer(yaml, "holograms.line-width", 40, 2048), bool(yaml, "holograms.shadowed"));
        String boardWorld = string(yaml, "weekly-coin-leaderboard.world");
        require(boardWorld.matches("[A-Za-z0-9_.-]+"), "weekly-coin-leaderboard.world 世界名无效");
        WeeklyLeaderboard board = new WeeklyLeaderboard(bool(yaml, "weekly-coin-leaderboard.enabled"),
                bool(yaml, "weekly-coin-leaderboard.position-configured"), boardWorld,
                finite(yaml, "weekly-coin-leaderboard.x"), finite(yaml, "weekly-coin-leaderboard.y"),
                finite(yaml, "weekly-coin-leaderboard.z"), decimal(yaml, "weekly-coin-leaderboard.yaw", -360, 360),
                integer(yaml, "weekly-coin-leaderboard.refresh-seconds", 10, 3600),
                billboard(yaml, "weekly-coin-leaderboard.billboard"));
        String normalParticle = string(yaml, "mascot.normal-particle"), overParticle = string(yaml, "mascot.over-limit-particle");
        require(normalParticle.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "mascot.normal-particle 无效");
        require(overParticle.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "mascot.over-limit-particle 无效");
        String mascotIdPath = yaml.contains("mascot.npc-id") ? "mascot.npc-id" : "mascot.citizens-npc-id";
        int mascotNpcId = integer(yaml, mascotIdPath, -1, Integer.MAX_VALUE);
        require(!bool(yaml, "mascot.enabled") || mascotNpcId >= 0,
                "mascot.npc-id 开启 Mascot 时必须填写管理员创建的 Citizens NPC ID");
        require(yaml.isList("mascot.hologram-lines") && yaml.getList("mascot.hologram-lines").stream().allMatch(String.class::isInstance),
                "mascot.hologram-lines 必须是文本列表");
        Mascot mascot = new Mascot(bool(yaml, "mascot.enabled"), mascotNpcId,
                bool(yaml, "mascot.hologram-enabled"), List.copyOf(yaml.getStringList("mascot.hologram-lines")),
                decimal(yaml, "mascot.hologram-y-offset", 0, 10),
                integer(yaml, "mascot.interaction-window-seconds", 1, 3600), integer(yaml, "mascot.normal-click-limit", 1, 100),
                integer(yaml, "mascot.over-limit-chat-cooldown-seconds", 1, 60), normalParticle,
                integer(yaml, "mascot.normal-particle-count", 0, 100), overParticle,
                integer(yaml, "mascot.over-limit-particle-count", 0, 100));
        List<JoinInfoEntry> links = new ArrayList<>();
        for (String id : List.of("docs", "website", "community", "discord")) {
            String url = optionalString(yaml, "join-info.links." + id, "");
            require(url.isEmpty() || safeUrl(url), "join-info.links." + id + " 值 \"" + url + "\" 必须为空或有效的 http/https URL（不可含命令、空格或用户凭据）");
            links.add(new JoinInfoEntry(id, url));
        }
        JoinInfo joinInfo = new JoinInfo(optionalBool(yaml, "join-info.enabled", true),
                yaml.contains("join-info.delay-ticks") ? integer(yaml, "join-info.delay-ticks", 0, 1200) : 30,
                List.copyOf(links));
        return new Settings(serverName, database, saveInterval, world, command, curve, cleanup, menu,
                new Features(optionalBool(yaml, "survival.enabled", true), optionalBool(yaml, "survival-new.enabled", true),
                        newWorld, newCommand, optionalBool(yaml, "minigames.enabled", true), games, tips, checkin, chat),
                bed, locationPrefix, afkPool, tab,
                welcome, holograms, board, mascot, optionalBool(yaml, "advanced.debug", false), joinInfo);
    }

    static void validateSurvivalCommand(String command, String path) {
        require(command.contains("{player}") && command.contains("{world}") && !command.stripLeading().startsWith("/")
                && !command.contains("\n") && !command.contains("\r"), path + " 需包含 {player} 和 {world}，且不要带开头的 / 或换行");
    }

    static int afkRewardInterval(YamlConfiguration yaml, java.util.function.Consumer<String> warning) {
        Object configured = yaml.get("afk-pool.reward.interval-seconds");
        if ((configured instanceof Integer || configured instanceof Long)
                && ((Number) configured).longValue() >= 1 && ((Number) configured).longValue() <= 31_536_000)
            return ((Number) configured).intValue();
        warning.accept("afk-pool.reward.interval-seconds 值 \"" + configured + "\" 无效；应为 1..31536000 的整数，本次使用安全默认 60 秒。");
        return 60;
    }

    static boolean safeUrl(String value) {
        try {
            var uri = java.net.URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank() && uri.getRawUserInfo() == null
                    && value.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
        } catch (IllegalArgumentException error) { return false; }
    }

    private static Display.Billboard billboard(YamlConfiguration yaml, String path) {
        String value = optionalString(yaml, path, "FIXED");
        require(List.of("FIXED", "CENTER").contains(value), path + " 值 \"" + value + "\" 必须为 FIXED 或 CENTER");
        return Display.Billboard.valueOf(value);
    }

    private static String string(YamlConfiguration yaml, String path) {
        require(yaml.isString(path), path + " 必须是文本"); return yaml.getString(path);
    }
    private static String optionalString(YamlConfiguration yaml, String path, String fallback) {
        if (!yaml.contains(path)) return fallback;
        return string(yaml, path);
    }
    private static long number(YamlConfiguration yaml, String path) {
        Object value = yaml.get(path);
        require(value instanceof Integer || value instanceof Long, path + " 必须是整数");
        return ((Number) value).longValue();
    }
    private static int integer(YamlConfiguration yaml, String path, int min, int max) {
        long n = number(yaml, path); require(n >= min && n <= max, path + " 范围为 " + min + ".." + max); return (int) n;
    }
    private static boolean bool(YamlConfiguration yaml, String path) {
        require(yaml.isBoolean(path), path + " 必须是 true/false"); return yaml.getBoolean(path);
    }
    private static boolean optionalBool(YamlConfiguration yaml, String path, boolean fallback) {
        if (!yaml.contains(path)) return fallback;
        return bool(yaml, path);
    }
    private static float decimal(YamlConfiguration yaml, String path, float min, float max) {
        require(yaml.get(path) instanceof Number, path + " 必须是数字");
        double n = yaml.getDouble(path);
        require(Double.isFinite(n) && n >= min && n <= max, path + " 范围为 " + min + ".." + max); return (float) n;
    }
    private static double finite(YamlConfiguration yaml, String path) {
        require(yaml.get(path) instanceof Number, path + " 必须是数字");
        double n = yaml.getDouble(path); require(Double.isFinite(n) && Math.abs(n) <= 30_000_000, path + " 坐标无效"); return n;
    }
    private static double ratio(YamlConfiguration yaml, String path, double min, double max) {
        require(yaml.get(path) instanceof Number, path + " 必须是数字");
        double n = yaml.getDouble(path); require(Double.isFinite(n) && n >= min && n <= max, path + " 范围无效"); return n;
    }
    static Material material(YamlConfiguration yaml, String path) {
        String configured = string(yaml, path);
        Material material = Material.matchMaterial(configured);
        if (material == null || !material.isItem() || material.isAir())
            throw new IllegalArgumentException(path + " 未知 Material \"" + configured + "\"" + suggestion(configured));
        return material;
    }
    static String suggestion(String configured) {
        String needle = configured.toUpperCase(Locale.ROOT);
        Material[] values = Material.values();
        if (values == null) return ""; // Unit-test registry boundary.
        return Arrays.stream(values).filter(Material::isItem)
                .min(Comparator.comparingInt(value -> distance(needle, value.name())))
                .filter(value -> distance(needle, value.name()) <= Math.max(2, needle.length() / 3))
                .map(value -> "；你是否想填 \"" + value.name() + "\"？").orElse("");
    }
    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(
                    current[j - 1] + 1, previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalArgumentException(message); }
    public record Cleanup(boolean enabled, int interval, boolean soundEnabled, String sound, float volume, float pitch) {}
    public record Menu(boolean enabled, boolean autoLayout, String title, int size, Material filler, String fillerName,
                       Map<String, Button> buttons) {}
    public record Button(int slot, Material material, String name, List<String> lore) {}
    public record Features(boolean survivalEnabled, boolean secondWorldEnabled, String newWorld, String newCommand,
                           boolean minigamesEnabled, Destination minigames, Tips tips, Checkin checkin, Chat chat) {}
    public record Destination(String world, double x, double y, double z, float yaw, float pitch) {}
    public record Tips(boolean enabled, int interval, String prefix, List<String> messages) {}
    public record Checkin(boolean enabled, ZoneId zone, long coins, long exp, String sound, float volume, float pitch, int particles) {}
    public record Chat(boolean enabled, String prefix) {}
    public record BedAutoSet(boolean enabled, Set<String> worlds) {
        public BedAutoSet { worlds = Set.copyOf(worlds); }
    }
    public record LocationPrefix(boolean enabled, String databaseFile, boolean cacheSession,
                                 boolean showChinaProvince, boolean showForeignCountry, boolean hideUnknown) {}
    public record AfkPool(boolean enabled, Destination teleport, int exitGrace, AfkReward reward, AfkTitle title, boolean endMessageEnabled) {}
    public record AfkReward(int interval, long baseExp, double normalMultiplier, double coinChance,
                            int coinMin, int coinMax) {}
    public record AfkTitle(boolean enabled, boolean hideWhileInventoryOpen, int stayTicks, int fadeOutTicks) {}
    public record GlobalTab(boolean enabled, boolean showLocationPrefix, int refreshSeconds) {}
    public record WelcomeTitle(boolean enabled, int delayTicks, int fadeInTicks, int stayTicks, int fadeOutTicks) {}
    public record Holograms(int lineWidth, boolean shadowed) {}
    public record WeeklyLeaderboard(boolean enabled, boolean positionConfigured, String world, double x, double y,
                                    double z, float yaw, int refreshSeconds, Display.Billboard billboard) {}
    public record JoinInfo(boolean enabled, int delayTicks, List<JoinInfoEntry> links) {
        public JoinInfo { links = List.copyOf(links); }
    }
    public record JoinInfoEntry(String id, String url) {}
    public record Mascot(boolean enabled, int npcId, boolean hologramEnabled, List<String> hologramLines, float hologramYOffset,
                         int windowSeconds, int normalClickLimit, int overLimitCooldownSeconds,
                         String normalParticle, int normalParticleCount, String overLimitParticle,
                         int overLimitParticleCount) {
        public Mascot { hologramLines = List.copyOf(hologramLines); }
    }
}
