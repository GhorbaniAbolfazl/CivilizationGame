package civilization.model.technology;

import civilization.model.building.HallLevel;
import civilization.model.economy.ResourceBundle;
import java.util.Objects;

public final class HallUpgradeOrder extends ProductionOrder {
    private static final long serialVersionUID = 1L;
    private final HallLevel targetLevel;

    public HallUpgradeOrder(HallLevel targetLevel, ResourceBundle paidCost, int totalTurns) {
        super(paidCost, totalTurns);
        this.targetLevel = Objects.requireNonNull(targetLevel, "targetLevel");
        if (targetLevel == HallLevel.BASE_CAMP) {
            throw new IllegalArgumentException("Base Camp is not an upgrade target");
        }
    }

    public HallLevel targetLevel() { return targetLevel; }
}
