package civilization.model.unit;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

public final class BorderExpanderUnit extends Unit {
    private static final long serialVersionUID = 1L;
    public BorderExpanderUnit(UnitId id, HexCoord position, ActionPoints actionPoints) {
        super(id, UnitType.BORDER_EXPANDER, position, actionPoints);
    }

    public BorderExpanderUnit(
            UnitId id, HexCoord position, ActionPoints actionPoints, FactionId ownerId) {
        super(id, UnitType.BORDER_EXPANDER, position, actionPoints, ownerId);
    }
}
