package civilization.bootstrap;

import civilization.application.GameEngine;
import civilization.application.GameFactory;
import civilization.application.event.SynchronousEventBus;
import civilization.application.factory.BuildingFactory;
import civilization.application.factory.UnitFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.generation.TribeGenerator;
import civilization.application.query.AvailableActionsQuery;
import civilization.application.query.MapSnapshotFactory;
import civilization.application.service.ActionPointService;
import civilization.application.service.AutoExploreService;
import civilization.application.service.BorderExpansionService;
import civilization.application.service.ConstructionService;
import civilization.application.service.MovementService;
import civilization.application.service.ProductionQueueService;
import civilization.application.service.ResourceProductionService;
import civilization.application.service.ResourceUpkeepService;
import civilization.application.service.StarvationService;
import civilization.application.service.TurnProcessor;
import civilization.application.service.UpgradeService;
import civilization.application.service.VisibilityService;
import civilization.application.service.WorkerAssignmentService;
import civilization.application.validation.BorderExpansionValidator;
import civilization.application.validation.ConstructionValidator;
import civilization.application.validation.MovementValidator;
import civilization.application.validation.ProductionValidator;
import civilization.application.validation.WorkerAssignmentValidator;
import civilization.model.map.HexCoord;
import civilization.model.game.GameState;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.MovementCostPolicy;

public final class GameEngineFactory {
    public GameEngine createDefaultGame() {
        return createGame(System.nanoTime());
    }

    public GameEngine createGame(long seed) {
        var rules = DefaultRulesetFactory.create();
        var visibility = new VisibilityService();
        var unitFactory = new UnitFactory();
        var buildingFactory = new BuildingFactory();
        var state = new GameFactory(visibility, unitFactory).create(
                new SeededProceduralMapGenerator().generate(8, seed, new HexCoord(0, 0)),
                new HexCoord(0, 0), seed, rules);
        new TribeGenerator().generate(state, rules);
        return createGame(state);
    }

    /** Creates controllers/services around a loaded model root without generating a map. */
    public GameEngine createGame(GameState state) {
        java.util.Objects.requireNonNull(state, "state");
        var rules = DefaultRulesetFactory.create();
        var visibility = new VisibilityService();
        var unitFactory = new UnitFactory();
        var buildingFactory = new BuildingFactory();
        var ap = new ActionPointService();
        var upgrades = new UpgradeService();
        var movementPolicy = new MovementCostPolicy();
        var movementValidator = new MovementValidator(movementPolicy);
        var constructionValidator = new ConstructionValidator();
        var workerValidator = new WorkerAssignmentValidator();
        var borderValidator = new BorderExpansionValidator();
        var productionValidator = new ProductionValidator(upgrades);
        var eventBus = new SynchronousEventBus();
        var movement = new MovementService(visibility, movementValidator, eventBus, movementPolicy);
        var workerAssignment = new WorkerAssignmentService(workerValidator);
        var construction = new ConstructionService(
                visibility, constructionValidator, buildingFactory);
        var borderExpansion = new BorderExpansionService(visibility, borderValidator);
        var queue = new ProductionQueueService(upgrades, productionValidator, unitFactory);
        return new GameEngine(
                state,
                rules,
                movement,
                workerAssignment,
                construction,
                borderExpansion,
                queue,
                new TurnProcessor(
                        ap,
                        new ResourceProductionService(),
                        queue,
                        new ResourceUpkeepService(),
                        new StarvationService(ap),
                        visibility,
                        eventBus),
                new MapSnapshotFactory(),
                new AvailableActionsQuery(
                        movementValidator,
                        constructionValidator,
                        workerValidator,
                        borderValidator,
                        productionValidator),
                new AutoExploreService(movement),
                eventBus);
    }
}
