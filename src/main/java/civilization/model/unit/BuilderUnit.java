package civilization.model.unit;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

public final class BuilderUnit extends Unit {
    private static final long serialVersionUID = 1L;
    private int remainingCharges;

    public BuilderUnit(UnitId id, HexCoord position, ActionPoints actionPoints, int remainingCharges) {
        super(id, UnitType.BUILDER, position, actionPoints);
        requireCharges(remainingCharges);
        this.remainingCharges = remainingCharges;
    }

    public BuilderUnit(
            UnitId id,
            HexCoord position,
            ActionPoints actionPoints,
            int remainingCharges,
            FactionId ownerId) {
        super(id, UnitType.BUILDER, position, actionPoints, ownerId);
        requireCharges(remainingCharges);
        this.remainingCharges = remainingCharges;
    }

    private static void requireCharges(int remainingCharges) {
        if (remainingCharges <= 0) {
            throw new IllegalArgumentException("A Builder must start with at least one charge");
        }
    }

    public int remainingCharges() {
        return remainingCharges;
    }

    public void consumeCharge() {
        requireActive();
        if (remainingCharges == 0) {
            throw new IllegalStateException("Builder has no charges");
        }
        remainingCharges--;
        if (remainingCharges == 0) {
            remove();
        }
    }
}
