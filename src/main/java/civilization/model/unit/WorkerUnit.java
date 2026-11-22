package civilization.model.unit;

import civilization.model.building.BuildingId;
import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

import java.util.Objects;
import java.util.Optional;

public final class WorkerUnit extends Unit {
    private static final long serialVersionUID = 1L;
    private BuildingId stationedAt;

    public WorkerUnit(UnitId id, HexCoord position, ActionPoints actionPoints) {
        super(id, UnitType.WORKER, position, actionPoints);
    }

    public WorkerUnit(
            UnitId id, HexCoord position, ActionPoints actionPoints, FactionId ownerId) {
        super(id, UnitType.WORKER, position, actionPoints, ownerId);
    }

    public Optional<BuildingId> stationedAt() {
        return Optional.ofNullable(stationedAt);
    }

    public boolean isStationed() {
        return stationedAt != null;
    }

    public void stationAt(BuildingId buildingId) {
        requireActive();
        if (stationedAt != null) {
            throw new IllegalStateException("Worker is already stationed");
        }
        stationedAt = Objects.requireNonNull(buildingId, "buildingId");
        markStationed();
    }

    /** Leaves the station without changing AP; station-exit rules are applied by the service. */
    public void leaveStation() {
        requireActive();
        stationedAt = null;
        markIdle();
    }
}
