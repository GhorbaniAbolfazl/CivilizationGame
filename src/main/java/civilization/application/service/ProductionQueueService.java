package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.command.HallProductionCommand;
import civilization.application.command.ProduceUnitCommand;
import civilization.application.command.ResearchTechnologyCommand;
import civilization.application.command.UpgradeHallCommand;
import civilization.application.factory.UnitFactory;
import civilization.application.validation.ProductionValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.economy.ResourceStockpile;
import civilization.model.game.GameState;
import civilization.model.technology.ProductionOrder;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.UpgradeProductionOrder;
import civilization.model.technology.HallUpgradeOrder;
import civilization.model.technology.TechnologyResearchOrder;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;
import civilization.rules.combat.MilitaryStackingPolicy;

import java.util.Objects;
import java.util.Optional;

public final class ProductionQueueService {
    private final UpgradeService upgradeService;
    private final ProductionValidator validator;
    private final UnitFactory unitFactory;
    private final HallUpgradeService hallUpgradeService;
    private final TechnologyResearchService technologyResearchService;
    private final MilitaryStackingPolicy stackingPolicy = new MilitaryStackingPolicy();

    public ProductionQueueService(UpgradeService upgradeService) {
        this(upgradeService, new ProductionValidator(upgradeService), new UnitFactory(),
                new HallUpgradeService(), new TechnologyResearchService());
    }

    public ProductionQueueService(
            UpgradeService upgradeService,
            ProductionValidator validator,
            UnitFactory unitFactory) {
        this(upgradeService, validator, unitFactory,
                new HallUpgradeService(), new TechnologyResearchService());
    }

    public ProductionQueueService(
            UpgradeService upgradeService,
            ProductionValidator validator,
            UnitFactory unitFactory,
            HallUpgradeService hallUpgradeService,
            TechnologyResearchService technologyResearchService) {
        this.upgradeService = Objects.requireNonNull(upgradeService, "upgradeService");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.unitFactory = Objects.requireNonNull(unitFactory, "unitFactory");
        this.hallUpgradeService = Objects.requireNonNull(hallUpgradeService, "hallUpgradeService");
        this.technologyResearchService = Objects.requireNonNull(
                technologyResearchService, "technologyResearchService");
    }

    public void queueUnit(GameState state, UnitType type, GameRules rules) {
        submit(state, new ProduceUnitCommand(type), rules);
    }

    public void queueHallUpgrade(GameState state, HallLevel target, GameRules rules) {
        submit(state, new UpgradeHallCommand(target), rules);
    }

    public void queueTechnology(GameState state, TechnologyId id, GameRules rules) {
        submit(state, new ResearchTechnologyCommand(id), rules);
    }

    public void submit(GameState state, HallProductionCommand command, GameRules rules) {
        Objects.requireNonNull(command, "command");
        ValidationSupport.requireAllowed(command.validate(validator, state, rules));
        debit(state.player().townHall().stockpile(), command.cost(rules));
        state.player().townHall().productionQueue().enqueue(command.createOrder(rules));
        state.incrementVersion();
    }

    public void queueUpgrade(GameState state, UpgradeId id, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validateUpgrade(state, id, rules));
        var definition = rules.upgrade(id);
        debit(state.player().townHall().stockpile(), definition.cost());
        state.player().townHall().productionQueue().enqueue(
                new UpgradeProductionOrder(id, definition.cost(), definition.productionTurns()));
        state.incrementVersion();
    }

    public Optional<Unit> advance(GameState state, GameRules rules) {
        if (!state.player().townHall().isActive()) return Optional.empty();
        ProductionOrder current = state.player().townHall().productionQueue().currentOrder().orElse(null);
        if (current == null) {
            return Optional.empty();
        }
        if (!current.isComplete()) {
            current.advanceOneTurn();
        }
        if (!current.isComplete()) {
            return Optional.empty();
        }
        Unit completedUnit = null;
        if (current instanceof UnitProductionOrder unitOrder) {
            if (!state.crises().isPopulationGrowthAllowed()) {
                return Optional.empty();
            }
            if (!state.player().unitCapacity().allows(
                    Math.toIntExact(state.player().activeUnitCount()),
                    state.player().constructedBuildings())) {
                return Optional.empty();
            }
            if (unitOrder.unitType().isMilitary()
                    && state.player().activeMilitaryUnitCount()
                    >= state.player().militaryUnitCapacity()) {
                return Optional.empty();
            }
            var spawn = unitOrder.unitType().isMilitary()
                    ? militarySpawnHex(state, unitOrder.unitType()).orElse(null)
                    : state.player().townHall().position();
            if (spawn == null) return Optional.empty();
            completedUnit = unitFactory.create(
                    civilization.model.unit.UnitId.from(state.gameplayRandom()),
                    unitOrder.unitType(), spawn, rules,
                    state.player().factionId());
            state.player().addCompletedUnit(completedUnit);
        } else if (current instanceof UpgradeProductionOrder upgradeOrder) {
            upgradeService.complete(state, upgradeOrder.upgradeId(), rules);
        } else if (current instanceof HallUpgradeOrder hallOrder) {
            hallUpgradeService.complete(state, hallOrder.targetLevel(), rules);
        } else if (current instanceof TechnologyResearchOrder technologyOrder) {
            technologyResearchService.complete(state, technologyOrder.technologyId(), rules);
        }
        state.player().townHall().productionQueue().removeCompletedOrder();
        return Optional.ofNullable(completedUnit);
    }

    public ProductionOrder cancel(GameState state) {
        ValidationSupport.requireAllowed(validator.validateCancellation(state));
        ProductionOrder cancelled = state.player().townHall().productionQueue().cancelCurrent();
        state.incrementVersion();
        return cancelled;
    }

    private static void debit(ResourceStockpile stockpile, civilization.model.economy.ResourceBundle cost) {
        if (!stockpile.canAfford(cost)) {
            throw new ActionFailure("Insufficient resources for production");
        }
        stockpile.debit(cost);
    }

    private Optional<civilization.model.map.HexCoord> militarySpawnHex(
            GameState state, UnitType type) {
        var hall = state.player().townHall().position();
        java.util.List<civilization.model.map.HexCoord> candidates = new java.util.ArrayList<>();
        candidates.add(hall);
        candidates.addAll(state.map().neighborsOf(hall).stream()
                .map(civilization.model.map.HexTile::coordinate)
                .sorted(java.util.Comparator.comparingInt(civilization.model.map.HexCoord::q)
                        .thenComparingInt(civilization.model.map.HexCoord::r)).toList());
        return candidates.stream()
                .filter(state.player().border()::owns)
                .filter(coord -> state.map().requireTile(coord).terrain()
                        != civilization.model.map.TerrainType.MOUNTAIN_RANGE)
                .filter(coord -> state.map().requireTile(coord).terrain()
                        != civilization.model.map.TerrainType.SEA)
                .filter(coord -> stackingPolicy.allows(state.player().units().stream()
                        .filter(unit -> unit.position().equals(coord)).toList(), type))
                .findFirst();
    }

}
