package land.momo.nekocore.config;

import land.momo.nekocore.TestDefaults;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureSettingsTest {
    @Test void completeDefaultCatalogAndPerksAreUsable() throws Exception {
        var config = TestDefaults.features();
        assertEquals(8, config.shop().categories().size()); assertEquals(109, config.shop().products().size());
        assertEquals(5, config.shop().rows());
        assertEquals(List.of(10,12,14,16,19,21,23,25), config.shop().categories().stream().map(FeatureSettings.Category::slot).toList());
        assertEquals(List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34), config.shop().productSlots());
        assertEquals(List.of(45,46,49), List.of(config.shop().previousSlot(),config.shop().nextSlot(),config.shop().infoSlot()));
        assertEquals(List.of("Yuki", "Momo", "Neko"), config.levelShop().titles().stream().map(FeatureSettings.Title::name).toList());
        assertEquals(List.of(2500L,7500L,20000L), config.levelShop().titles().stream().map(FeatureSettings.Title::price).toList());
        assertEquals(List.of("mame", "momo", "sora"), config.levelShop().titles().stream().map(FeatureSettings.Title::id).toList());
        assertEquals(List.of(1.10, 1.10, 1.20), config.levelShop().titles().stream().map(FeatureSettings.Title::afkExpMultiplier).toList());
        assertEquals(30, config.levelShop().title("sora").cooldown());
        assertEquals(Set.of("world"), config.levelShop().worlds());
        assertTrue(config.shop().products().stream().allMatch(p -> p.price() > 0 && p.sellLimit() == 200));
        assertEquals(4, config.shop().product("echo_shard").buyLimit());
        assertEquals(4800, config.shop().product("diamond_chestplate").price());
        Map<String,Long> diamondPrices=Map.of(
                "diamond_sword",1800L,"diamond_helmet",2800L,"diamond_chestplate",4800L,
                "diamond_leggings",4000L,"diamond_boots",2400L,"diamond_pickaxe",2400L,
                "diamond_axe",2200L,"diamond_shovel",1200L,"diamond_hoe",1600L);
        diamondPrices.forEach((id,price) -> {
            assertEquals(price,config.shop().product(id).price(),id);
            assertEquals(Math.floorDiv(Math.multiplyExact(price,2L),3L),config.shop().sellPrice(price),id+" sell");
        });
        assertNull(config.shop().product("netherite_chestplate"));
        assertTrue(config.shop().products().stream().noneMatch(p -> p.id().contains("netherite")));
        assertTrue(config.books().excluded().containsAll(Set.of("binding_curse", "vanishing_curse")));
    }
    @Test void whitelistDefaultsUnknownWorldsToReadonlyAndUnlocksThreeSizes() throws Exception {
        var bag = TestDefaults.features().bag();
        assertTrue(bag.writable("world")); assertFalse(bag.writable("world_new"));
        assertFalse(bag.writable("lobby")); assertFalse(bag.writable("future_world"));
        assertEquals(18, bag.capacity(1)); assertEquals(18, bag.capacity(9)); assertEquals(27, bag.capacity(10));
        assertEquals(27, bag.capacity(24)); assertEquals(36, bag.capacity(25));
    }
    @Test void sellPriceFloorsPerUnitWithoutFloatingPointOrOverflow() throws Exception {
        var shop = TestDefaults.features().shop();
        assertEquals(2, shop.sellPrice(3)); assertEquals(2, shop.sellPrice(4)); assertEquals(3, shop.sellPrice(5));
        assertEquals(new java.math.BigInteger("6148914691236517204").longValueExact(), shop.sellPrice(Long.MAX_VALUE));
    }
    @Test void invalidPricesWorldTypesSlotsAndRatiosFailBeforeApplyingReload() throws Exception {
        for (var change : Map.of("store.products.stone.price", -1, "store.sell-price.numerator", 4,
                "store.categories.plants.slot", 100, "bag.writable-worlds", List.of(123), "bag.unlock-levels.36", 5,
                "tpn.timeout-seconds", 0, "store.maximum-custom-quantity", 0).entrySet()) {
            var yaml = TestDefaults.yaml(); yaml.set(change.getKey(), change.getValue());
            assertThrows(IllegalArgumentException.class, () -> TestDefaults.features(yaml), change.getKey());
        }
    }
}
