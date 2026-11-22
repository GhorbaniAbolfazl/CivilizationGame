package civilization.view.animation;

import civilization.model.unit.UnitId;
import civilization.view.render.WorldPoint;

import java.util.Objects;

/** Presentation-only interpolation for one already-committed logical move. */
public final class UnitMoveAnimation {
    private final UnitId unitId;
    private final WorldPoint from;
    private final WorldPoint to;
    private final long startNanos;
    private final long durationNanos;
    private final long stateVersion;

    public UnitMoveAnimation(
            UnitId unitId,
            WorldPoint from,
            WorldPoint to,
            long startNanos,
            long durationNanos,
            long stateVersion) {
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        if (startNanos < 0 || durationNanos <= 0 || stateVersion <= 0) {
            throw new IllegalArgumentException("Animation timing/version values are invalid");
        }
        this.startNanos = startNanos;
        this.durationNanos = durationNanos;
        this.stateVersion = stateVersion;
    }

    public UnitId unitId() {
        return unitId;
    }

    public long stateVersion() {
        return stateVersion;
    }

    public double progressAt(long nowNanos) {
        if (nowNanos <= startNanos) {
            return 0.0;
        }
        double linear = Math.min(1.0, (nowNanos - startNanos) / (double) durationNanos);
        return linear * linear * (3.0 - 2.0 * linear);
    }

    public WorldPoint positionAt(long nowNanos) {
        return from.interpolate(to, progressAt(nowNanos));
    }

    public boolean isCompleteAt(long nowNanos) {
        return nowNanos - startNanos >= durationNanos;
    }
}
