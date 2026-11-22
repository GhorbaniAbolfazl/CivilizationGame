package civilization.model.combat;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import java.util.Objects;

/** Immutable evidence used by mission detection; UI input can never manufacture a kill. */
public record CombatKillRecord(UnitId unitId, FactionId defeatedOwner,
        HexCoord location, int turnNumber) implements java.io.Serializable {
    public CombatKillRecord {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(defeatedOwner, "defeatedOwner");
        Objects.requireNonNull(location, "location");
        if (turnNumber < 1) throw new IllegalArgumentException("Turn must be positive");
    }
}
