package civilization.application.query;

import civilization.model.map.HexCoord;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.TerrainType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Visibility-filtered map data. Unknown hexes intentionally contain no terrain secrets. */
public record HexSnapshot(
        HexCoord coordinate,
        boolean discovered,
        boolean currentlyVisible,
        boolean owned,
        TerrainType terrain,
        Map<NaturalResourceType, Integer> remainingResources) {

    public HexSnapshot {
        Objects.requireNonNull(coordinate, "coordinate");
        remainingResources = Map.copyOf(Objects.requireNonNull(
                remainingResources, "remainingResources"));
        if (!discovered && (currentlyVisible || terrain != null || !remainingResources.isEmpty())) {
            throw new IllegalArgumentException("Unknown hexes cannot expose terrain or resources");
        }
        if (remainingResources.values().stream().anyMatch(amount -> amount < 0)) {
            throw new IllegalArgumentException("Resource amounts cannot be negative");
        }
    }

    public Optional<TerrainType> discoveredTerrain() {
        return Optional.ofNullable(terrain);
    }
}
