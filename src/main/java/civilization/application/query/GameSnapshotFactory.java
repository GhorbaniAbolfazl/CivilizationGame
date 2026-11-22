package civilization.application.query;

import civilization.model.building.WorkerStationBuilding;
import civilization.model.game.CrisisType;
import civilization.model.game.GameState;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.technology.UpgradeProductionOrder;
import civilization.model.technology.HallUpgradeOrder;
import civilization.model.technology.TechnologyResearchOrder;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitStatus;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;

import java.util.EnumMap;
import java.util.Objects;
import civilization.application.service.EconomyProjectionService;
import civilization.rules.GameRules;

public final class GameSnapshotFactory {
    private final MapSnapshotFactory mapSnapshotFactory;
    private final EconomyProjectionService economyProjectionService;
    private final GameRules rules;
    private final civilization.application.service.HappinessService happinessService =
            new civilization.application.service.HappinessService();

    public GameSnapshotFactory(
            MapSnapshotFactory mapSnapshotFactory,
            EconomyProjectionService economyProjectionService,
            GameRules rules) {
        this.mapSnapshotFactory = Objects.requireNonNull(mapSnapshotFactory, "mapSnapshotFactory");
        this.economyProjectionService = Objects.requireNonNull(
                economyProjectionService, "economyProjectionService");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public GameSnapshot create(GameState state) {
        var playerUnits = state.player().units();
        var workerCounts = state.player().stationedWorkerCounts();
        var allBuildings = state.player().allBuildings();
        var units = playerUnits.stream().map(unit -> new UnitDetailSnapshot(
                unit.id(), unit.type(), unit.position(), unit.status(),
                unit.actionPoints().current(), unit.actionPoints().capacity(),
                unit instanceof BuilderUnit builder ? builder.remainingCharges() : 0,
                unit instanceof WorkerUnit worker ? worker.stationedAt().orElse(null) : null,
                unit.healthState().map(civilization.model.common.Health::current).orElse(0),
                unit.healthState().map(civilization.model.common.Health::maximum).orElse(0),
                unit instanceof civilization.model.unit.MilitaryUnit military
                        ? military.attackRange() : 0,
                unit.isEmbarked())).toList();
        var buildings = allBuildings.stream().map(building -> {
            int capacity = building instanceof WorkerStationBuilding production
                    ? production.workerCapacity() : 0;
            return new BuildingSnapshot(
                    building.id(), building.type(), building.position(), building.status(),
                    workerCounts.getOrDefault(building.id(), 0), capacity);
        }).toList();
        EnumMap<UnitType, Integer> byType = new EnumMap<>(UnitType.class);
        for (UnitType type : UnitType.values()) {
            byType.put(type, 0);
        }
        int idle = 0;
        int military = 0;
        for (var unit : playerUnits) {
            byType.merge(unit.type(), 1, Math::addExact);
            if (unit instanceof civilization.model.unit.MilitaryUnit) military++;
            if (unit.status() != UnitStatus.STATIONED && unit.actionPoints().current() > 0) idle++;
        }
        var happiness = happinessService.evaluate(state);
        int active = playerUnits.size();
        QueueSnapshot activeOrder = queue(state);
        HudSnapshot hud = new HudSnapshot(
                state.turn().turnNumber(),
                state.player().townHall().stockpile().amounts(),
                state.player().townHall().stockpile().capacities(),
                economyProjectionService.netPerTurn(state, rules),
                active,
                state.player().totalUnitCapacity(),
                active - military,
                military,
                state.player().militaryUnitCapacity(),
                byType,
                activeOrder,
                state.crises().isActive(CrisisType.STARVATION),
                state.crises().isActive(CrisisType.RESOURCE_SHORTAGE),
                idle,
                happiness.value(), happiness.tier(), happiness.accumulatedEvents(),
                happiness.monumentContribution(), happiness.hallGarrisonContribution(),
                state.season().season(), state.season().turnWithinSeason(),
                state.isGameOver(), state.outcomeReason());
        return new GameSnapshot(
                state.stateVersion(), mapSnapshotFactory.create(state, workerCounts), units, buildings, hud,
                state.player().technologies().completedUpgrades(),
                new TownHallSnapshot(state.player().townHall().level(),
                        state.player().townHall().health().current(),
                        state.player().townHall().health().maximum(),
                        state.player().townHall().defense(),
                        state.player().technologies().researchedTechnologies(),
                        state.player().townHall().stockpile().amounts(),
                        state.player().townHall().stockpile().capacities(), activeOrder));
    }

    private static QueueSnapshot queue(GameState state) {
        var order = state.player().townHall().productionQueue().currentOrder().orElse(null);
        if (order instanceof UnitProductionOrder unit) {
            return new QueueSnapshot(unit.unitType().name(), unit.remainingTurns());
        }
        if (order instanceof UpgradeProductionOrder upgrade) {
            return new QueueSnapshot(upgrade.upgradeId().name(), upgrade.remainingTurns());
        }
        if (order instanceof HallUpgradeOrder hall) {
            return new QueueSnapshot("HALL_" + hall.targetLevel().name(), hall.remainingTurns());
        }
        if (order instanceof TechnologyResearchOrder technology) {
            return new QueueSnapshot("TECH_" + technology.technologyId().value(),
                    technology.remainingTurns());
        }
        return null;
    }
}
