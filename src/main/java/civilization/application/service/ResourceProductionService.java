package civilization.application.service;

import civilization.model.building.Dock;
import civilization.model.economy.ResourceBundle;
import civilization.model.game.GameState;
import civilization.rules.GameRules;

import java.util.Objects;

public final class ResourceProductionService {
    private final ProductionCalculator calculator;

    public ResourceProductionService() {
        this(new ProductionCalculator());
    }

    public ResourceProductionService(ProductionCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    public ProductionResult produce(GameState state, GameRules rules) {
        var workerCounts = state.player().stationedWorkerCounts();
        ProductionPlan plan = calculator.calculate(state, rules);
        for (BuildingProduction entry : plan.buildingProduction()) {
            var building = state.player().requireBuilding(entry.buildingId());
            int extracted = state.map().requireTile(entry.sourceCoordinate())
                    .resourceDeposit(entry.depositType()).orElseThrow()
                    .extract(entry.amount());
            if (extracted != entry.amount()) {
                throw new IllegalStateException("Production plan no longer matches deposit state");
            }
            if (!hasAvailableMatchingDeposit(state, building)) {
                state.player().releaseWorkersFromBuilding(building.id());
            }
        }
        state.player().constructedBuildings().stream()
                .filter(building -> building.type().isProductionBuilding())
                .filter(building -> workerCounts.getOrDefault(building.id(), 0) > 0)
                .filter(building -> !hasAvailableMatchingDeposit(state, building))
                .forEach(building -> state.player().releaseWorkersFromBuilding(building.id()));
        ResourceBundle gross = plan.gross();
        ResourceBundle overflow = state.player().townHall().stockpile().credit(gross);
        ResourceBundle accepted = gross.minusFloorZero(overflow);
        return new ProductionResult(gross, accepted, overflow);
    }

    private static boolean hasAvailableMatchingDeposit(
            GameState state, civilization.model.building.Building building) {
        if (building instanceof Dock) {
            return !new DockFishSourceResolver()
                    .availableSources(state, building.position(), true).isEmpty();
        }
        var tile = state.map().requireTile(building.position());
        return building.type().eligibleNaturalResources().stream()
                .anyMatch(tile::hasAvailableNaturalResource);
    }

}
