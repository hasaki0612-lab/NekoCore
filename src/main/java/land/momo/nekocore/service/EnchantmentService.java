package land.momo.nekocore.service;

import land.momo.nekocore.NekoCorePlugin;
import land.momo.nekocore.config.FeatureSettings;
import land.momo.nekocore.data.CommerceRepository;
import land.momo.nekocore.model.BookRoller;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitTask;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGenerator;

/** Only one daily task; database batches survive restart, reload and disabled shop periods. */
public final class EnchantmentService {
    private final NekoCorePlugin plugin;
    private CommerceRepository.Batch batch;
    private BukkitTask timer;
    private long generation;
    public EnchantmentService(NekoCorePlugin plugin) { this.plugin = plugin; }
    public CommerceRepository.Batch current() { return batch; }
    public static List<BookRoller.Enchant> validatePool(FeatureSettings.Books settings) {
        List<BookRoller.Enchant> pool = new ArrayList<>();
        for (String value : settings.pool()) {
            NamespacedKey key = NamespacedKey.fromString(value.contains(":") ? value : "minecraft:" + value);
            if (key == null) throw new IllegalArgumentException("无效附魔键：" + value);
            Enchantment enchantment = Registry.ENCHANTMENT.get(key);
            if (enchantment == null) throw new IllegalArgumentException("找不到附魔：" + value);
            if (!settings.excluded().contains(value) && !settings.excluded().contains(key.toString())
                    && !settings.excluded().contains(key.getKey()))
                pool.add(new BookRoller.Enchant(key.toString(), enchantment.getMaxLevel()));
        }
        // Validate uniqueness and availability before accepting a reload.
        BookRoller.roll(pool, settings.maximumPrice(), settings.normalPrice(), settings.prices(), new Random(0));
        return List.copyOf(pool);
    }
    public CompletableFuture<CommerceRepository.Batch> refresh(boolean force) {
        var settings = plugin.features().books();
        var candidates = BookRoller.roll(validatePool(settings), settings.maximumPrice(), settings.normalPrice(), settings.prices(), RandomGenerator.getDefault());
        long token = generation;
        var future = plugin.commerce().ensureBatch(candidates, force);
        future.whenComplete((next, error) -> plugin.onMain(() -> {
            if (generation != token) return;
            if (error == null) batch = next;
            else plugin.getLogger().log(java.util.logging.Level.SEVERE, "附魔书刷新失败，保留旧批次并于 60 秒后重试", NekoCorePlugin.unwrap(error));
            schedule(error == null ? nextRefreshSeconds(Instant.now()) : 60);
        }));
        return future;
    }
    public void restart() { stop(); refresh(false); }
    public void stop() { generation++; if (timer != null) { timer.cancel(); timer = null; } }
    private void schedule(long seconds) {
        if (timer != null) timer.cancel();
        timer = Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(false), Math.max(1, seconds) * 20L);
    }
    public static long nextRefreshSeconds(Instant now) {
        ZonedDateTime local = now.atZone(CommerceRepository.ZONE);
        ZonedDateTime next = local.toLocalDate().atTime(4, 0).atZone(CommerceRepository.ZONE);
        if (!next.isAfter(local)) next = next.plusDays(1);
        return Math.max(1, (Duration.between(now, next.toInstant()).toMillis() + 999) / 1000);
    }
    public static ItemStack item(CommerceRepository.Offer offer) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(Objects.requireNonNull(NamespacedKey.fromString(offer.enchantment())));
        if (enchantment == null || offer.level() > enchantment.getMaxLevel()) throw new IllegalArgumentException("已保存附魔不被当前服务端支持");
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        var meta = (EnchantmentStorageMeta) stack.getItemMeta();
        meta.addStoredEnchant(enchantment, offer.level(), false); stack.setItemMeta(meta); return stack;
    }
}
