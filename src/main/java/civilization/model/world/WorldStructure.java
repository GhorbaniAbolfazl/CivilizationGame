package civilization.model.world;

import civilization.model.common.Health;
import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;
import java.util.Objects;

/** Damageable non-player structure, including tribe camps and hostile Town Halls. */
public final class WorldStructure implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final WorldStructureId id;
    private final WorldStructureType type;
    private final FactionId ownerId;
    private final HexCoord position;
    private final Health health;

    public WorldStructure(WorldStructureId id, WorldStructureType type, FactionId ownerId,
            HexCoord position, int maximumHealth) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.position = Objects.requireNonNull(position, "position");
        this.health = new Health(maximumHealth);
        if (ownerId.equals(FactionId.PLAYER)) {
            throw new IllegalArgumentException("Player structures belong in PlayerState");
        }
    }

    public WorldStructureId id() { return id; }
    public WorldStructureType type() { return type; }
    public FactionId ownerId() { return ownerId; }
    public HexCoord position() { return position; }
    public Health health() { return health; }
}
