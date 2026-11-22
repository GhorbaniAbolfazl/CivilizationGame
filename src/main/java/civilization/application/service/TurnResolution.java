package civilization.application.service;

import civilization.model.economy.TurnEconomyReport;
import civilization.model.unit.Unit;

import java.util.List;
import java.util.Objects;

public record TurnResolution(TurnEconomyReport economyReport, List<String> phaseTrace, Unit completedUnit) {
    public TurnResolution {
        Objects.requireNonNull(economyReport, "economyReport");
        phaseTrace = List.copyOf(Objects.requireNonNull(phaseTrace, "phaseTrace"));
    }
}
