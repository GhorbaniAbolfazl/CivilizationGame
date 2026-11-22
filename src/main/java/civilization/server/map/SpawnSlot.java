package civilization.server.map;

import civilization.model.map.HexCoord;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record SpawnSlot(int index, HexCoord coordinate) {
    // PHASE 3 - Added in Phase 3
    public SpawnSlot {
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        Objects.requireNonNull(coordinate, "coordinate");
    }
}
