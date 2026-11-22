package civilization.rules;

import civilization.model.economy.ResourceBundle;
import civilization.model.infrastructure.InfrastructureType;
import java.util.Objects;

public record InfrastructureDefinition(
        InfrastructureType type,
        ResourceBundle constructionCost,
        int constructionApCost,
        boolean consumesBuilderCharge,
        int maximumHealth) {
    public InfrastructureDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(constructionCost, "constructionCost");
        if (constructionApCost <= 0 || maximumHealth < 0) {
            throw new IllegalArgumentException("Invalid infrastructure definition");
        }
        if (type == InfrastructureType.WALL && maximumHealth == 0) {
            throw new IllegalArgumentException("Wall must have positive HP");
        }
        if (type == InfrastructureType.ROAD && maximumHealth != 0) {
            throw new IllegalArgumentException("Road does not use combat HP");
        }
    }
}
