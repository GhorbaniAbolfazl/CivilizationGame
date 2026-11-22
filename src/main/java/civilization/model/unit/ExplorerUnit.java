package civilization.model.unit;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

public final class ExplorerUnit extends Unit {
    private static final long serialVersionUID = 1L;
    public ExplorerUnit(UnitId id, HexCoord position, ActionPoints actionPoints) {
        super(id, UnitType.EXPLORER, position, actionPoints);
    }

    public ExplorerUnit(
            UnitId id, HexCoord position, ActionPoints actionPoints, FactionId ownerId) {
        super(id, UnitType.EXPLORER, position, actionPoints, ownerId);
    }
}
