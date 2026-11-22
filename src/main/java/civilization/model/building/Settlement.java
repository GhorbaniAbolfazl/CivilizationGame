package civilization.model.building;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

public final class Settlement extends Building {
    private static final long serialVersionUID = 1L;
    private final int unitCapacityContribution;

    public Settlement(BuildingId id, HexCoord position, int unitCapacityContribution) {
        super(id, BuildingType.SETTLEMENT, position);
        requireContribution(unitCapacityContribution);
        this.unitCapacityContribution = unitCapacityContribution;
    }

    public Settlement(
            BuildingId id,
            HexCoord position,
            int unitCapacityContribution,
            FactionId ownerId) {
        super(id, BuildingType.SETTLEMENT, position, ownerId);
        requireContribution(unitCapacityContribution);
        this.unitCapacityContribution = unitCapacityContribution;
    }

    public Settlement(BuildingId id, HexCoord position, int unitCapacityContribution,
            FactionId ownerId, int maximumHealth) {
        super(id, BuildingType.SETTLEMENT, position, ownerId, maximumHealth);
        requireContribution(unitCapacityContribution);
        this.unitCapacityContribution = unitCapacityContribution;
    }

    private static void requireContribution(int unitCapacityContribution) {
        if (unitCapacityContribution <= 0) {
            throw new IllegalArgumentException("Settlement Unit Cap contribution must be positive");
        }
    }

    public int unitCapacityContribution() {
        return unitCapacityContribution;
    }
}
