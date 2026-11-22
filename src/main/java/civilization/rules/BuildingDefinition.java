package civilization.rules;

import civilization.model.building.BuildingType;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;

import java.util.Objects;
import java.util.Optional;

public record BuildingDefinition(
        BuildingType type,
        ResourceBundle constructionCost,
        int constructionApCost,
        int workerCapacity,
        int baseProductionPerWorker,
        ResourceType upkeepResource,
        int upkeepAmount,
        int visionRadius,
        int unitCapContribution,
        int maximumHealth) {

    public BuildingDefinition(BuildingType type, ResourceBundle constructionCost,
            int constructionApCost, int workerCapacity, int baseProductionPerWorker,
            ResourceType upkeepResource, int upkeepAmount, int visionRadius,
            int unitCapContribution) {
        this(type, constructionCost, constructionApCost, workerCapacity,
                baseProductionPerWorker, upkeepResource, upkeepAmount, visionRadius,
                unitCapContribution, 100);
    }

    public BuildingDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(constructionCost, "constructionCost");
        if (constructionApCost < 0 || workerCapacity < 0 || baseProductionPerWorker < 0
                || upkeepAmount < 0 || visionRadius < 0 || unitCapContribution < 0
                || maximumHealth <= 0) {
            throw new IllegalArgumentException("Building definition values cannot be negative");
        }
        if (upkeepAmount > 0 && upkeepResource == null) {
            throw new IllegalArgumentException("Upkeep resource is required when upkeep is positive");
        }
        if (type.isProductionBuilding() && (workerCapacity == 0 || baseProductionPerWorker == 0)) {
            throw new IllegalArgumentException("Producing buildings need Worker capacity and a base rate");
        }
        if (!type.isProductionBuilding() && (workerCapacity != 0 || baseProductionPerWorker != 0)) {
            throw new IllegalArgumentException("Non-producing buildings cannot define Worker production");
        }
    }

    public Optional<ResourceType> upkeepResourceOptional() {
        return Optional.ofNullable(upkeepResource);
    }
}
