package land.momo.nekocore.model;

import java.util.UUID;

public record Profile(UUID uuid, String name, long coins, int level, long exp,
                      long playtimeSeconds, long firstJoin, long lastJoin, boolean showIp) {
    public Profile withPlaytime(long seconds) {
        return new Profile(uuid, name, coins, level, exp, seconds, firstJoin, lastJoin, showIp);
    }
}
