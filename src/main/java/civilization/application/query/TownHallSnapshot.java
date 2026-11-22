package civilization.application.query;

import civilization.model.building.HallLevel;
import civilization.model.technology.TechnologyId;
import java.util.Objects;
import java.util.Set;
import civilization.model.economy.ResourceBundle;

/** Immutable Town Hall/read-model data used by Swing. */
public record TownHallSnapshot(HallLevel level, int health, int maximumHealth, int defense,
        Set<TechnologyId> researchedTechnologies, ResourceBundle stored,
        ResourceBundle capacities, QueueSnapshot activeOrder) {
    public TownHallSnapshot {
        Objects.requireNonNull(level, "level");
        researchedTechnologies = Set.copyOf(researchedTechnologies);
        Objects.requireNonNull(stored); Objects.requireNonNull(capacities);
    }
    public java.util.Optional<QueueSnapshot> currentOrder() {
        return java.util.Optional.ofNullable(activeOrder);
    }
}
