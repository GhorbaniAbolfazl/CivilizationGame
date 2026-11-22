package civilization.model.building;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

/** Non-producing welfare building whose conditional Happiness is derived centrally. */
public final class Monument extends Building {
    private static final long serialVersionUID = 1L;
    public Monument(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth) {
        super(id, BuildingType.MONUMENT, position, ownerId, maximumHealth);
    }
}
