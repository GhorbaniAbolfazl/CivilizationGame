package civilization.application.query;

import civilization.model.economy.ResourceBundle;
import civilization.model.unit.UnitType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HudSnapshot(
        int turnNumber,
        ResourceBundle stored,
        ResourceBundle capacities,
        Map<civilization.model.economy.ResourceType, Integer> netPerTurn,
        int activeUnits,
        int unitCap,
        int civilianUnits,
        int militaryUnits,
        int militaryUnitCap,
        Map<UnitType, Integer> unitsByType,
        QueueSnapshot queue,
        boolean starvation,
        boolean resourceCrisis,
        int idleUnitsWithAp,
        int happiness,
        civilization.model.happiness.HappinessTier happinessTier,
        int accumulatedHappiness,
        int monumentHappiness,
        int garrisonHappiness,
        civilization.model.time.Season season,
        int turnWithinSeason,
        boolean gameOver,
        String outcomeReason) {

    public HudSnapshot {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(capacities, "capacities");
        netPerTurn = Map.copyOf(Objects.requireNonNull(netPerTurn, "netPerTurn"));
        unitsByType = Map.copyOf(Objects.requireNonNull(unitsByType, "unitsByType"));
        Objects.requireNonNull(happinessTier, "happinessTier");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(outcomeReason, "outcomeReason");
        if (turnNumber <= 0 || activeUnits < 0 || unitCap < 0 || civilianUnits < 0
                || militaryUnits < 0 || militaryUnitCap < 0 || idleUnitsWithAp < 0) {
            throw new IllegalArgumentException("Invalid HUD values");
        }
        if (turnWithinSeason < 1 || turnWithinSeason > 10) {
            throw new IllegalArgumentException("Invalid season turn");
        }
    }

    public Optional<QueueSnapshot> currentQueue() {
        return Optional.ofNullable(queue);
    }
}
