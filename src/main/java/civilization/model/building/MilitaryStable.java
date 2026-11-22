package civilization.model.building;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

/** Distinct Cavalry-prerequisite structure; its still-undefined Horse/HP rules remain separate. */
public final class MilitaryStable extends Building {
    private static final long serialVersionUID = 1L;
    public MilitaryStable(BuildingId id, HexCoord position) {
        super(id, BuildingType.MILITARY_STABLE, position);
    }

    public MilitaryStable(BuildingId id, HexCoord position, FactionId ownerId) {
        super(id, BuildingType.MILITARY_STABLE, position, ownerId);
    }

    public MilitaryStable(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth) {
        super(id, BuildingType.MILITARY_STABLE, position, ownerId, maximumHealth);
    }
}
