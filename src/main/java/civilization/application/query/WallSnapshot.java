package civilization.application.query;

import civilization.model.map.HexEdge;
import java.util.Objects;

public record WallSnapshot(HexEdge edge, int currentHealth, int maximumHealth) {
    public WallSnapshot {
        Objects.requireNonNull(edge, "edge");
        if (currentHealth < 0 || maximumHealth <= 0 || currentHealth > maximumHealth) {
            throw new IllegalArgumentException("Invalid Wall health snapshot");
        }
    }
}
