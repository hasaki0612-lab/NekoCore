package land.momo.nekocore.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class Public110UpgradeTest {
    @TempDir Path directory;
    void seed() throws Exception {
        for(var name:List.of("config","messages")) try(var in=getClass().getResourceAsStream("/migration/v"+(name.equals("config")?8:7)+"-"+name+".yml")){
            Files.copy(in,directory.resolve(name+".yml"));
        }
    }
    YamlConfiguration read(String name) throws Exception {var y=new YamlConfiguration(); y.load(directory.resolve(name+".yml").toFile());return y;}
    @Test void defaultsUpgradeBackUpExactlyAndSecondRunIsIdempotent() throws Exception {
        seed(); String before=Files.readString(directory.resolve("config.yml")); ConfigUpgrader.upgrade(directory);
        var c=read("config"); var m=read("messages");
        assertEquals(9,c.getInt("config-version")); assertEquals(8,m.getInt("messages-version"));
        assertEquals(180,c.getInt("tips.interval-seconds")); assertEquals(60,c.getInt("afk-pool.reward.interval-seconds"));
        assertEquals("FIXED",c.getString("weekly-coin-leaderboard.billboard")); assertEquals(2.25,c.getDouble("mascot.hologram-y-offset"));
        assertEquals("",c.getString("join-info.links.docs"));
        try(var files=Files.list(directory)){assertEquals(before,Files.readString(files.filter(p->p.getFileName().toString().startsWith("config.yml.pre-")).findFirst().orElseThrow()));}
        String upgraded=Files.readString(directory.resolve("config.yml"));ConfigUpgrader.upgrade(directory);
        assertEquals(upgraded,Files.readString(directory.resolve("config.yml")));
    }
    @Test void customBrandWorldSlotsRewardsTipsTabAndMessagesSurvive() throws Exception {
        seed(); var c=read("config");var m=read("messages");
        Map<String,Object> custom=Map.of("branding.server-name","Custom","survival.world","survival","gui.items.profile.slot",0,
                "afk-pool.reward.interval-seconds",75,"tips.interval-seconds",240,"tips.messages",List.of("Custom tip"),
                "daily-tasks.rewards.easy.coins",88,"mascot.hologram-y-offset",1.8,"join-info.links.docs","https://github.com");
        custom.forEach(c::set);c.save(directory.resolve("config.yml").toFile());
        m.set("tab.header","Custom TAB");m.set("home-success","Custom home");m.save(directory.resolve("messages.yml").toFile());
        ConfigUpgrader.upgrade(directory);c=read("config");m=read("messages");
        for(var entry:custom.entrySet())assertEquals(entry.getValue(),c.get(entry.getKey()),entry.getKey());
        assertEquals("Custom TAB",m.getString("tab.header"));assertEquals("Custom home",m.getString("home-success"));
    }
    @Test void invalidRewardIntervalsUseSixtyWithPathAndValueWarning(){
        for(Object invalid:Arrays.asList(null,0,-1,1.5,"sixty",Long.MAX_VALUE)){
            var yaml=new YamlConfiguration();yaml.set("afk-pool.reward.interval-seconds",invalid);
            List<String>warnings=new ArrayList<>();
            assertEquals(60,Settings.afkRewardInterval(yaml,warnings::add));assertEquals(1,warnings.size());
            assertTrue(warnings.getFirst().contains("afk-pool.reward.interval-seconds"));
        }
        var yaml=new YamlConfiguration();yaml.set("afk-pool.reward.interval-seconds",75);
        assertEquals(75,Settings.afkRewardInterval(yaml,ignored->fail()));
    }
    @Test void onlyHttpHttpsWithoutCredentialsAreAccepted(){
        assertTrue(Settings.safeUrl("https://github.com/hasaki0612-lab/NekoCore"));
        for(String invalid:List.of("javascript:alert(1)","/op Player_A","file:///data","https://user:pass@github.com","https://","https://github.com bad"))
            assertFalse(Settings.safeUrl(invalid),invalid);
    }
    @Test void billboardDefaultsFixedAndSupportsCenterButRejectsUnknown() throws Exception {
        seed();var c=read("config");c.set("weekly-coin-leaderboard.billboard",null);c.save(directory.resolve("config.yml").toFile());
        Material item=mock(Material.class);when(item.isItem()).thenReturn(true);
        try(var registry=mockStatic(Material.class)){
            registry.when(()->Material.matchMaterial(anyString())).thenReturn(item);
            assertEquals(Display.Billboard.FIXED,Settings.load(directory.resolve("config.yml").toFile()).weeklyLeaderboard().billboard());
            c.set("weekly-coin-leaderboard.billboard","CENTER");c.save(directory.resolve("config.yml").toFile());
            assertEquals(Display.Billboard.CENTER,Settings.load(directory.resolve("config.yml").toFile()).weeklyLeaderboard().billboard());
            c.set("weekly-coin-leaderboard.billboard","ROTATE");c.save(directory.resolve("config.yml").toFile());
            assertThrows(IllegalArgumentException.class,()->Settings.load(directory.resolve("config.yml").toFile()));
        }
    }
}
