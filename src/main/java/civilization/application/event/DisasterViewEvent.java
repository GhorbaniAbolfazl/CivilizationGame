package civilization.application.event;

import civilization.model.disaster.DisasterType;
import civilization.model.map.HexCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Fog-safe presentation data. Hidden events contain neither type nor coordinates. */
public record DisasterViewEvent(Optional<DisasterType> type, Set<HexCoord> affectedHexes,
        boolean visible, String message) implements GameEvent {
    public DisasterViewEvent {
        type = Objects.requireNonNull(type, "type");
        affectedHexes = Set.copyOf(Objects.requireNonNull(affectedHexes, "affectedHexes"));
        Objects.requireNonNull(message, "message");
        if (!visible && (type.isPresent() || !affectedHexes.isEmpty())) {
            throw new IllegalArgumentException("Hidden disaster presentation leaks map knowledge");
        }
    }
}
