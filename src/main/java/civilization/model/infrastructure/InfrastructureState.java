package civilization.model.infrastructure;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.TerrainType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Authoritative registries for tile and edge infrastructure. */
public final class InfrastructureState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<InfrastructureId, Infrastructure> byId = new LinkedHashMap<>();
    private final Map<HexCoord, TileInfrastructure> tileInfrastructure = new LinkedHashMap<>();
    private final Map<HexEdge, EdgeInfrastructure> edgeInfrastructure = new LinkedHashMap<>();

    public void add(TileInfrastructure infrastructure, GameMap map) {
        Objects.requireNonNull(infrastructure, "infrastructure");
        Objects.requireNonNull(map, "map");
        var tile = map.requireTile(infrastructure.position());
        if (tile.terrain() == TerrainType.MOUNTAIN_RANGE || tile.terrain() == TerrainType.SEA) {
            throw new IllegalArgumentException("Tile infrastructure is forbidden on " + tile.terrain());
        }
        requireUniqueId(infrastructure);
        if (tileInfrastructure.putIfAbsent(infrastructure.position(), infrastructure) != null) {
            throw new IllegalStateException("Tile infrastructure already exists at " + infrastructure.position());
        }
        byId.put(infrastructure.id(), infrastructure);
    }

    public void add(EdgeInfrastructure infrastructure, GameMap map) {
        Objects.requireNonNull(infrastructure, "infrastructure");
        Objects.requireNonNull(map, "map");
        HexEdge edge = map.requireEdge(infrastructure.edge().first(), infrastructure.edge().second());
        var firstTerrain = map.requireTile(edge.first()).terrain();
        var secondTerrain = map.requireTile(edge.second()).terrain();
        if (firstTerrain == TerrainType.MOUNTAIN_RANGE || secondTerrain == TerrainType.MOUNTAIN_RANGE
                || firstTerrain == TerrainType.SEA || secondTerrain == TerrainType.SEA) {
            throw new IllegalArgumentException("Edge infrastructure is forbidden beside Sea or Mountain Range");
        }
        requireUniqueId(infrastructure);
        if (edgeInfrastructure.putIfAbsent(edge, infrastructure) != null) {
            throw new IllegalStateException("Edge infrastructure already exists at " + edge);
        }
        byId.put(infrastructure.id(), infrastructure);
    }

    public Optional<TileInfrastructure> at(HexCoord coordinate) {
        return Optional.ofNullable(tileInfrastructure.get(Objects.requireNonNull(coordinate, "coordinate")));
    }

    public Optional<EdgeInfrastructure> on(HexEdge edge) {
        return Optional.ofNullable(edgeInfrastructure.get(Objects.requireNonNull(edge, "edge")));
    }

    public Collection<TileInfrastructure> tileInfrastructure() {
        return Collections.unmodifiableCollection(tileInfrastructure.values());
    }

    public Collection<EdgeInfrastructure> edgeInfrastructure() {
        return Collections.unmodifiableCollection(edgeInfrastructure.values());
    }

    public Optional<Infrastructure> byId(InfrastructureId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    public Collection<Infrastructure> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Infrastructure remove(InfrastructureId id) {
        Infrastructure removed = byId.remove(Objects.requireNonNull(id, "id"));
        if (removed == null) {
            throw new IllegalArgumentException("Unknown infrastructure: " + id);
        }
        if (removed instanceof TileInfrastructure tile) {
            tileInfrastructure.remove(tile.position());
        } else if (removed instanceof EdgeInfrastructure edge) {
            edgeInfrastructure.remove(edge.edge());
        }
        return removed;
    }

    private void requireUniqueId(Infrastructure infrastructure) {
        if (byId.containsKey(infrastructure.id())) {
            throw new IllegalStateException("Duplicate infrastructure ID: " + infrastructure.id());
        }
    }
}
