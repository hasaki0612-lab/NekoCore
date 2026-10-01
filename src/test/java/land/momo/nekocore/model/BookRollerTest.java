package land.momo.nekocore.model;

import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.service.EnchantmentService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BookRollerTest {
    @Test void exactTwoMaximumSixStrictlySubMaximumWithoutDuplicateEnchantments() {
        List<BookRoller.Enchant> pool = List.of(new BookRoller.Enchant("mending", 1), new BookRoller.Enchant("frost", 2),
                new BookRoller.Enchant("sharp", 5), new BookRoller.Enchant("unbreaking", 3), new BookRoller.Enchant("protection", 4),
                new BookRoller.Enchant("fortune", 3), new BookRoller.Enchant("power", 5), new BookRoller.Enchant("efficiency", 5), new BookRoller.Enchant("silk", 1));
        for (int seed = 0; seed < 500; seed++) {
            var offers = BookRoller.roll(pool, 1800, 300, Map.of("mending", 3600L), new Random(seed));
            assertEquals(8, offers.size()); assertEquals(8, offers.stream().map(CommerceRepository.Offer::enchantment).distinct().count());
            assertEquals(2, offers.stream().filter(CommerceRepository.Offer::maximum).count());
            for (var offer : offers) {
                int maximum = pool.stream().filter(e -> e.key().equals(offer.enchantment())).findFirst().orElseThrow().maxLevel();
                if (offer.maximum()) assertEquals(maximum, offer.level());
                else { assertTrue(offer.level() >= 1 && offer.level() <= 2); assertTrue(offer.level() < maximum); }
                assertEquals(offer.enchantment().equals("mending") ? 3600 : offer.maximum() ? 1800 : 300, offer.price());
            }
        }
    }
    @Test void insufficientOrDuplicatePoolRejected() {
        assertThrows(IllegalArgumentException.class, () -> BookRoller.roll(List.of(new BookRoller.Enchant("mending", 1)), 1, 1, Map.of(), new Random()));
        var duplicates = List.of(new BookRoller.Enchant("same", 3), new BookRoller.Enchant("same", 4));
        assertThrows(IllegalArgumentException.class, () -> BookRoller.roll(duplicates, 1, 1, Map.of(), new Random()));
    }
    @Test void oneShotRefreshTargetsShanghaiFourExactly() {
        assertEquals(1, EnchantmentService.nextRefreshSeconds(Instant.parse("2026-09-29T19:59:59Z")));
        assertEquals(86400, EnchantmentService.nextRefreshSeconds(Instant.parse("2026-09-29T20:00:00Z")));
        assertEquals(14400, EnchantmentService.nextRefreshSeconds(Instant.parse("2026-09-29T16:00:00Z")));
    }
}
