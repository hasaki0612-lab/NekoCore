package land.momo.nekocore.model;

import java.util.UUID;

public record Home(UUID playerUuid, UUID worldUuid, String worldName, double x, double y,
                   double z, float yaw, float pitch) {
    public Home {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Home coordinates must be finite");
    }
}
