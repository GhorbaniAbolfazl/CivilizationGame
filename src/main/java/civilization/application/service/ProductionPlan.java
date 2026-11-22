package civilization.application.service;

import civilization.model.economy.ResourceBundle;

import java.util.List;
import java.util.Objects;

/** Pure production calculation shared by execution and HUD projection. */
public record ProductionPlan(ResourceBundle gross, List<BuildingProduction> buildingProduction) {
    public ProductionPlan {
        Objects.requireNonNull(gross, "gross");
        buildingProduction = List.copyOf(Objects.requireNonNull(
                buildingProduction, "buildingProduction"));
    }
}
