package civilization.model.unit;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

public final class CavalryUnit extends MilitaryUnit {
    private static final long serialVersionUID = 1L;
    public CavalryUnit(UnitId id, HexCoord position, ActionPoints ap, FactionId ownerId,
            int health, int attackRange) {
        super(id, UnitType.CAVALRY, position, ap, ownerId, health, attackRange);
    }
}
