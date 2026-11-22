package civilization.model.player;

import civilization.model.building.Building;
import civilization.model.building.Settlement;

import java.util.Collection;
import java.util.Objects;

public final class UnitCapacity implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final int baseCapacity;

    public UnitCapacity(int baseCapacity) {
        if (baseCapacity < 0) {
            throw new IllegalArgumentException("Base Unit Cap cannot be negative");
        }
        this.baseCapacity = baseCapacity;
    }

    public int baseCapacity() {
        return baseCapacity;
    }

    public int totalCapacity(Collection<? extends Building> buildings) {
        Objects.requireNonNull(buildings, "buildings");
        int settlementContribution = buildings.stream()
                .filter(Building::isActive)
                .filter(Settlement.class::isInstance)
                .map(Settlement.class::cast)
                .mapToInt(Settlement::unitCapacityContribution)
                .sum();
        return Math.addExact(baseCapacity, settlementContribution);
    }

    public boolean allows(int activeUnitCount, Collection<? extends Building> buildings) {
        if (activeUnitCount < 0) {
            throw new IllegalArgumentException("Active unit count cannot be negative");
        }
        return activeUnitCount < totalCapacity(buildings);
    }
}
