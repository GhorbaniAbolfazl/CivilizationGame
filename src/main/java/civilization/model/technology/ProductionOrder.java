package civilization.model.technology;

import civilization.model.economy.ResourceBundle;

import java.util.Objects;

public abstract sealed class ProductionOrder implements java.io.Serializable permits UnitProductionOrder, UpgradeProductionOrder,
        HallUpgradeOrder, TechnologyResearchOrder {
    private static final long serialVersionUID = 1L;
    private final ResourceBundle paidCost;
    private final int totalTurns;
    private int remainingTurns;

    protected ProductionOrder(ResourceBundle paidCost, int totalTurns) {
        this.paidCost = Objects.requireNonNull(paidCost, "paidCost");
        if (totalTurns <= 0) {
            throw new IllegalArgumentException("Production duration must be positive");
        }
        this.totalTurns = totalTurns;
        this.remainingTurns = totalTurns;
    }

    public final ResourceBundle paidCost() {
        return paidCost;
    }

    public final int totalTurns() {
        return totalTurns;
    }

    public final int remainingTurns() {
        return remainingTurns;
    }

    public final boolean isComplete() {
        return remainingTurns == 0;
    }

    public final void advanceOneTurn() {
        if (isComplete()) {
            throw new IllegalStateException("Completed order cannot advance again");
        }
        remainingTurns--;
    }
}
