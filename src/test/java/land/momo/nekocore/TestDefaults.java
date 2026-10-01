package land.momo.nekocore;

import land.momo.nekocore.config.FeatureSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public final class TestDefaults {
    private TestDefaults() {}
    public static YamlConfiguration yaml() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(TestDefaults.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) { config.load(reader); }
        return config;
    }
    public static FeatureSettings features() throws Exception { return features(yaml()); }
    public static FeatureSettings features(YamlConfiguration config) {
        Material material = mock(Material.class); when(material.isItem()).thenReturn(true);
        when(material.getMaxStackSize()).thenReturn(64);
        try (var materials = mockStatic(Material.class)) {
            materials.when(() -> Material.matchMaterial(anyString())).thenReturn(material);
            return FeatureSettings.load(config);
        }
    }
}
