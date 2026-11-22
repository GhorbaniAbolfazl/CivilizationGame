package civilization.application.query;

import civilization.model.building.BuildingId;
import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitStatus;
import civilization.model.unit.UnitType;

import java.util.Objects;
import java.util.Optional;

public record UnitDetailSnapshot(
        UnitId id,
        UnitType type,
        HexCoord position,
        UnitStatus status,
        int remainingAp,
        int apCapacity,
        int builderCharges,
        BuildingId stationedAt,
        int health,
        int maximumHealth,
        int attackRange,
        boolean embarked) {

    public UnitDetailSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(status, "status");
        if (remainingAp < 0 || apCapacity <= 0 || remainingAp > apCapacity || builderCharges < 0
                || health < 0 || maximumHealth < 0 || health > maximumHealth || attackRange < 0) {
            throw new IllegalArgumentException("Invalid unit detail values");
        }
    }

    public Optional<BuildingId> station() {
        return Optional.ofNullable(stationedAt);
    }
}
