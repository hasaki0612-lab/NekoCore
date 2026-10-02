package land.momo.nekocore.config;

import io.papermc.paper.registry.RegistryAccess;
import org.bukkit.*;
import org.bukkit.inventory.ItemType;
import org.bukkit.block.BlockType;
import org.mockito.MockedStatic;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 只模拟 Paper 注册表和旧材料转换，保留实际枚举与全部配置验证。 */
final class PaperRegistryFixture implements AutoCloseable {
    private final MockedStatic<RegistryAccess> accessMock;
    private final MockedStatic<Bukkit> bukkit;
    @SuppressWarnings({"unchecked", "rawtypes"})
    PaperRegistryFixture() {
        Map<Object,Registry<?>> registries = new HashMap<>();
        RegistryAccess access = mock(RegistryAccess.class, invocation -> {
            if (invocation.getMethod().getName().equals("getRegistry"))
                return registries.computeIfAbsent(invocation.getArgument(0),
                        ignored -> mock(Registry.class, withSettings().mockMaker("mock-maker-proxy")));
            return RETURNS_DEFAULTS.answer(invocation);
        });
        accessMock = mockStatic(RegistryAccess.class);
        accessMock.when(RegistryAccess::registryAccess).thenReturn(access);
        // Registry 的静态初始化先取得模拟 Access；代理 mock 不要求内联初始化。
        ItemType item = mock(ItemType.class, withSettings().mockMaker("mock-maker-proxy"));
        BlockType block = mock(BlockType.class, withSettings().mockMaker("mock-maker-proxy"));
        BlockType air = mock(BlockType.class, withSettings().mockMaker("mock-maker-proxy"));
        when(air.isAir()).thenReturn(true);
        when(Registry.ITEM.get(any(NamespacedKey.class))).thenReturn(item);
        when(Registry.BLOCK.get(any(NamespacedKey.class))).thenAnswer(invocation ->
                Set.of("air", "cave_air", "void_air").contains(((NamespacedKey)invocation.getArgument(0)).getKey()) ? air : block);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.fromLegacy(any(org.bukkit.material.MaterialData.class), anyBoolean())).thenReturn(Material.STONE);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
    }
    public void close() { bukkit.close(); accessMock.close(); }
}
