package civilization.model.world;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorldStructureRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<WorldStructureId, WorldStructure> structures = new LinkedHashMap<>();

    public void add(WorldStructure structure, GameMap map) {
        Objects.requireNonNull(structure, "structure");
        var tile = Objects.requireNonNull(map, "map").requireTile(structure.position());
        if (tile.terrain() == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
            throw new IllegalArgumentException("World structure cannot occupy Mountain Range");
        }
        if (at(structure.position()).isPresent()) {
            throw new IllegalStateException("World structure already exists at " + structure.position());
        }
        if (structures.putIfAbsent(structure.id(), structure) != null) {
            throw new IllegalArgumentException("Duplicate world structure ID: " + structure.id());
        }
    }

    public Optional<WorldStructure> at(HexCoord position) {
        return structures.values().stream().filter(s -> s.position().equals(position)).findFirst();
    }

    public WorldStructure remove(WorldStructureId id) {
        WorldStructure removed = structures.remove(Objects.requireNonNull(id, "id"));
        if (removed == null) throw new IllegalArgumentException("Unknown world structure: " + id);
        return removed;
    }

    public Collection<WorldStructure> all() {
        return Collections.unmodifiableCollection(structures.values());
    }
}
