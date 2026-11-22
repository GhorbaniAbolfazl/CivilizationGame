package civilization.model.map;

import java.util.Objects;

/** Canonical, undirected edge between two adjacent hexes. */
public record HexEdge(HexCoord first, HexCoord second) implements java.io.Serializable {
    public HexEdge {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.isAdjacentTo(second)) {
            throw new IllegalArgumentException("A hex edge requires adjacent endpoints");
        }
        if (compare(first, second) > 0) {
            HexCoord swap = first;
            first = second;
            second = swap;
        }
    }

    public boolean touches(HexCoord coordinate) {
        return first.equals(coordinate) || second.equals(coordinate);
    }

    private static int compare(HexCoord left, HexCoord right) {
        int byQ = Integer.compare(left.q(), right.q());
        return byQ != 0 ? byQ : Integer.compare(left.r(), right.r());
    }
}
