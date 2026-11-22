package civilization.model.world;

import civilization.model.identity.FactionId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Non-player ownership; player ownership remains authoritative in EmpireBorder. */
public final class TerritoryRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<HexCoord, FactionId> owners = new LinkedHashMap<>();

    public void claim(HexCoord coordinate, FactionId owner, GameMap map) {
        var tile = Objects.requireNonNull(map, "map").requireTile(coordinate);
        if (tile.terrain() == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
            throw new IllegalArgumentException("Mountain ranges cannot belong to territory");
        }
        Objects.requireNonNull(owner, "owner");
        if (owner.equals(FactionId.PLAYER)) {
            throw new IllegalArgumentException("Player territory belongs in EmpireBorder");
        }
        owners.put(Objects.requireNonNull(coordinate, "coordinate"), owner);
    }

    public Optional<FactionId> ownerAt(HexCoord coordinate) {
        return Optional.ofNullable(owners.get(Objects.requireNonNull(coordinate, "coordinate")));
    }

    public Optional<FactionId> relinquish(HexCoord coordinate) {
        return Optional.ofNullable(owners.remove(Objects.requireNonNull(coordinate, "coordinate")));
    }

    public Map<HexCoord, FactionId> ownership() { return Collections.unmodifiableMap(owners); }
}
