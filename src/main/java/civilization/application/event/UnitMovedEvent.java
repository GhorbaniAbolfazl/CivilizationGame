package civilization.application.event;

import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;

import java.util.Objects;

/** Immutable fact emitted after one logical movement step has committed. */
public record UnitMovedEvent(
        UnitId unitId,
        HexCoord from,
        HexCoord to,
        long stateVersion) implements GameEvent {

    public UnitMovedEvent {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isAdjacentTo(to)) {
            throw new IllegalArgumentException("A movement event must describe one adjacent step");
        }
        if (stateVersion <= 0) {
            throw new IllegalArgumentException("State version must be positive");
        }
    }
}
