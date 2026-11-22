package civilization.rules;

import civilization.model.economy.ResourceBundle;
import civilization.model.unit.UnitType;

import java.util.Objects;

public record UnitDefinition(
        UnitType type,
        int actionPointCapacity,
        int visionRadius,
        int foodUpkeep,
        ResourceBundle productionCost,
        int productionTurns,
        int initialBuilderCharges,
        int maximumHealth,
        int attackRange) {

    public UnitDefinition(UnitType type, int actionPointCapacity, int visionRadius,
            int foodUpkeep, ResourceBundle productionCost, int productionTurns,
            int initialBuilderCharges) {
        this(type, actionPointCapacity, visionRadius, foodUpkeep, productionCost,
                productionTurns, initialBuilderCharges, 0, 0);
    }

    public UnitDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(productionCost, "productionCost");
        if (actionPointCapacity <= 0 || visionRadius < 0 || foodUpkeep < 0 || productionTurns <= 0) {
            throw new IllegalArgumentException("Invalid unit definition values for " + type);
        }
        if (type == UnitType.BUILDER && initialBuilderCharges <= 0) {
            throw new IllegalArgumentException("Builder charges must be positive");
        }
        if (type != UnitType.BUILDER && initialBuilderCharges != 0) {
            throw new IllegalArgumentException("Only Builders have charges");
        }
        if (type.isMilitary() && (maximumHealth <= 0 || attackRange <= 0)) {
            throw new IllegalArgumentException("Military units require HP and attack range");
        }
        if (!type.isMilitary() && (maximumHealth != 0 || attackRange != 0)) {
            throw new IllegalArgumentException("Civilian units cannot have military stats");
        }
    }
}
