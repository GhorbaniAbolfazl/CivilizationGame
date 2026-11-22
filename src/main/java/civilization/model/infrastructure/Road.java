package civilization.model.infrastructure;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;
import java.util.Objects;

public record Road(InfrastructureId id, HexCoord position, FactionId ownerId)
        implements TileInfrastructure, java.io.Serializable {
    public Road(InfrastructureId id, HexCoord position) {
        this(id, position, FactionId.PLAYER);
    }

    public Road {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
