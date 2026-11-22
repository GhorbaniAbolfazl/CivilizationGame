package civilization.application.service;

import civilization.model.economy.ResourceBundle;

import java.util.Objects;

public record UpkeepResult(ResourceBundle consumed, ResourceBundle shortage) {
    public UpkeepResult {
        Objects.requireNonNull(consumed, "consumed");
        Objects.requireNonNull(shortage, "shortage");
    }
}
