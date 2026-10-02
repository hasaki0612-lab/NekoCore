package land.momo.nekocore.config;

import java.util.*;

/** 精确、前缀、包含、编辑距离排序；最多十项，不维护猜测的中文翻译。 */
public final class LookupNames {
    private LookupNames() {}
    public static List<String> search(String keyword, Collection<String> available, int maximum) {
        String query = keyword.trim().toUpperCase(Locale.ROOT);
        if (query.startsWith("MINECRAFT:")) query = query.substring("MINECRAFT:".length());
        if (query.isEmpty() || !query.matches("[A-Z0-9_]+")) return List.of();
        final String needle = query;
        int threshold = Math.max(2, needle.length() / 3);
        return available.stream().distinct().filter(name -> name.contains(needle) || distance(needle, name) <= threshold)
                .sorted(Comparator.comparingInt((String name) -> rank(needle, name))
                        .thenComparingInt(name -> rank(needle, name) == 3 ? distance(needle, name) : 0)
                        .thenComparing(String::compareTo))
                .limit(Math.max(0, Math.min(10, maximum))).toList();
    }
    private static int rank(String needle, String name) {
        return name.equals(needle) ? 0 : name.startsWith(needle) ? 1 : name.contains(needle) ? 2 : 3;
    }
    public static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }
}
