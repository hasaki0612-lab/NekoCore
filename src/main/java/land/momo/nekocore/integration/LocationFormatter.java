package land.momo.nekocore.integration;

import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Turns GeoIP records into deliberately coarse, privacy-friendly Chinese labels. */
public final class LocationFormatter {
    private static final Map<String, String> CHINA_SUBDIVISIONS = Map.ofEntries(
            Map.entry("AH", "安徽"), Map.entry("BJ", "北京"), Map.entry("CQ", "重庆"),
            Map.entry("FJ", "福建"), Map.entry("GD", "广东"), Map.entry("GS", "甘肃"),
            Map.entry("GX", "广西"), Map.entry("GZ", "贵州"), Map.entry("HA", "河南"),
            Map.entry("HB", "湖北"), Map.entry("HE", "河北"), Map.entry("HI", "海南"),
            Map.entry("HK", "香港"), Map.entry("HL", "黑龙江"), Map.entry("HN", "湖南"),
            Map.entry("JL", "吉林"), Map.entry("JS", "江苏"), Map.entry("JX", "江西"),
            Map.entry("LN", "辽宁"), Map.entry("MO", "澳门"), Map.entry("NM", "内蒙古"),
            Map.entry("NX", "宁夏"), Map.entry("QH", "青海"), Map.entry("SC", "四川"),
            Map.entry("SD", "山东"), Map.entry("SH", "上海"), Map.entry("SN", "陕西"),
            Map.entry("SX", "山西"), Map.entry("TJ", "天津"), Map.entry("TW", "台湾"),
            Map.entry("XJ", "新疆"), Map.entry("XZ", "西藏"), Map.entry("YN", "云南"),
            Map.entry("ZJ", "浙江")
    );
    private static final Map<String, String> TERRITORIES = Map.of("HK", "香港", "MO", "澳门", "TW", "台湾");
    private static final Set<String> CHINA_SHORT_NAMES = Set.copyOf(CHINA_SUBDIVISIONS.values());
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private LocationFormatter() {}

    public static String display(String countryCode, String chineseCountry, String subdivisionCode,
                                 boolean showChinaProvince, boolean showForeignCountry) {
        return display(countryCode, chineseCountry, subdivisionCode, null, showChinaProvince, showForeignCountry);
    }

    public static String display(String countryCode, String chineseCountry, String subdivisionCode,
                                 String chineseSubdivision, boolean showChinaProvince, boolean showForeignCountry) {
        String country = upper(countryCode);
        if (country.equals("CN")) {
            if (!showChinaProvince) return "";
            String byCode = CHINA_SUBDIVISIONS.get(upper(subdivisionCode));
            return byCode == null ? chinaSubdivision(chineseSubdivision) : byCode;
        }
        if (!showForeignCountry) return "";
        String territory = TERRITORIES.get(country);
        if (territory != null) return territory;
        String name = chineseCountry == null ? "" : chineseCountry.strip();
        if (containsHan(name)) return name;
        if (!ISO_COUNTRIES.contains(country)) return "";
        String fallback = Locale.of("", country).getDisplayCountry(Locale.SIMPLIFIED_CHINESE).strip();
        return containsHan(fallback) ? fallback : "";
    }

    private static String chinaSubdivision(String value) {
        if (value == null) return "";
        String name = value.strip();
        name = switch (name) {
            case "新疆维吾尔自治区" -> "新疆";
            case "广西壮族自治区" -> "广西";
            case "内蒙古自治区" -> "内蒙古";
            case "宁夏回族自治区" -> "宁夏";
            case "西藏自治区" -> "西藏";
            case "香港特别行政区" -> "香港";
            case "澳门特别行政区" -> "澳门";
            default -> name.endsWith("省") || name.endsWith("市") ? name.substring(0, name.length() - 1) : name;
        };
        return CHINA_SHORT_NAMES.contains(name) ? name : "";
    }

    private static String upper(String value) { return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT); }
    private static boolean containsHan(String value) {
        return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }
}
