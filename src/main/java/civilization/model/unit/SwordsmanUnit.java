package civilization.model.unit;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

public final class SwordsmanUnit extends MilitaryUnit {
    private static final long serialVersionUID = 1L;
    public SwordsmanUnit(UnitId id, HexCoord position, ActionPoints ap, FactionId ownerId,
            int health, int attackRange) {
        super(id, UnitType.SWORDSMAN, position, ap, ownerId, health, attackRange);
    }
}
