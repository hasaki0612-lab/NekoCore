package land.momo.nekocore.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocationFormatterTest {
    @Test void mainlandUsesOnlyShortProvinceAndNeverCity() {
        assertEquals("浙江", show("CN", "中国", "ZJ"));
        assertEquals("上海", show("CN", "中国", "SH"));
        assertEquals("北京", show("CN", "中国", "BJ"));
        assertEquals("新疆", show("CN", "中国", "XJ"));
        assertEquals("内蒙古", show("CN", "中国", "NM"));
        assertEquals("", show("CN", "中国", "UNKNOWN"));
        assertEquals("浙江", LocationFormatter.display("CN", "中国", "", "浙江省", true, true));
        assertEquals("新疆", LocationFormatter.display("CN", "中国", "", "新疆维吾尔自治区", true, true));
        assertEquals("", LocationFormatter.display("CN", "中国", "", "丽水市", true, true));
    }
    @Test void territoriesAndForeignCountriesStayCoarseAndChinese() {
        assertEquals("香港", show("HK", "香港", ""));
        assertEquals("澳门", show("MO", "澳门", ""));
        assertEquals("台湾", show("TW", "台湾", ""));
        assertEquals("日本", show("JP", "日本", "13"));
        assertEquals("美国", show("US", "美国", "CA"));
        assertEquals("美国", show("US", "United States", "CA"));
        assertEquals("", LocationFormatter.display("JP", "日本", "13", true, false));
    }
    @Test void unresolvedValuesNeverLeakCodesOrUnknownLabels() {
        assertEquals("", show(null, null, null));
        assertEquals("", show("ZZ", "", ""));
        assertEquals("", LocationFormatter.display("CN", "中国", "ZJ", false, true));
    }
    private String show(String country, String name, String subdivision) {
        return LocationFormatter.display(country, name, subdivision, true, true);
    }
}
