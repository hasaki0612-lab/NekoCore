package land.momo.nekocore.config;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.Messages;
import land.momo.nekocore.service.EnchantmentService;
import land.momo.nekocore.service.InventoryExchangeService;
import land.momo.nekocore.gui.TitleMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ConfigurationValidationTest {
    @TempDir Path directory;
    void seed() throws Exception {
        for (String name : List.of("config","messages")) try(var input=getClass().getResourceAsStream("/"+name+".yml")){
            Files.copy(input,directory.resolve(name+".yml"));
        }
    }
    ConfigurationValidation.Loaded load() throws Exception {
        return ConfigurationValidation.load(directory.resolve("config.yml").toFile(),directory.resolve("messages.yml").toFile(),
                getClass().getResourceAsStream("/messages.yml"), ignored -> {});
    }
    @Test void collectsIndependentBadMaterialsEntityAndMessagesWithoutWritingFiles() throws Exception {
        seed(); var yaml=new YamlConfiguration();yaml.load(directory.resolve("config.yml").toFile());
        yaml.set("store.products.bread.material","bred");yaml.set("store.products.stone.material","stne");
        yaml.set("daily-tasks.rules.hard_marksman.entities",List.of("PHANTON","GHST"));
        yaml.set("cleanup.interval-seconds",0);yaml.save(directory.resolve("config.yml").toFile());
        var messages=new YamlConfiguration();messages.load(directory.resolve("messages.yml").toFile());
        messages.set("home-success",123);messages.set("reload-success",false);messages.save(directory.resolve("messages.yml").toFile());
        byte[] before=Files.readAllBytes(directory.resolve("config.yml")), beforeMessages=Files.readAllBytes(directory.resolve("messages.yml"));
        try(var registry=new PaperRegistryFixture();var books=mockStatic(EnchantmentService.class)){
            books.when(()->EnchantmentService.validatePool(any())).thenReturn(List.of());
            var failure=assertThrows(ConfigurationValidation.Failure.class,this::load);
            String text=failure.getMessage();
            for(String key:List.of("store.products.bread.material","store.products.stone.material","daily-tasks.rules.hard_marksman.entities",
                    "cleanup.interval-seconds","home-success","reload-success","BREAD","STONE","PHANTOM","GHAST")) assertTrue(text.contains(key),key);
            assertTrue(failure.issues().size()>=7);
        }
        assertArrayEquals(before,Files.readAllBytes(directory.resolve("config.yml")));
        assertArrayEquals(beforeMessages,Files.readAllBytes(directory.resolve("messages.yml")));
    }
    @Test void malformedMessagesAndConfigAreBothReported() throws Exception {
        seed();Files.writeString(directory.resolve("config.yml"),"a: [broken\n");Files.writeString(directory.resolve("messages.yml"),"b: [broken\n");
        var error=assertThrows(ConfigurationValidation.Failure.class,this::load);
        assertTrue(error.getMessage().contains("config.yml"));assertTrue(error.getMessage().contains("messages.yml"));
        assertEquals(2,error.issues().size());
    }
    @Test void optionalNewMessagesFillFromDefaultsWithoutWritingOrResettingAdminText() throws Exception {
        seed();var yaml=new YamlConfiguration();yaml.load(directory.resolve("messages.yml").toFile());
        yaml.set("lookup",null);yaml.set("home-success","自定义");yaml.save(directory.resolve("messages.yml").toFile());
        byte[] before=Files.readAllBytes(directory.resolve("messages.yml"));
        try(var registry=new PaperRegistryFixture();var books=mockStatic(EnchantmentService.class)){
            books.when(()->EnchantmentService.validatePool(any())).thenReturn(List.of());
            var loaded=load();assertEquals("自定义",loaded.messages().raw("home-success"));
            assertTrue(loaded.messages().raw("lookup.console").contains("控制台"));
        }
        assertArrayEquals(before,Files.readAllBytes(directory.resolve("messages.yml")));
    }
    @Test void checkAndRejectedReloadUseIdenticalResultAndKeepPreviousSettings() throws Exception {
        seed();Files.writeString(directory.resolve("config.yml"),"a: [broken\n");
        NekoCorePlugin plugin=mock(NekoCorePlugin.class,CALLS_REAL_METHODS);
        Messages messages=mock(Messages.class);Settings previous=mock(Settings.class);
        var sender=mock(CommandSender.class);
        set(plugin,"messages",messages);set(plugin,"settings",previous);
        set(plugin,"exchanges",mock(InventoryExchangeService.class));set(plugin,"titleMenu",mock(TitleMenu.class));
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        doAnswer(ignored->getClass().getResourceAsStream("/messages.yml")).when(plugin).getResource("messages.yml");
        doReturn(java.util.logging.Logger.getAnonymousLogger()).when(plugin).getLogger();
        plugin.checkConfig(sender);plugin.reload(sender);
        var check=org.mockito.ArgumentCaptor.forClass(Map.class);var reload=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(messages).send(eq(sender),eq("config-check-failed"),check.capture());
        verify(messages).send(eq(sender),eq("reload-failed"),reload.capture());
        assertEquals(check.getValue().get("reason"),reload.getValue().get("reason"));
        assertSame(previous,plugin.settings());assertFalse((boolean)get(plugin,"reloading"));
    }
    static void set(Object object,String name,Object value)throws Exception {var field=NekoCorePlugin.class.getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    static Object get(Object object,String name)throws Exception {var field=NekoCorePlugin.class.getDeclaredField(name);field.setAccessible(true);return field.get(object);}
}
