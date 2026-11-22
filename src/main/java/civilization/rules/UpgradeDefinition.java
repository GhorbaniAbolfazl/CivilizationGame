package civilization.rules;

import civilization.model.economy.ResourceBundle;
import civilization.model.technology.UpgradeId;

import java.util.Objects;
import java.util.Set;

public record UpgradeDefinition(
        UpgradeId id,
        ResourceBundle cost,
        int productionTurns,
        Set<UpgradeId> prerequisites) {

    public UpgradeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cost, "cost");
        prerequisites = Set.copyOf(Objects.requireNonNull(prerequisites, "prerequisites"));
        if (productionTurns <= 0) {
            throw new IllegalArgumentException("Upgrade duration must be positive");
        }
        if (prerequisites.contains(id)) {
            throw new IllegalArgumentException("An upgrade cannot require itself");
        }
    }
}
