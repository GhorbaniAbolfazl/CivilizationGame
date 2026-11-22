package civilization.model.map;

import java.util.List;

/** Immutable axial coordinate for a pointy- or flat-top hex grid. */
public record HexCoord(int q, int r) implements java.io.Serializable {
    private static final List<HexCoord> DIRECTIONS = List.of(
            new HexCoord(1, 0),
            new HexCoord(1, -1),
            new HexCoord(0, -1),
            new HexCoord(-1, 0),
            new HexCoord(-1, 1),
            new HexCoord(0, 1)
    );

    public List<HexCoord> neighbors() {
        return DIRECTIONS.stream().map(this::add).toList();
    }

    public boolean isAdjacentTo(HexCoord other) {
        return distanceTo(other) == 1;
    }

    public int distanceTo(HexCoord other) {
        int dq = q - other.q;
        int dr = r - other.r;
        int ds = (q + r) - (other.q + other.r);
        return Math.max(Math.abs(dq), Math.max(Math.abs(dr), Math.abs(ds)));
    }

    private HexCoord add(HexCoord delta) {
        return new HexCoord(q + delta.q, r + delta.r);
    }
}
