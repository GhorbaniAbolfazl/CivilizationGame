package civilization.application.query;

import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.identity.FactionId;

import java.util.Objects;

public record UnitSnapshot(UnitId id, UnitType type, HexCoord position, int remainingAp,
        FactionId owner, int health, int maximumHealth, boolean embarked) {
    public UnitSnapshot(UnitId id, UnitType type, HexCoord position, int remainingAp) {
        this(id, type, position, remainingAp, FactionId.PLAYER, 0, 0, false);
    }
    public UnitSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        if (remainingAp < 0 || health < 0 || maximumHealth < 0 || health > maximumHealth) {
            throw new IllegalArgumentException("Invalid unit snapshot values");
        }
    }

    public boolean military() { return type.isMilitary(); }
}
