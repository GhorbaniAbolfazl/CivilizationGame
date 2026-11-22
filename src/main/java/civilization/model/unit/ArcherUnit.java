package civilization.model.unit;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

public final class ArcherUnit extends MilitaryUnit {
    private static final long serialVersionUID = 1L;
    public ArcherUnit(UnitId id, HexCoord position, ActionPoints ap, FactionId ownerId,
            int health, int attackRange) {
        super(id, UnitType.ARCHER, position, ap, ownerId, health, attackRange);
    }
}
