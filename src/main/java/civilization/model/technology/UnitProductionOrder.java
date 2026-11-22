package civilization.model.technology;

import civilization.model.economy.ResourceBundle;
import civilization.model.unit.UnitType;

import java.util.Objects;

public final class UnitProductionOrder extends ProductionOrder {
    private static final long serialVersionUID = 1L;
    private final UnitType unitType;

    public UnitProductionOrder(UnitType unitType, ResourceBundle paidCost, int totalTurns) {
        super(paidCost, totalTurns);
        this.unitType = Objects.requireNonNull(unitType, "unitType");
    }

    public UnitType unitType() {
        return unitType;
    }
}
