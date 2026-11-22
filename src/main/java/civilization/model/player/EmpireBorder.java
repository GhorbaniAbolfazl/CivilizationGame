package civilization.model.player;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class EmpireBorder implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Set<HexCoord> ownedHexes = new HashSet<>();

    public EmpireBorder(Set<HexCoord> initialHexes, GameMap gameMap) {
        Objects.requireNonNull(gameMap, "gameMap");
        Set<HexCoord> copy = Set.copyOf(Objects.requireNonNull(initialHexes, "initialHexes"));
        if (copy.stream().anyMatch(coordinate -> !gameMap.contains(coordinate))) {
            throw new IllegalArgumentException("Territory cannot contain coordinates outside the map");
        }
        if (copy.stream().anyMatch(coordinate -> gameMap.requireTile(coordinate).terrain()
                == civilization.model.map.TerrainType.MOUNTAIN_RANGE)) {
            throw new IllegalArgumentException("Mountain ranges cannot belong to territory");
        }
        ownedHexes.addAll(copy);
    }

    public boolean owns(HexCoord coordinate) {
        return ownedHexes.contains(coordinate);
    }

    public void claimAll(Set<HexCoord> coordinates, GameMap gameMap) {
        Objects.requireNonNull(gameMap, "gameMap");
        Set<HexCoord> copy = Set.copyOf(Objects.requireNonNull(coordinates, "coordinates"));
        if (copy.stream().anyMatch(coordinate -> !gameMap.contains(coordinate))) {
            throw new IllegalArgumentException("Claim cannot contain coordinates outside the map");
        }
        if (copy.stream().anyMatch(coordinate -> gameMap.requireTile(coordinate).terrain()
                == civilization.model.map.TerrainType.MOUNTAIN_RANGE)) {
            throw new IllegalArgumentException("Mountain ranges cannot belong to territory");
        }
        ownedHexes.addAll(copy);
    }

    public Set<HexCoord> ownedHexes() {
        return Collections.unmodifiableSet(ownedHexes);
    }

    // PHASE 3 - Added in Phase 3
    public void releaseAll(Set<HexCoord> coordinates) {
        ownedHexes.removeAll(Set.copyOf(Objects.requireNonNull(coordinates, "coordinates")));
    }

    // PHASE 3 - Added in Phase 3
    public void clear() { ownedHexes.clear(); }
}
