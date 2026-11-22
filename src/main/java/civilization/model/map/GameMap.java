package civilization.model.map;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public final class GameMap implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<HexCoord, HexTile> tiles;
    private final Set<HexEdge> riverEdges = new HashSet<>();

    public GameMap(Collection<HexTile> tiles) {
        Objects.requireNonNull(tiles, "tiles");
        if (tiles.isEmpty()) {
            throw new IllegalArgumentException("A game map must contain at least one hex");
        }
        Map<HexCoord, HexTile> indexed = new LinkedHashMap<>();
        for (HexTile tile : tiles) {
            Objects.requireNonNull(tile, "tile");
            if (indexed.put(tile.coordinate(), tile) != null) {
                throw new IllegalArgumentException("Duplicate hex coordinate: " + tile.coordinate());
            }
        }
        this.tiles = Collections.unmodifiableMap(indexed);
    }

    public Optional<HexTile> tileAt(HexCoord coordinate) {
        return Optional.ofNullable(tiles.get(Objects.requireNonNull(coordinate, "coordinate")));
    }

    public HexTile requireTile(HexCoord coordinate) {
        return tileAt(coordinate)
                .orElseThrow(() -> new IllegalArgumentException("Coordinate is outside the map: " + coordinate));
    }

    public boolean contains(HexCoord coordinate) {
        return tiles.containsKey(coordinate);
    }

    public List<HexTile> neighborsOf(HexCoord coordinate) {
        requireTile(coordinate);
        return coordinate.neighbors().stream().map(tiles::get).filter(Objects::nonNull).toList();
    }

    public Collection<HexTile> tiles() {
        return tiles.values();
    }

    public int size() {
        return tiles.size();
    }

    /**
     * Deterministic q/r-ordered map coordinates in an axial radius. Runtime vision and local
     * effects use this indexed lookup instead of scanning every tile on a large map.
     */
    public List<HexCoord> coordinatesWithin(HexCoord center, int radius) {
        requireTile(Objects.requireNonNull(center, "center"));
        if (radius < 0) throw new IllegalArgumentException("Radius cannot be negative");
        java.util.ArrayList<HexCoord> result = new java.util.ArrayList<>(
                1 + 3 * radius * (radius + 1));
        for (int dq = -radius; dq <= radius; dq++) {
            int minimumDr = Math.max(-radius, -dq - radius);
            int maximumDr = Math.min(radius, -dq + radius);
            for (int dr = minimumDr; dr <= maximumDr; dr++) {
                HexCoord coordinate = new HexCoord(center.q() + dq, center.r() + dr);
                if (tiles.containsKey(coordinate)) result.add(coordinate);
            }
        }
        return List.copyOf(result);
    }

    public HexEdge requireEdge(HexCoord first, HexCoord second) {
        HexEdge edge = new HexEdge(first, second);
        if (!contains(edge.first()) || !contains(edge.second())) {
            throw new IllegalArgumentException("Edge endpoints must be on the map: " + edge);
        }
        return edge;
    }

    public void addRiver(HexCoord first, HexCoord second) {
        riverEdges.add(requireEdge(first, second));
    }

    public boolean hasRiver(HexCoord first, HexCoord second) {
        return riverEdges.contains(requireEdge(first, second));
    }

    public Set<HexEdge> riverEdges() {
        return Collections.unmodifiableSet(riverEdges);
    }
}
