package land.momo.nekocore.config;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LookupNamesTest {
    @Test void exactPrefixContainsThenFuzzyOrderIsDeterministic() {
        var names=List.of("IRON_SWORD","SWORD_HANDLE","SWORX","SWORD","DIAMOND_SWORD","AXE","SWORD_HANDLE");
        assertEquals(List.of("SWORD","SWORD_HANDLE","DIAMOND_SWORD","IRON_SWORD","SWORX"),LookupNames.search("sword",names,10));
    }
    @Test void fuzzyKeepsLevenshteinNamesButDoesNotDumpUnrelatedEnums() {
        assertEquals(List.of("BREAD"),LookupNames.search("bred",List.of("BREAD","AXE","DIAMOND_SWORD"),10));
        assertTrue(LookupNames.search("zzzzzzzzzzzzzzzz",List.of("BREAD","AXE"),10).isEmpty());
    }
    @Test void exactNamespacesCaseAndMaximumTenAreHandled() {
        assertEquals(List.of("DIAMOND_SWORD"),LookupNames.search("minecraft:diamond_sword",List.of("DIAMOND_SWORD"),10));
        var names=new ArrayList<String>();for(int i=0;i<30;i++)names.add("STONE_"+i);
        assertEquals(10,LookupNames.search("stone",names,100).size());
        assertTrue(LookupNames.search("stone",names,0).isEmpty());
    }
    @Test void chineseAndEmptyQueriesNeverInventTranslations() {
        for(String query:List.of("钻石剑","","  ","op Player","*"))assertTrue(LookupNames.search(query,List.of("DIAMOND_SWORD"),10).isEmpty());
    }
}
