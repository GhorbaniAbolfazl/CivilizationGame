package civilization.rules;

import civilization.model.building.HallLevel;
import civilization.model.economy.ResourceBundle;
import java.util.Objects;

public record HallUpgradeDefinition(
        HallLevel targetLevel,
        ResourceBundle cost,
        int productionTurns,
        ResourceBundle storageIncrease,
        int completionHeal) {
    public HallUpgradeDefinition {
        Objects.requireNonNull(targetLevel, "targetLevel");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(storageIncrease, "storageIncrease");
        if (targetLevel == HallLevel.BASE_CAMP || productionTurns <= 0 || completionHeal < 0) {
            throw new IllegalArgumentException("Invalid Hall upgrade definition");
        }
    }
}
