package civilization.model.infrastructure;

import civilization.model.common.Health;
import civilization.model.identity.FactionId;
import civilization.model.map.HexEdge;
import java.util.Objects;

public record Wall(InfrastructureId id, HexEdge edge, FactionId ownerId, Health health)
        implements EdgeInfrastructure, java.io.Serializable {
    public Wall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(health, "health");
    }
}
