package civilization.application.query;

import civilization.model.building.BuildingId;
import civilization.model.building.BuildingStatus;
import civilization.model.building.BuildingType;
import civilization.model.map.HexCoord;

import java.util.Objects;

public record BuildingSnapshot(
        BuildingId id,
        BuildingType type,
        HexCoord position,
        BuildingStatus status,
        int stationedWorkers,
        int workerCapacity) {

    public BuildingSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(status, "status");
        if (stationedWorkers < 0 || workerCapacity < 0 || stationedWorkers > workerCapacity) {
            throw new IllegalArgumentException("Invalid Worker occupancy");
        }
    }
}
