package civilization.model.technology;

import civilization.model.building.HallLevel;
import civilization.model.economy.ResourceBundle;
import java.util.Objects;
import java.util.Set;

public record TechnologyDefinition(
        TechnologyId id,
        Set<TechnologyId> prerequisites,
        HallLevel requiredHallLevel,
        ResourceBundle cost,
        int productionTurns) implements java.io.Serializable {
    public TechnologyDefinition(TechnologyId id, Set<TechnologyId> prerequisites) {
        this(id, prerequisites, HallLevel.BASE_CAMP, ResourceBundle.ZERO, 1);
    }

    public TechnologyDefinition {
        Objects.requireNonNull(id, "id");
        prerequisites = Set.copyOf(Objects.requireNonNull(prerequisites, "prerequisites"));
        if (prerequisites.contains(id)) {
            throw new IllegalArgumentException("A technology cannot require itself");
        }
        Objects.requireNonNull(requiredHallLevel, "requiredHallLevel");
        Objects.requireNonNull(cost, "cost");
        if (productionTurns <= 0) throw new IllegalArgumentException("Technology duration must be positive");
    }
}
