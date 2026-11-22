package civilization.model.disaster;

import civilization.model.map.HexCoord;
import java.util.Objects;
import java.util.Set;

public record DisasterOccurrence(
        DisasterId id,
        DisasterType type,
        HexCoord center,
        Set<HexCoord> affectedHexes,
        int occurredOnTurn,
        int effectTurns) implements java.io.Serializable {
    public DisasterOccurrence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(center, "center");
        affectedHexes = Set.copyOf(Objects.requireNonNull(affectedHexes, "affectedHexes"));
        if (!affectedHexes.contains(center)) throw new IllegalArgumentException("Affected area must include center");
        if (occurredOnTurn < 1 || effectTurns < 0) throw new IllegalArgumentException("Invalid disaster duration");
    }
}
