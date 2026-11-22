package civilization.model.building;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;
import civilization.model.common.Health;

import java.util.Objects;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.Instant;

// PHASE 3 - Added in Phase 3
public abstract sealed class Building implements java.io.Serializable permits TownHall, ProductionBuilding, Settlement,
        MilitaryStable, Bazaar, Dock, Monument, Apothecary {
    private static final long serialVersionUID = 1L;
    private static final int FAILURE_TURNS_BEFORE_BREAKAGE = 3;

    private final BuildingId id;
    private final BuildingType type;
    private final FactionId ownerId;
    private final HexCoord position;
    private final Health health;
    private BuildingStatus status = BuildingStatus.ACTIVE;
    private int consecutiveUnpaidUpkeepTurns;
    // PHASE 3 - Added in Phase 3
    private Instant createdAt;

    protected Building(BuildingId id, BuildingType type, HexCoord position) {
        this(id, type, position, FactionId.PLAYER, 100);
    }

    protected Building(
            BuildingId id, BuildingType type, HexCoord position, FactionId ownerId) {
        this(id, type, position, ownerId, 100);
    }

    protected Building(
            BuildingId id, BuildingType type, HexCoord position, FactionId ownerId,
            int maximumHealth) {
        this(id, type, position, ownerId, new Health(maximumHealth));
    }

    protected Building(
            BuildingId id, BuildingType type, HexCoord position, FactionId ownerId,
            Health health) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.health = Objects.requireNonNull(health, "health");
        // PHASE 3 - Added in Phase 3
        this.createdAt = Instant.now();
    }

    public final BuildingId id() {
        return id;
    }

    public final BuildingType type() {
        return type;
    }

    public final FactionId ownerId() {
        return ownerId;
    }

    public final HexCoord position() {
        return position;
    }

    public final Health health() {
        return health;
    }

    public final BuildingStatus status() {
        return status;
    }

    // PHASE 3 - Added in Phase 3
    public final Instant createdAt() {
        return createdAt;
    }

    public final boolean isActive() {
        return status == BuildingStatus.ACTIVE;
    }

    /** Marks a zero-HP structure as destroyed. Registry removal is handled by its aggregate. */
    public final void destroy() {
        if (!health.isDepleted()) {
            throw new IllegalStateException("A building can only be destroyed at zero HP");
        }
        status = BuildingStatus.DESTROYED;
    }

    /** Irreversible owner-requested removal; combat still requires zero HP via {@link #destroy()}. */
    public final void demolish() {
        if (status == BuildingStatus.DESTROYED) {
            throw new IllegalStateException("Building is already destroyed");
        }
        health.damage(health.current());
        status = BuildingStatus.DESTROYED;
    }

    public final int consecutiveUnpaidUpkeepTurns() {
        return consecutiveUnpaidUpkeepTurns;
    }

    /** Called when a paid upkeep turn resets the consecutive-failure sequence. */
    public final void resetUnpaidUpkeepSequence() {
        requireActive();
        consecutiveUnpaidUpkeepTurns = 0;
    }

    /** Returns true if this failure changed the building to BROKEN. */
    public final boolean recordUnpaidUpkeep() {
        requireActive();
        consecutiveUnpaidUpkeepTurns++;
        if (consecutiveUnpaidUpkeepTurns >= FAILURE_TURNS_BEFORE_BREAKAGE) {
            status = BuildingStatus.BROKEN;
            return true;
        }
        return false;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Broken building cannot process upkeep");
        }
    }

    // PHASE 3 - Added in Phase 3
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        // PHASE 3 - Added in Phase 3
        if (createdAt == null) createdAt = Instant.EPOCH;
    }
}
