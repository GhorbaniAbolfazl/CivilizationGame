package civilization.model.unit;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;
import civilization.model.common.Health;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

public abstract sealed class Unit implements java.io.Serializable
        permits ExplorerUnit, BuilderUnit, WorkerUnit, BorderExpanderUnit, MilitaryUnit {
    private static final long serialVersionUID = 1L;
    private final UnitId id;
    private final UnitType type;
    private final FactionId ownerId;
    private final ActionPoints actionPoints;
    private HexCoord position;
    private UnitStatus status;
    private boolean embarked;
    // PHASE 3 - Added in Phase 3
    private Instant createdAt;
    /**
     * Phase 1 civilian units did not have combat HP. Phase 2 disasters, however, apply
     * numeric HP damage to military and non-military units. Keep that durability here
     * without moving the established military-health serialization field.
     */
    private Health civilianHealth;

    protected Unit(UnitId id, UnitType type, HexCoord position, ActionPoints actionPoints) {
        this(id, type, position, actionPoints, FactionId.PLAYER);
    }

    protected Unit(
            UnitId id,
            UnitType type,
            HexCoord position,
            ActionPoints actionPoints,
            FactionId ownerId) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.position = Objects.requireNonNull(position, "position");
        this.actionPoints = Objects.requireNonNull(actionPoints, "actionPoints");
        this.status = UnitStatus.ACTIVE;
        this.civilianHealth = type.isMilitary() ? null : new Health(100);
        // PHASE 3 - Added in Phase 3
        this.createdAt = Instant.now();
    }

    public final UnitId id() {
        return id;
    }

    public final UnitType type() {
        return type;
    }

    public final FactionId ownerId() {
        return ownerId;
    }

    public final HexCoord position() {
        return position;
    }

    public final ActionPoints actionPoints() {
        return actionPoints;
    }

    public final UnitStatus status() {
        return status;
    }

    // PHASE 3 - Added in Phase 3
    public final Instant createdAt() {
        return createdAt;
    }

    public final boolean isEmbarked() { return embarked; }

    /** HP used by disasters; military retains its specification-defined 1/2 HP scale. */
    public final Optional<Health> healthState() {
        if (this instanceof MilitaryUnit military) return Optional.of(military.health());
        return Optional.ofNullable(civilianHealth);
    }

    public final void setEmbarked(boolean embarked) {
        requireActive();
        this.embarked = embarked;
    }

    public final boolean isActive() {
        return status != UnitStatus.REMOVED;
    }

    public final void moveTo(HexCoord destination) {
        requireActive();
        position = Objects.requireNonNull(destination, "destination");
    }

    public final void markIdle() {
        requireActive();
        status = UnitStatus.IDLE;
    }

    protected final void markStationed() {
        requireActive();
        status = UnitStatus.STATIONED;
    }

    public final void markActive() {
        requireActive();
        status = UnitStatus.ACTIVE;
    }

    public final void remove() {
        status = UnitStatus.REMOVED;
    }

    protected final void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Removed units cannot be changed");
        }
    }

    /** Adds the new civilian durability field safely when loading older v1/v2 saves. */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        if (type != null && !type.isMilitary() && civilianHealth == null) {
            civilianHealth = new Health(100);
        }
        // PHASE 3 - Added in Phase 3
        if (createdAt == null) createdAt = Instant.EPOCH;
    }
}
