package land.momo.nekocore.config;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationErrorUxTest {
    @Test void materialErrorHasHumanPurposeRealLineCurrentSuggestionAndCopyableYaml() {
        var index = YamlSourceIndex.of("store:\n  products:\n    bread:\n      material: bred\n");
        String text = ValidationIssue.of("config.yml","store.products.bread.material","bred",
                "未知 Material \"bred\"；你是否想填 \"BREAD\"？",index).format();
        for (String part : List.of("商店商品「bread」","物品类型","config.yml","第 4 行","当前值：bred","建议值：BREAD",
                "material: bred","material: BREAD","Minecraft 物品/方块类型","/nekocore lookup")) assertTrue(text.contains(part),part);
    }
    @Test void flowMappingsQuotedKeysAndBlockTextDoNotInventKeyLocations() {
        String source = "# note\r\n'branding': { 'server-name': \"demo: # name\" }\r\ntext: |-\r\n  fake:\r\n    material: bred\r\n";
        var index=YamlSourceIndex.of(source);
        assertEquals(2,index.line("branding.server-name").orElseThrow());
        assertTrue(index.line("fake.material").isEmpty());
    }
    @Test void aliasMergeDuplicateKeysAndUnparseableYamlUseHonestFallback() {
        for (String source : List.of("base: &b {x: 1}\nother: *b\n","base: &b {x: 1}\nother: {<<: *b}\n",
                "branding:\n  server-name: first\n  server-name: second\n","branding:\n\tserver-name: name\n")) {
            var index=YamlSourceIndex.of(source); assertTrue(index.line("branding.server-name").isEmpty());
            String text=ValidationIssue.of("config.yml","branding.server-name","bad","需要文本",index).format();
            assertFalse(text.contains("第 ")); assertTrue(text.contains("附近内容")); assertTrue(text.contains("branding.server-name"));
        }
    }
    @Test void missingKeyNeverGetsFakeLineAndListCorrectionKeepsOtherEntries() {
        var index=YamlSourceIndex.of("daily-tasks:\n  rules:\n    hard_marksman:\n      entities: [PHANTON, GHAST]\n");
        var issue=ValidationIssue.of("config.yml","daily-tasks.rules.hard_marksman.entities","PHANTON",
                "未知 EntityType；你是否想填 \"PHANTOM\"？",index);
        assertTrue(issue.format().contains("Minecraft 生物类型")); assertTrue(issue.format().contains("保留其他正确项"));
        assertTrue(index.line("missing").isEmpty());
    }
    @Test void firstScreenCapsTwentyButRetainsTotalCountAndRepairWorkflow() {
        var issues=new ArrayList<ValidationIssue>();
        for(int i=0;i<25;i++) issues.add(ValidationIssue.of("config.yml","key."+i,i,"无效",YamlSourceIndex.of("")));
        var error=new ConfigurationValidation.Failure(issues);
        assertEquals(25,error.issues().size());
        assertEquals(20,error.getMessage().split("\\[配置错误\\]",-1).length-1);
        assertTrue(error.getMessage().contains("共有 25 项")); assertTrue(error.getMessage().contains("/nekocore config check"));
    }
}
