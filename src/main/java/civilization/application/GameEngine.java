package civilization.application;

import civilization.application.service.BorderExpansionService;
import civilization.application.service.ConstructionService;
import civilization.application.service.MovementService;
import civilization.application.service.ProductionQueueService;
import civilization.application.service.TurnProcessor;
import civilization.application.service.TurnResolution;
import civilization.application.service.WorkerAssignmentService;
import civilization.application.service.AutoExploreService;
import civilization.application.service.TradeService;
import civilization.application.service.CombatService;
import civilization.application.service.InfrastructureConstructionService;
import civilization.application.service.DemolitionService;
import civilization.application.service.TribeDiscoveryService;
import civilization.application.service.TribeInteractionService;
import civilization.application.service.TribeMissionService;
import civilization.application.service.TribeRewardService;
import civilization.application.query.TribeQuery;
import civilization.application.query.TribeSnapshot;
import civilization.application.tribe.GiftResult;
import civilization.application.tribe.TribeActionAvailability;
import civilization.model.mission.Mission;
import civilization.model.mission.MissionId;
import civilization.application.event.CombatResolvedEvent;
import civilization.application.event.UnitMovedEvent;
import civilization.application.event.EventBus;
import civilization.application.query.MapSnapshotFactory;
import civilization.application.query.AvailableActionsQuery;
import civilization.application.query.GameSnapshot;
import civilization.application.query.GameSnapshotFactory;
import civilization.application.service.EconomyProjectionService;
import civilization.application.action.ActionPreview;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.technology.ProductionOrder;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;
import civilization.application.trade.TradePreview;
import civilization.application.trade.TradeResult;
import civilization.model.economy.ResourceType;
import civilization.model.identity.TribeId;
import civilization.model.trade.BazaarTradeLevel;
import civilization.model.trade.TradingPostId;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.CombatResult;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.infrastructure.Infrastructure;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.map.HexEdge;

import java.util.Objects;
import java.util.Set;

/** UI-independent facade over all mandatory player actions. */
public final class GameEngine {
    private GameState state;
    private final GameRules rules;
    private final MovementService movementService;
    private final WorkerAssignmentService workerAssignmentService;
    private final ConstructionService constructionService;
    private final BorderExpansionService borderExpansionService;
    private final ProductionQueueService productionQueueService;
    private final TurnProcessor turnProcessor;
    private final MapSnapshotFactory mapSnapshotFactory;
    private final GameSnapshotFactory gameSnapshotFactory;
    private final AvailableActionsQuery availableActionsQuery;
    private final AutoExploreService autoExploreService;
    private final EventBus eventBus;
    private final TradeService tradeService;
    private final CombatService combatService;
    private final InfrastructureConstructionService infrastructureConstructionService;
    private final DemolitionService demolitionService;
    private final TribeDiscoveryService tribeDiscoveryService;
    private final TribeInteractionService tribeInteractionService;
    private final TribeMissionService tribeMissionService;
    private final TribeQuery tribeQuery = new TribeQuery();

    public GameEngine(
            GameState state,
            GameRules rules,
            MovementService movementService,
            WorkerAssignmentService workerAssignmentService,
            ConstructionService constructionService,
            BorderExpansionService borderExpansionService,
            ProductionQueueService productionQueueService,
            TurnProcessor turnProcessor) {
        this(state, rules, movementService, workerAssignmentService, constructionService,
                borderExpansionService, productionQueueService, turnProcessor,
                new MapSnapshotFactory(), new AvailableActionsQuery(),
                new AutoExploreService(movementService), EventBus.noOp());
    }

    public GameEngine(
            GameState state,
            GameRules rules,
            MovementService movementService,
            WorkerAssignmentService workerAssignmentService,
            ConstructionService constructionService,
            BorderExpansionService borderExpansionService,
            ProductionQueueService productionQueueService,
            TurnProcessor turnProcessor,
            MapSnapshotFactory mapSnapshotFactory,
            AvailableActionsQuery availableActionsQuery,
            AutoExploreService autoExploreService) {
        this(state, rules, movementService, workerAssignmentService, constructionService,
                borderExpansionService, productionQueueService, turnProcessor, mapSnapshotFactory,
                availableActionsQuery, autoExploreService, EventBus.noOp());
    }

    public GameEngine(
            GameState state,
            GameRules rules,
            MovementService movementService,
            WorkerAssignmentService workerAssignmentService,
            ConstructionService constructionService,
            BorderExpansionService borderExpansionService,
            ProductionQueueService productionQueueService,
            TurnProcessor turnProcessor,
            MapSnapshotFactory mapSnapshotFactory,
            AvailableActionsQuery availableActionsQuery,
            AutoExploreService autoExploreService,
            EventBus eventBus) {
        this.state = Objects.requireNonNull(state, "state");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.movementService = Objects.requireNonNull(movementService, "movementService");
        this.workerAssignmentService = Objects.requireNonNull(
                workerAssignmentService, "workerAssignmentService");
        this.constructionService = Objects.requireNonNull(constructionService, "constructionService");
        this.borderExpansionService = Objects.requireNonNull(
                borderExpansionService, "borderExpansionService");
        this.productionQueueService = Objects.requireNonNull(
                productionQueueService, "productionQueueService");
        this.turnProcessor = Objects.requireNonNull(turnProcessor, "turnProcessor");
        this.mapSnapshotFactory = Objects.requireNonNull(mapSnapshotFactory, "mapSnapshotFactory");
        this.gameSnapshotFactory = new GameSnapshotFactory(
                this.mapSnapshotFactory, new EconomyProjectionService(), this.rules);
        this.availableActionsQuery = Objects.requireNonNull(
                availableActionsQuery, "availableActionsQuery");
        this.autoExploreService = Objects.requireNonNull(autoExploreService, "autoExploreService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.tradeService = new TradeService(this.eventBus);
        this.combatService = new CombatService();
        this.infrastructureConstructionService = new InfrastructureConstructionService();
        this.demolitionService = new DemolitionService();
        this.tribeDiscoveryService = new TribeDiscoveryService(this.eventBus);
        TribeRewardService tribeRewards = new TribeRewardService();
        this.tribeInteractionService = new TribeInteractionService(
                tribeDiscoveryService, tribeRewards, this.eventBus);
        this.tribeMissionService = new TribeMissionService(
                new civilization.rules.tribe.MissionCatalog(), tribeDiscoveryService,
                tribeRewards, this.eventBus);
    }

    public EventBus eventBus() { return eventBus; }

    /** Persistence boundary. Views never receive this mutable model root. */
    public GameState persistenceState() { return state; }

    /** Installs an already decoded, rebuilt and validated load candidate without running a turn. */
    public void restoreLoadedState(GameState candidate) {
        Objects.requireNonNull(candidate, "candidate").validateSpatialReferences();
        state = candidate;
    }

    public GameSnapshot snapshot() {
        return gameSnapshotFactory.create(state);
    }

    public ActionPreview previewMove(UnitId unitId, HexCoord destination) {
        return availableActionsQuery.canMove(state, unitId, destination, rules);
    }

    public ActionPreview previewConstruction(UnitId builderId, BuildingType type, HexCoord target) {
        return availableActionsQuery.canConstruct(state, builderId, type, target, rules);
    }

    public ActionPreview previewStation(UnitId workerId, BuildingId buildingId) {
        return availableActionsQuery.canStation(state, workerId, buildingId, rules);
    }

    public ActionPreview previewQueueUnit(UnitType type) {
        return availableActionsQuery.canQueueUnit(state, type, rules);
    }

    public ActionPreview previewQueueUpgrade(UpgradeId id) {
        return availableActionsQuery.canQueueUpgrade(state, id, rules);
    }

    public ActionPreview previewHallUpgrade(HallLevel target) {
        return availableActionsQuery.canUpgradeHall(state, target, rules);
    }

    public ActionPreview previewTechnology(TechnologyId id) {
        return availableActionsQuery.canResearchTechnology(state, id, rules);
    }

    public ActionPreview previewCancelHallProduction() {
        return availableActionsQuery.canCancelHallProduction(state);
    }

    public ActionPreview previewRoad(UnitId builderId, HexCoord target) {
        return new civilization.application.validation.InfrastructureConstructionValidator()
                .validateRoad(state, builderId, target, rules);
    }

    public ActionPreview previewWall(UnitId builderId, HexEdge edge) {
        return new civilization.application.validation.InfrastructureConstructionValidator()
                .validateWall(state, builderId, edge, rules);
    }

    public ActionPreview previewDemolishBuilding(UnitId builderId, HexCoord target) {
        return state.player().buildingAt(target)
                .map(building -> new civilization.application.validation.DemolitionValidator()
                        .building(state,builderId,building.id()))
                .orElseGet(() -> ActionPreview.reject("No player building occupies the target tile"));
    }

    public ActionPreview previewDemolishRoad(UnitId builderId, HexCoord target) {
        return state.world().infrastructure().at(target)
                .filter(Road.class::isInstance).map(Road.class::cast)
                .map(road -> new civilization.application.validation.DemolitionValidator()
                        .infrastructure(state,builderId,road.id()))
                .orElseGet(() -> ActionPreview.reject("No Road occupies the target tile"));
    }

    public ActionPreview previewDemolishWall(UnitId builderId, HexEdge edge) {
        return state.world().infrastructure().on(edge)
                .filter(Wall.class::isInstance).map(Wall.class::cast)
                .map(wall -> new civilization.application.validation.DemolitionValidator()
                        .infrastructure(state,builderId,wall.id()))
                .orElseGet(() -> ActionPreview.reject("No Wall occupies the target edge"));
    }

    public void demolishBuildingAt(UnitId builderId, HexCoord target) {
        demolishBuilding(builderId,state.player().buildingAt(target)
                .orElseThrow(() -> new ActionFailure("No player building occupies the target tile")).id());
    }

    public void demolishRoadAt(UnitId builderId, HexCoord target) {
        var road=state.world().infrastructure().at(target).filter(Road.class::isInstance)
                .map(Road.class::cast).orElseThrow(() -> new ActionFailure("No Road occupies the target tile"));
        demolishInfrastructure(builderId,road.id());
    }

    public void demolishWallAt(UnitId builderId, HexEdge edge) {
        var wall=state.world().infrastructure().on(edge).filter(Wall.class::isInstance)
                .map(Wall.class::cast).orElseThrow(() -> new ActionFailure("No Wall occupies the target edge"));
        demolishInfrastructure(builderId,wall.id());
    }

    public ActionPreview previewBorderExpansion(UnitId unitId, HexCoord selected) {
        return availableActionsQuery.canExpandBorder(state, unitId, selected, rules);
    }

    public UnitMovedEvent move(UnitId unitId, HexCoord destination) {
        return movementService.move(state, unitId, destination, rules);
    }

    public java.util.Optional<UnitMovedEvent> autoExploreStep(UnitId explorerId) {
        return autoExploreService.moveNext(state, explorerId, rules);
    }

    public void stationWorker(UnitId workerId, BuildingId buildingId) {
        workerAssignmentService.station(state, workerId, buildingId, rules);
    }

    public void unstationWorker(UnitId workerId) {
        workerAssignmentService.unstation(state, workerId);
    }

    public Building construct(UnitId builderId, BuildingType type, HexCoord target) {
        return constructionService.construct(state, builderId, type, target, rules);
    }

    public Road buildRoad(UnitId builderId, HexCoord target) {
        return infrastructureConstructionService.buildRoad(state, builderId, target, rules);
    }

    public Wall buildWall(UnitId builderId, HexEdge edge) {
        return infrastructureConstructionService.buildWall(state, builderId, edge, rules);
    }

    public Building demolishBuilding(UnitId builderId, BuildingId buildingId) {
        return demolitionService.demolishBuilding(state, builderId, buildingId);
    }

    public Infrastructure demolishInfrastructure(UnitId builderId, InfrastructureId id) {
        return demolitionService.demolishInfrastructure(state, builderId, id);
    }

    public Set<HexCoord> expandBorder(UnitId expanderId, HexCoord target) {
        return borderExpansionService.expand(state, expanderId, target, rules);
    }

    public void queueUnit(UnitType type) {
        productionQueueService.queueUnit(state, type, rules);
    }

    public void queueUpgrade(UpgradeId id) {
        productionQueueService.queueUpgrade(state, id, rules);
    }

    public void upgradeHall(HallLevel target) {
        productionQueueService.queueHallUpgrade(state, target, rules);
    }

    public void researchTechnology(TechnologyId id) {
        productionQueueService.queueTechnology(state, id, rules);
    }

    public ProductionOrder cancelHallProduction() {
        return productionQueueService.cancel(state);
    }

    public TradePreview previewBazaarTrade(BuildingId bazaarId, BazaarTradeLevel level,
            ResourceType source, ResourceType target) {
        return tradeService.previewBazaar(state, bazaarId, level, source, target);
    }

    public TradeResult tradeAtBazaar(BuildingId bazaarId, BazaarTradeLevel level,
            ResourceType source, ResourceType target) {
        return tradeService.tradeAtBazaar(state, bazaarId, level, source, target);
    }

    public TradePreview previewTradingPostTrade(TradingPostId postId,
            ResourceType source, ResourceType target, int amount) {
        return tradeService.previewTradingPost(state, postId, source, target, amount);
    }

    public TradeResult tradeAtTradingPost(TradingPostId postId,
            ResourceType source, ResourceType target, int amount) {
        return tradeService.tradeAtTradingPost(state, postId, source, target, amount);
    }

    public TradePreview previewTribeTrade(TribeId tribeId,
            ResourceType source, ResourceType target, int amount) {
        return tradeService.previewTribe(state, tribeId, source, target, amount);
    }

    public TradeResult tradeWithTribe(TribeId tribeId,
            ResourceType source, ResourceType target, int amount) {
        return tradeService.tradeWithTribe(state, tribeId, source, target, amount);
    }

    public CombatResult attack(AttackRequest request) {
        CombatResult result = combatService.playerAttack(state, request);
        eventBus.publish(new CombatResolvedEvent(result));
        return result;
    }

    public ActionPreview previewAttack(AttackRequest request) {
        return combatService.previewPlayerAttack(state,request);
    }

    public java.util.List<TribeSnapshot> discoveredTribes() {
        tribeDiscoveryService.refresh(state);
        return tribeQuery.discoveredTribes(state);
    }

    public TribeActionAvailability tribeActions(TribeId tribeId) {
        return tribeInteractionService.availability(state, tribeId);
    }

    public ActionPreview previewTribeGift(TribeId tribeId, ResourceType resource, int amount) {
        return tribeInteractionService.previewGift(state, tribeId, resource, amount);
    }

    public GiftResult giveTribeGift(TribeId tribeId, ResourceType resource, int amount) {
        return tribeInteractionService.giveGift(state, tribeId, resource, amount);
    }

    public void declareTribeWar(TribeId tribeId) {
        tribeInteractionService.declareWar(state, tribeId);
    }

    public void requestTribePeace(TribeId tribeId) {
        tribeInteractionService.requestPeace(state, tribeId);
    }

    public void requestTribeAlliance(TribeId tribeId) {
        tribeInteractionService.requestAlliance(state, tribeId, rules);
    }

    public Mission requestTribeMission(TribeId tribeId) {
        return tribeMissionService.offer(state, tribeId);
    }

    public void acceptTribeMission(MissionId missionId) {
        tribeMissionService.accept(state, missionId);
    }

    public ActionPreview previewMissionDelivery(MissionId missionId) {
        return tribeMissionService.previewDelivery(state, missionId);
    }

    public void deliverTribeMission(MissionId missionId) {
        tribeMissionService.deliver(state, missionId, rules);
    }

    public void cancelTribeMission(MissionId missionId) {
        tribeMissionService.cancel(state, missionId);
    }

    public TurnResolution endTurn() {
        return turnProcessor.endTurn(state, rules);
    }
}
