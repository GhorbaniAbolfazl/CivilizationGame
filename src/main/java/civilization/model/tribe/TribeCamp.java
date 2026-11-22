package civilization.model.tribe;

import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.world.WorldStructure;
import civilization.model.world.WorldStructureId;
import civilization.model.world.WorldStructureType;
import java.util.Objects;

/** Tribe-owned camp backed by the shared damageable world-structure model. */
public final class TribeCamp implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final TribeId tribeId;
    private final WorldStructure structure;

    public TribeCamp(TribeId tribeId, HexCoord position, int maximumHealth) {
        this(tribeId, new WorldStructure(WorldStructureId.random(),
                WorldStructureType.TRIBE_CAMP,
                Objects.requireNonNull(tribeId, "tribeId").asFactionId(), position, maximumHealth));
    }

    public TribeCamp(TribeId tribeId, WorldStructure structure) {
        this.tribeId = Objects.requireNonNull(tribeId, "tribeId");
        this.structure = Objects.requireNonNull(structure, "structure");
        if (!structure.ownerId().equals(tribeId.asFactionId())
                || structure.type() != WorldStructureType.TRIBE_CAMP) {
            throw new IllegalArgumentException("Camp structure owner/type does not match tribe");
        }
    }

    public TribeId tribeId() { return tribeId; }
    public HexCoord position() { return structure.position(); }
    public civilization.model.common.Health health() { return structure.health(); }
    public FactionId ownerId() { return structure.ownerId(); }
    public WorldStructure structure() { return structure; }
}
