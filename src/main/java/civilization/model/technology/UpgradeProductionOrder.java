package civilization.model.technology;

import civilization.model.economy.ResourceBundle;

import java.util.Objects;

public final class UpgradeProductionOrder extends ProductionOrder {
    private static final long serialVersionUID = 1L;
    private final UpgradeId upgradeId;

    public UpgradeProductionOrder(UpgradeId upgradeId, ResourceBundle paidCost, int totalTurns) {
        super(paidCost, totalTurns);
        this.upgradeId = Objects.requireNonNull(upgradeId, "upgradeId");
    }

    public UpgradeId upgradeId() {
        return upgradeId;
    }
}
