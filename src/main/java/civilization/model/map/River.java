package civilization.model.map;

import java.util.Objects;

/** River identity is its canonical map edge; it is not a terrain tile. */
public record River(HexEdge edge) implements java.io.Serializable {
    public River {
        Objects.requireNonNull(edge, "edge");
    }
}
