package civilization.application.service;

import civilization.model.economy.ResourceBundle;

import java.util.Objects;

public record ProductionResult(
        ResourceBundle produced,
        ResourceBundle accepted,
        ResourceBundle overflow) {
    public ProductionResult {
        Objects.requireNonNull(produced, "produced");
        Objects.requireNonNull(accepted, "accepted");
        Objects.requireNonNull(overflow, "overflow");
    }
}
