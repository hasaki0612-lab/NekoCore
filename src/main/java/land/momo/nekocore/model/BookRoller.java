package land.momo.nekocore.model;

import land.momo.nekocore.data.CommerceRepository.Offer;
import java.util.*;
import java.util.random.RandomGenerator;

/** Pure sampling rules, independent of Bukkit registry and wall-clock time. */
public final class BookRoller {
    public record Enchant(String key, int maxLevel) {
        public Enchant { if (maxLevel < 1) throw new IllegalArgumentException("max level"); }
    }
    private BookRoller() {}
    public static List<Offer> roll(List<Enchant> pool, long maximumPrice, long normalPrice, Map<String, Long> prices, RandomGenerator random) {
        List<Enchant> distinct = pool.stream().distinct().toList();
        if (distinct.stream().map(Enchant::key).distinct().count() != distinct.size()) throw new IllegalArgumentException("Duplicate enchantment keys");
        List<Enchant> normal = new ArrayList<>(distinct.stream().filter(e -> e.maxLevel() > 1).toList());
        if (distinct.size() < 8 || normal.size() < 6) throw new IllegalArgumentException("附魔池至少需要 8 种附魔，其中 6 种的原版最高等级须大于 I");
        List<Offer> offers = new ArrayList<>(); Set<String> selected = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            Enchant enchant = normal.remove(random.nextInt(normal.size())); selected.add(enchant.key());
            int level = random.nextInt(1, Math.min(2, enchant.maxLevel() - 1) + 1);
            offers.add(new Offer(i + 2, enchant.key(), level, price(prices, enchant.key(), normalPrice), false));
        }
        List<Enchant> maximum = new ArrayList<>(distinct.stream().filter(e -> !selected.contains(e.key())).toList());
        for (int i = 0; i < 2; i++) {
            Enchant enchant = maximum.remove(random.nextInt(maximum.size()));
            offers.add(new Offer(i, enchant.key(), enchant.maxLevel(), price(prices, enchant.key(), maximumPrice), true));
        }
        offers.sort(Comparator.comparingInt(Offer::slot)); return List.copyOf(offers);
    }
    private static long price(Map<String, Long> prices, String key, long fallback) {
        return prices.getOrDefault(key, prices.getOrDefault(key.replace("minecraft:", ""), fallback));
    }
}
