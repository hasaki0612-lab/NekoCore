package land.momo.nekocore.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.math.BigInteger;
import java.util.*;

/** 1.2 modules, kept separate from the stable 1.1 settings and independently validated on reload. */
public record FeatureSettings(LevelShop levelShop, Shop shop, Books books, Bag bag, Tpn tpn, NameTags nameTags) {
    public record Title(String id, String name, String prefix, Material icon, int slot, int rank, long price,
                        boolean autoCheckin, boolean coloredChat, int cooldown, double afkExpMultiplier,
                        List<String> description) {
        public Title { description = List.copyOf(description); }
    }
    public record LevelShop(boolean enabled, Set<String> worlds, List<Title> titles, String autoPermission,
                            String colorPermission, String fastPermission) {
        public LevelShop { worlds = Set.copyOf(worlds); titles = List.copyOf(titles); }
        public Title title(String id) { return titles.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    }
    public record Category(String id, String name, Material icon, int slot) {}
    public record Product(String id, String category, Material material, String name, long price, boolean sellable,
                          int buyLimit, int sellLimit, int slot) {}
    public record Shop(boolean enabled, int rows, List<Category> categories, List<Product> products,
                       int sellNumerator, int sellDenominator, int inputTimeout, int maximumInput,
                       List<Integer> productSlots, int previousSlot, int nextSlot, int infoSlot) {
        public Shop { categories = List.copyOf(categories); products = List.copyOf(products); productSlots = List.copyOf(productSlots); }
        public long sellPrice(long buyPrice) {
            return BigInteger.valueOf(buyPrice).multiply(BigInteger.valueOf(sellNumerator))
                    .divide(BigInteger.valueOf(sellDenominator)).longValueExact();
        }
        public Product product(String id) { return products.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null); }
    }
    public record Books(List<String> pool, Set<String> excluded, long maximumPrice, long normalPrice,
                        Map<String, Long> prices) {
        public Books { pool = List.copyOf(pool); excluded = Set.copyOf(excluded); prices = Map.copyOf(prices); }
    }
    public record Bag(boolean enabled, Set<String> writableWorlds, int level27, int level36, int noticeSeconds) {
        public Bag { writableWorlds = Set.copyOf(writableWorlds); }
        public int capacity(int level) { return level >= level36 ? 36 : level >= level27 ? 27 : 18; }
        public boolean writable(String world) { return writableWorlds.contains(world); }
    }
    public record Tpn(boolean enabled, int timeout, int cooldown, int fastCooldown, String sound,
                      float volume, float pitch, int particles) {}
    public record NameTags(boolean enabled, int checkSeconds) {}

    public static FeatureSettings load(File file) throws Exception {
        YamlConfiguration c = new YamlConfiguration(); c.load(file); return load(c);
    }
    public static FeatureSettings load(ConfigurationSection c) {
        List<Title> titles = new ArrayList<>(); Set<Integer> titleSlots = new HashSet<>(), ranks = new HashSet<>();
        for (String id : section(c, "levelshop.titles").getKeys(false)) {
            identifier(id); String p = "levelshop.titles." + id + ".";
            int slot = integer(c, p + "slot", 0, 17), rank = integer(c, p + "rank", 1, 100);
            if (!titleSlots.add(slot) || !ranks.add(rank)) bad(p, "槽位和等级次序不能重复");
            titles.add(new Title(id, string(c, p + "name"), string(c, p + "prefix"), material(c, p + "material"), slot, rank,
                    number(c, p + "price", 0, Long.MAX_VALUE / 2), bool(c, p + "auto-checkin"), bool(c, p + "colored-chat"),
                    integer(c, p + "tpn-cooldown-seconds", 0, 86400), ratio(c, p + "afk-exp-multiplier", 0, 100),
                    strings(c, p + "description")));
        }
        if (titles.size() != 3) bad("levelshop.titles", "请配置三个头衔档位");
        titles.sort(Comparator.comparingInt(Title::rank));
        LevelShop levelShop = new LevelShop(bool(c, "levelshop.enabled"), new HashSet<>(strings(c, "levelshop.worlds")), titles,
                permission(c, "levelshop.permissions.auto-checkin"), permission(c, "levelshop.permissions.colored-chat"),
                permission(c, "levelshop.permissions.fast-tpn"));
        int rows = integer(c, "store.category-rows", 2, 6);
        List<Category> categories = new ArrayList<>(); Set<Integer> slots = new HashSet<>(); Set<String> categoryIds = new HashSet<>();
        for (String id : section(c, "store.categories").getKeys(false)) {
            identifier(id); String p = "store.categories." + id + ".";
            int slot = integer(c, p + "slot", 0, (rows - 1) * 9 - 1);
            if (!slots.add(slot)) bad(p, "分类槽位重复");
            categories.add(new Category(id, string(c, p + "name"), material(c, p + "material"), slot)); categoryIds.add(id);
        }
        if (!categoryIds.contains("enchantments")) bad("store.categories", "请保留 enchantments 附魔书分类");
        int sellLimit = integer(c, "store.default-sell-limit", 1, 1000000);
        List<Product> products = new ArrayList<>(); Map<String, Set<Integer>> productSlots = new HashMap<>();
        for (String id : section(c, "store.products").getKeys(false)) {
            identifier(id); String p = "store.products." + id + "."; String category = string(c, p + "category");
            if (!categoryIds.contains(category) || category.equals("enchantments")) bad(p, "商品分类不存在或使用了动态分类");
            int slot = c.contains(p + "slot") ? integer(c, p + "slot", 0, 44) : -1;
            if (slot >= 0 && !productSlots.computeIfAbsent(category, ignored -> new HashSet<>()).add(slot)) bad(p, "商品槽位重复");
            products.add(new Product(id, category, material(c, p + "material"), string(c, p + "name"), number(c, p + "price", 1, Long.MAX_VALUE / 1000000),
                    bool(c, p + "sell"), c.contains(p + "buy-limit") ? integer(c, p + "buy-limit", 1, 1000000) : -1,
                    c.contains(p + "sell-limit") ? integer(c, p + "sell-limit", 1, 1000000) : sellLimit, slot));
        }
        int numerator = integer(c, "store.sell-price.numerator", 0, 1000), denominator = integer(c, "store.sell-price.denominator", 1, 1000);
        if (numerator > denominator) bad("store.sell-price", "卖价不能高于买价");
        List<Integer> layout = integers(c, "store.product-layout.slots", 0, 44);
        if (layout.isEmpty() || new HashSet<>(layout).size() != layout.size()) bad("store.product-layout.slots", "槽位不可为空或重复");
        int previous = integer(c, "store.product-layout.previous-slot", 45, 52);
        int next = integer(c, "store.product-layout.next-slot", 45, 52);
        int info = integer(c, "store.product-layout.info-slot", 45, 52);
        if (Set.of(previous, next, info).size() != 3) bad("store.product-layout", "翻页与提示槽位不能重复");
        Shop shop = new Shop(bool(c, "store.enabled"), rows, categories, products, numerator, denominator,
                integer(c, "store.input-timeout-seconds", 5, 300), integer(c, "store.maximum-custom-quantity", 1, 1000000),
                layout, previous, next, info);
        Map<String, Long> prices = new HashMap<>();
        for (String key : section(c, "store.enchantments.prices").getKeys(false))
            prices.put(key, number(c, "store.enchantments.prices." + key, 1, Long.MAX_VALUE / 1000000));
        Books books = new Books(strings(c, "store.enchantments.pool"), new HashSet<>(strings(c, "store.enchantments.excluded")),
                number(c, "store.enchantments.maximum-price", 1, Long.MAX_VALUE / 1000000),
                number(c, "store.enchantments.normal-price", 1, Long.MAX_VALUE / 1000000), prices);
        if (books.pool().isEmpty()) bad("store.enchantments.pool", "附魔池不能为空");
        Bag bag = new Bag(bool(c, "bag.enabled"), new HashSet<>(strings(c, "bag.writable-worlds")),
                integer(c, "bag.unlock-levels.27", 2, 1000000), integer(c, "bag.unlock-levels.36", 3, 1000000),
                integer(c, "bag.readonly-notice-seconds", 1, 60));
        if (bag.level27() >= bag.level36()) bad("bag.unlock-levels", "36 格等级应高于 27 格");
        String sound = string(c, "tpn.sound.name");
        if (!sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) bad("tpn.sound.name", "音效需使用命名空间");
        Tpn tpn = new Tpn(bool(c, "tpn.enabled"), integer(c, "tpn.timeout-seconds", 5, 300), integer(c, "tpn.cooldown-seconds", 0, 86400),
                integer(c, "tpn.fast-cooldown-seconds", 0, 86400), sound, decimal(c, "tpn.sound.volume", 0, 2),
                decimal(c, "tpn.sound.pitch", 0.5f, 2), integer(c, "tpn.particle-count", 0, 64));
        return new FeatureSettings(levelShop, shop, books, bag, tpn,
                new NameTags(bool(c, "nametag.enabled"), integer(c, "nametag.scoreboard-check-seconds", 1, 60)));
    }
    private static void identifier(String value) { if (!value.matches("[a-z0-9_-]{1,48}")) bad(value, "标识符只使用英文小写、数字、短横线和下划线"); }
    private static ConfigurationSection section(ConfigurationSection c, String p) {
        ConfigurationSection value = c.getConfigurationSection(p); if (value == null) bad(p, "需要配置分组"); return value;
    }
    private static String permission(ConfigurationSection c, String p) {
        String value = string(c, p); if (!value.matches("[a-z0-9_.-]+")) bad(p, "权限节点无效"); return value;
    }
    private static String string(ConfigurationSection c, String p) {
        if (!c.isString(p) || c.getString(p).isBlank()) bad(p, "不能为空"); return c.getString(p);
    }
    private static List<String> strings(ConfigurationSection c, String p) {
        if (!(c.get(p) instanceof List<?> list)) { bad(p, "需要列表"); return List.of(); }
        if (list.stream().anyMatch(x -> !(x instanceof String s) || s.isBlank())) bad(p, "列表内容不能为空");
        return list.stream().map(String.class::cast).toList();
    }
    private static List<Integer> integers(ConfigurationSection c, String p, int min, int max) {
        if (!(c.get(p) instanceof List<?> list)) { bad(p, "需要整数列表"); return List.of(); }
        List<Integer> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Integer number) || number < min || number > max) bad(p, "槽位范围无效");
            result.add((Integer) value);
        }
        return List.copyOf(result);
    }
    private static boolean bool(ConfigurationSection c, String p) { if (!(c.get(p) instanceof Boolean)) bad(p, "需要 true/false"); return c.getBoolean(p); }
    private static int integer(ConfigurationSection c, String p, int min, int max) { return (int) number(c, p, min, max); }
    private static long number(ConfigurationSection c, String p, long min, long max) {
        Object value = c.get(p);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < min || ((Number) value).longValue() > max)
            bad(p, "整数需在 " + min + " 到 " + max + " 之间");
        return ((Number) value).longValue();
    }
    private static float decimal(ConfigurationSection c, String p, float min, float max) {
        Object value = c.get(p);
        if (!(value instanceof Number) || !Float.isFinite(((Number) value).floatValue()) || ((Number) value).floatValue() < min || ((Number) value).floatValue() > max)
            bad(p, "数值范围无效");
        return ((Number) value).floatValue();
    }
    private static double ratio(ConfigurationSection c, String p, double min, double max) {
        Object value = c.get(p);
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                || ((Number) value).doubleValue() < min || ((Number) value).doubleValue() > max) bad(p, "数值范围无效");
        return ((Number) value).doubleValue();
    }
    private static Material material(ConfigurationSection c, String p) {
        String configured = string(c, p); Material m = Material.matchMaterial(configured);
        if (m == null || !m.isItem()) bad(p, "未知 Material \"" + configured + "\"" + suggestion(configured)); return m;
    }
    private static String suggestion(String configured) {
        String needle = configured.toUpperCase(Locale.ROOT);
        return Arrays.stream(Material.values()).filter(Material::isItem)
                .min(Comparator.comparingInt(value -> distance(needle, value.name())))
                .filter(value -> distance(needle, value.name()) <= Math.max(2, needle.length() / 3))
                .map(value -> "；你是否想填 \"" + value.name() + "\"？").orElse("");
    }
    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1]; for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) { int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1)); previous = current; }
        return previous[right.length()];
    }
    private static void bad(String p, String message) { throw new IllegalArgumentException(p + ": " + message); }
}
