package land.momo.nekocore.config;

import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceContractTest {
    YamlConfiguration resource(String path) throws Exception {
        var yaml=new YamlConfiguration();
        try(var reader=new InputStreamReader(getClass().getResourceAsStream(path),StandardCharsets.UTF_8)){yaml.load(reader);} return yaml;
    }
    @Test void pluginCommandsAreNamespacedNormallyAndLevelshopHasNoPublicCommand() throws Exception {
        var yaml=resource("/plugin.yml"); assertEquals("1.2.0",yaml.getString("version"));
        var messages=resource("/messages.yml");
        for(String command:List.of("menu","tasks","coins","checkin","home","sethome","check","store","bag","tpn","yes","no","nekocore")) {
            assertTrue(yaml.contains("commands."+command));
            assertTrue(messages.isString("usage."+(command.equals("nekocore")?"admin":command)),command+" usage");
        }
        assertFalse(yaml.contains("commands.levelshop"));
        for(String permission:List.of("store","bag","tpn","levelshop")) assertEquals(true,yaml.get("permissions.nekocore."+permission+".default"));
        assertEquals("op",yaml.getString("permissions.nekocore.admin.default"));
    }
    @Test void allOldMessageKeysRemainAndFiveSecondCountdownIsOneColoredToken() throws Exception {
        var old=resource("/migration/v3-messages.yml"); var current=resource("/messages.yml");
        for(String key:old.getKeys(true)) if(!old.isConfigurationSection(key)) assertTrue(current.contains(key),key);
        for(int seconds=1;seconds<=5;seconds++) {
            Component component=Messages.text(current.getString("cleanup-countdown"),Map.of("seconds",""+seconds));
            assertTrue(hasToken(component,seconds+"秒"));
        }
    }
    boolean hasToken(Component component,String token) {
        return component instanceof TextComponent text && text.content().contains(token) && TextColor.fromHexString("#FFE49A").equals(text.color())
                || component.children().stream().anyMatch(child -> hasToken(child,token));
    }
    @Test void tipsCoverEachNewCommandSeparatelyAndKeepThreeMinuteCadence() throws Exception {
        var config=resource("/config.yml"); assertEquals(180,config.getInt("tips.interval-seconds"));
        var tips=config.getStringList("tips.messages");
        for(String command:List.of("/store","/bag","/tpn","/yes","/no")) assertTrue(tips.stream().anyMatch(line -> line.contains(command)),command);
        assertTrue(tips.stream().noneMatch(line -> line.contains("/yes") && line.contains("/no")));
        assertTrue(tips.stream().anyMatch(line -> line.contains("{server}")));
    }
    @Test void afkAndLocationDefaultsArePrivateCoarseAndConfigurable() throws Exception {
        var config=resource("/config.yml"); var messages=resource("/messages.yml");
        assertEquals(9,config.getInt("config-version")); assertEquals(8,messages.getInt("messages-version"));
        assertTrue(config.getBoolean("tab.show-location-prefix"));
        assertEquals(34,config.getInt("gui.items.afk-pool.slot")); assertEquals("WATER_BUCKET",config.getString("gui.items.afk-pool.material"));
        assertFalse(config.getBoolean("location-prefix.enabled")); assertFalse(config.getBoolean("afk-pool.enabled"));
        assertFalse(config.getBoolean("afk-pool.position-configured")); assertEquals(60,config.getInt("afk-pool.reward.interval-seconds"));
        assertEquals(1,config.getInt("afk-pool.reward.coin-min")); assertEquals(4,config.getInt("afk-pool.reward.coin-max"));
        assertEquals(0.45,config.getDouble("afk-pool.reward.coin-chance")); assertTrue(messages.contains("afk-pool.title"));
        assertEquals(2,config.getInt("afk-pool.exit-grace-seconds")); assertTrue(config.getBoolean("welcome-title.enabled"));
        assertFalse(config.getBoolean("weekly-coin-leaderboard.position-configured"));
        assertFalse(config.getBoolean("mascot.enabled")); assertEquals(-1,config.getInt("mascot.npc-id"));
        assertEquals(2, config.getStringList("mascot.hologram-lines").size());
        assertEquals(16,messages.getStringList("mascot.replies").size());
        assertEquals("My Server",config.getString("branding.server-name")); assertTrue(config.getBoolean("gui.auto-layout"));
        assertTrue(config.getString("gui.items.privacy.name").contains("网络地区"));
        assertFalse(config.getString("gui.items.privacy.name").contains("IP 地址"));
    }
    @Test void publicRuntimeResourcesContainNoPrivateBranding() throws Exception {
        // Any routable IPv4 literal is forbidden, rather than one hard-coded address:
        // listing a real host here would itself publish it. Reserved, loopback and
        // documentation ranges are allowed if a resource ever needs an example.
        // Note: this also rejects version strings shaped like a.b.c.d. That is
        // acceptable because none of the three resources below contains any
        // four-octet dotted literal today, so the rule is strictly stronger.
        var forbiddenAddress = java.util.regex.Pattern.compile(
                "\\b(?!0\\.0\\.0\\.0\\b)(?!127\\.)(?!10\\.)(?!192\\.168\\.)(?!169\\.254\\.)"
                        + "(?!172\\.(?:1[6-9]|2[0-9]|3[01])\\.)(?!192\\.0\\.2\\.)(?!198\\.51\\.100\\.)"
                        + "(?!203\\.0\\.113\\.)(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
        List<String> resources = new ArrayList<>(List.of("/config.yml", "/messages.yml", "/plugin.yml"));
        for (int version = 1; version <= 8; version++) for (String type : List.of("config", "messages")) {
            String path = "/migration/v" + version + "-" + type + ".yml";
            if (getClass().getResource(path) != null) resources.add(path);
        }
        for (String path : resources) {
            String text;
            try (var input = getClass().getResourceAsStream(path)) {
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String forbidden : List.of("Momo Land", "DeepSeek", "小鲸鱼娘", "大肥鱼", "RainYun", "Evoxt"))
                assertFalse(text.contains(forbidden), path + " contains " + forbidden);
            var address = forbiddenAddress.matcher(text);
            assertFalse(address.find(), () -> path + " contains a routable IP address: " + address.group());
        }
    }
    @Test void freshInstallIsSelfContainedAndOptionalIntegrationsStayOptional() throws Exception {
        var plugin = resource("/plugin.yml");
        assertFalse(plugin.contains("depend"));
        assertEquals(Set.of("PlaceholderAPI", "Multiverse-Core", "Citizens"),
                new HashSet<>(plugin.getStringList("softdepend")));
        for (int version = 1; version <= 5; version++)
            assertNotNull(getClass().getResource("/db/migration/V" + version + "__" + switch (version) {
                case 1 -> "initial.sql";
                case 2 -> "daily_checkins.sql";
                case 3 -> "shops_bags_titles.sql";
                case 4 -> "daily_tasks.sql";
                default -> "weekly_coin_earnings.sql";
            }));
        assertNull(getClass().getResource("/db/migration/V6__future.sql"));
        assertNull(getClass().getResource("/GeoLite2-City.mmdb"));
        var config = resource("/config.yml");
        assertTrue(config.getBoolean("survival.enabled"));
        for (String path : List.of("survival-new.enabled", "minigames.enabled", "location-prefix.enabled",
                "afk-pool.enabled", "afk-pool.position-configured", "weekly-coin-leaderboard.enabled",
                "weekly-coin-leaderboard.position-configured", "mascot.enabled"))
            assertFalse(config.getBoolean(path), path);
    }
    @Test void joinInfoHasChatTemplatesAndEmptyExternalLinks() throws Exception {
        var messages=resource("/messages.yml");
        var config=resource("/config.yml");
        assertTrue(messages.isList("join-info.lines"));
        assertTrue(config.getBoolean("join-info.enabled"));
        for(String id:List.of("docs","website","community","discord")) assertEquals("",config.getString("join-info.links."+id));
        assertEquals("FIXED",config.getString("weekly-coin-leaderboard.billboard"));
        assertTrue(config.getBoolean("afk-pool.end-message-enabled"));
    }
}
