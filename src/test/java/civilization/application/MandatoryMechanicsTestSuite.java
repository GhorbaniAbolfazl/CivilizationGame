package civilization.application;

import civilization.application.service.ActionPointService;
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
import civilization.application.query.AvailableActionsQuery;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingStatus;
import civilization.model.building.BuildingType;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.Settlement;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.CrisisState;
import civilization.model.game.CrisisType;
import civilization.model.game.GameState;
import civilization.model.identity.FactionId;
import civilization.model.game.TurnState;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.technology.UpgradeId;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BorderExpanderUnit;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitStatus;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic application-rule tests runnable with plain Java assertions. */
public final class MandatoryMechanicsTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();
    private static final VisibilityService VISIBILITY = new VisibilityService();

    private MandatoryMechanicsTestSuite() {
    }

    public static void main(String[] args) {
        gameFactoryCreatesRequiredInitialState();
        apRegenerationAndTerrainMovementCosts();
        workerStationCapacityAndProduction();
        storageCapacityAndDepletionAreEnforced();
        productionUsesAnotherEligibleDepositBeforeIdlingWorker();
        foodConsumptionAndStarvation();
        starvationBlocksPopulationGrowth();
        constructionCostsPlacementChargesAndConsumption();
        buildingUpkeepAndDestruction();
        unitCapAndPopulationExpansion();
        fogOfWarPermanentExplorationAndExplorerVision();
        borderExpansionClaimsSevenAndConsumesUnit();
        consumedBorderExpanderStopsProvidingVisionImmediately();
        upgradePrerequisitesCostsAndEffects();
        townHallQueueAndExactTurnOrdering();
        actionPreviewsRejectInvalidTurnPhase();
        previewAndExecutionShareAuthoritativeValidation();
        System.out.println("MandatoryMechanicsTestSuite: all checks passed");
    }

    private static void gameFactoryCreatesRequiredInitialState() {
        GameMap map = radiusMap(4);
        HexCoord center = new HexCoord(0, 0);
        GameState state = new GameFactory(VISIBILITY).create(map, center, 42L, RULES);

        check(state.player().townHall().position().equals(center), "Town Hall must start at map center");
        check(state.player().activeUnitCount() == 5
                        && state.player().activeUnitCount(UnitType.BUILDER) == 2
                        && state.player().activeUnitCount(UnitType.WORKER) == 2
                        && state.player().activeUnitCount(UnitType.EXPLORER) == 1,
                "New game must have exactly two Builders, two Workers, and one Explorer");
        check(state.player().townHall().stockpile().amounts().equals(RULES.initialResources()),
                "New game did not use the configured starting resources");
        check(state.player().totalUnitCapacity() == 7, "Initial Unit Cap is incorrect");
        check(state.player().units().stream().allMatch(unit -> unit.actionPoints().current()
                        == RULES.unit(unit.type()).actionPointCapacity()),
                "Initial units must start with their type-specific full AP");
        check(state.player().visibility().discoveredHexes().size() < map.tiles().size(),
                "Most of a new map must remain under Fog of War");
    }

    private static void apRegenerationAndTerrainMovementCosts() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        HexCoord mountain = new HexCoord(-1, 0);
        GameMap map = new GameMap(List.of(
                grass(center),
                forest(forest),
                mountain(mountain, false)));
        PlayerState player = player(map, center, Set.of(center, forest, mountain), richStockpile(), 10);
        ExplorerUnit explorer = new ExplorerUnit(
                UnitId.random(), center, new ActionPoints(RULES.unit(UnitType.EXPLORER).actionPointCapacity()));
        player.addUnit(explorer);
        GameState state = state(map, player);
        VISIBILITY.refresh(state, RULES);
        MovementService movement = new MovementService(VISIBILITY);

        check(movement.movementCost(state, forest, RULES) == 2, "Forest movement cost must be 2");
        check(movement.movementCost(state, mountain, RULES) == 4, "Mountain movement cost must be 4");
        movement.move(state, explorer.id(), forest, RULES);
        check(explorer.position().equals(forest), "Movement did not change position");
        check(explorer.actionPoints().current() == 6, "Movement did not consume terrain AP");
        expectFailure(() -> movement.move(state, explorer.id(), mountain, RULES),
                "Non-adjacent movement must fail");

        explorer.actionPoints().spend(6);
        new ActionPointService().refillLivingUnits(player);
        check(explorer.actionPoints().current() == 8, "AP did not regenerate to unit capacity");
    }

    private static void workerStationCapacityAndProduction() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord farmHex = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(
                grass(center),
                new HexTile(farmHex, TerrainType.GRASSLAND,
                        new ResourceDeposit(NaturalResourceType.WHEAT, 20))));
        PlayerState player = player(map, center, Set.of(center, farmHex), emptyStockpile(), 10);
        ProductionBuilding farm = new ProductionBuilding(
                BuildingId.random(), BuildingType.FARM, farmHex, 1);
        player.addBuilding(farm);
        WorkerUnit first = new WorkerUnit(UnitId.random(), farmHex, new ActionPoints(4));
        WorkerUnit second = new WorkerUnit(UnitId.random(), farmHex, new ActionPoints(4));
        player.addUnit(first);
        player.addUnit(second);
        GameState state = state(map, player);
        player.visibility().discoverAll(Set.of(center, farmHex));
        WorkerAssignmentService assignment = new WorkerAssignmentService();

        assignment.station(state, first.id(), farm.id(), RULES);
        check(first.status() == UnitStatus.STATIONED && first.actionPoints().current() == 3,
                "Stationing must consume Worker AP and change status");
        expectFailure(() -> assignment.station(state, second.id(), farm.id(), RULES),
                "Worker capacity must be enforced");

        var production = new ResourceProductionService().produce(state, RULES);
        check(production.produced().amountOf(ResourceType.FOOD) == 6,
                "Farm Worker output plus Town Hall safeguard is incorrect");
        check(production.produced().amountOf(ResourceType.WOOD) == 1,
                "Town Hall Wood safeguard is missing");
        check(map.requireTile(farmHex).resourceDeposit(NaturalResourceType.WHEAT)
                        .orElseThrow().remainingAmount() == 15,
                "Natural deposit was not reduced by farm output");

        int apBeforeExit = first.actionPoints().current();
        assignment.unstation(state, first.id());
        check(first.actionPoints().current() == apBeforeExit,
                "Leaving a station must not refund AP");
    }

    private static void storageCapacityAndDepletionAreEnforced() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord farmHex = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(
                grass(center),
                new HexTile(farmHex, TerrainType.GRASSLAND,
                        new ResourceDeposit(NaturalResourceType.WHEAT, 2))));
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(2, 2, 0, 0), ResourceBundle.of(3, 3, 3, 3));
        PlayerState player = player(map, center, Set.of(center, farmHex), stockpile, 10);
        ProductionBuilding farm = new ProductionBuilding(
                BuildingId.random(), BuildingType.FARM, farmHex, 1);
        player.addBuilding(farm);
        WorkerUnit worker = new WorkerUnit(UnitId.random(), farmHex, new ActionPoints(4));
        player.addUnit(worker);
        worker.stationAt(farm.id());
        GameState state = state(map, player);

        var result = new ResourceProductionService().produce(state, RULES);
        check(stockpile.amountOf(ResourceType.FOOD) == 3
                        && stockpile.amountOf(ResourceType.WOOD) == 3,
                "Production must not exceed per-resource storage capacity");
        check(result.overflow().amountOf(ResourceType.FOOD) == 2,
                "Unstored production overflow was not reported");
        check(map.requireTile(farmHex).resourceDeposit(NaturalResourceType.WHEAT)
                        .orElseThrow().isDepleted(),
                "Finite natural deposit was not depleted");
        check(!worker.isStationed() && worker.status() == UnitStatus.IDLE,
                "A Worker must become idle when its deposit is depleted");
    }

    private static void productionUsesAnotherEligibleDepositBeforeIdlingWorker() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord farmHex = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(
                grass(center),
                new HexTile(farmHex, TerrainType.GRASSLAND, List.of(
                        new ResourceDeposit(NaturalResourceType.WHEAT, 1),
                        new ResourceDeposit(NaturalResourceType.RICE, 1)))));
        PlayerState player = player(map, center, Set.of(center, farmHex), emptyStockpile(), 10);
        ProductionBuilding farm = new ProductionBuilding(
                BuildingId.random(), BuildingType.FARM, farmHex, 1);
        player.addBuilding(farm);
        WorkerUnit worker = new WorkerUnit(UnitId.random(), farmHex, new ActionPoints(4));
        player.addUnit(worker);
        worker.stationAt(farm.id());
        GameState state = state(map, player);
        ResourceProductionService production = new ResourceProductionService();

        production.produce(state, RULES);
        check(worker.isStationed(),
                "Worker must remain stationed while another eligible deposit is available");
        production.produce(state, RULES);
        check(!worker.isStationed(),
                "Worker must become idle only after every eligible deposit is depleted");
    }

    private static void foodConsumptionAndStarvation() {
        HexCoord center = new HexCoord(0, 0);
        GameMap map = new GameMap(List.of(grass(center)));
        PlayerState player = player(map, center, Set.of(center), emptyStockpile(), 10);
        WorkerUnit first = new WorkerUnit(UnitId.random(), center, new ActionPoints(4, 0));
        WorkerUnit second = new WorkerUnit(UnitId.random(), center, new ActionPoints(4, 0));
        player.addUnit(first);
        player.addUnit(second);
        GameState state = state(map, player);

        var resolution = turnProcessor().endTurn(state, RULES);
        check(resolution.economyReport().produced().amountOf(ResourceType.FOOD) == 1,
                "Town Hall must produce +1 Food before upkeep");
        check(resolution.economyReport().consumed().amountOf(ResourceType.FOOD) == 1,
                "Available Food was not consumed");
        check(resolution.economyReport().shortage().amountOf(ResourceType.FOOD) == 1,
                "Unmet Food upkeep was not recorded");
        check(state.crises().isActive(CrisisType.STARVATION)
                        && !state.crises().isPopulationGrowthAllowed(),
                "Food shortage must activate Starvation and stop growth");
        check(first.actionPoints().current() == 3 && second.actionPoints().current() == 3,
                "Starvation penalty must apply after AP refresh");
    }

    private static void starvationBlocksPopulationGrowth() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord settlementHex = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(settlementHex)));
        TownHall hall = new TownHall(
                BuildingId.random(), center, richStockpile(), new ProductionQueue());
        PlayerState player = new PlayerState(
                hall,
                new UnitCapacity(10),
                new EmpireBorder(Set.of(center, settlementHex), map),
                new VisibilityState(Set.of(center, settlementHex), Set.of(center, settlementHex)),
                new TechnologyState(Set.of(UpgradeId.SETTLEMENT_CONSTRUCTION)));
        BuilderUnit builder = new BuilderUnit(
                UnitId.random(), settlementHex, new ActionPoints(5), 2);
        player.addUnit(builder);
        GameState state = state(map, player);
        ProductionQueueService queue = new ProductionQueueService(new UpgradeService());
        state.crises().activate(CrisisType.STARVATION);

        expectFailure(() -> queue.queueUnit(state, UnitType.WORKER, RULES),
                "New unit production must be blocked during Starvation");
        state.crises().resolve(CrisisType.STARVATION);
        queue.queueUnit(state, UnitType.WORKER, RULES);
        state.crises().activate(CrisisType.STARVATION);

        check(queue.advance(state, RULES).isEmpty()
                        && !hall.productionQueue().isEmpty()
                        && player.activeUnitCount() == 1,
                "A unit must not complete while Starvation blocks population growth");
        int chargesBefore = builder.remainingCharges();
        expectFailure(() -> new ConstructionService(VISIBILITY).construct(
                        state, builder.id(), BuildingType.SETTLEMENT, settlementHex, RULES),
                "Settlement construction must be blocked during Starvation");
        check(builder.remainingCharges() == chargesBefore
                        && player.buildingAt(settlementHex).isEmpty(),
                "Rejected Starvation growth must not partially construct a Settlement");
    }

    private static void constructionCostsPlacementChargesAndConsumption() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord firstHex = new HexCoord(1, 0);
        HexCoord secondHex = new HexCoord(1, -1);
        HexCoord thirdHex = new HexCoord(0, -1);
        HexCoord invalid = new HexCoord(-1, 0);
        GameMap map = new GameMap(List.of(
                grass(center), forest(firstHex), forest(secondHex), forest(thirdHex), grass(invalid)));
        Set<HexCoord> owned = Set.of(center, firstHex, secondHex, thirdHex, invalid);
        PlayerState player = player(map, center, owned, richStockpile(), 10);
        BuilderUnit builder = new BuilderUnit(UnitId.random(), firstHex, new ActionPoints(5), 3);
        player.addUnit(builder);
        GameState state = state(map, player);
        player.visibility().discoverAll(owned);
        ConstructionService construction = new ConstructionService(VISIBILITY);

        int woodBefore = player.townHall().stockpile().amountOf(ResourceType.WOOD);
        construction.construct(state, builder.id(), BuildingType.LUMBER_MILL, firstHex, RULES);
        check(player.townHall().stockpile().amountOf(ResourceType.WOOD) == woodBefore - 5,
                "Construction resource cost was not deducted");
        check(builder.actionPoints().current() == 4 && builder.remainingCharges() == 2,
                "Construction AP/charge cost is incorrect");

        builder.moveTo(invalid);
        expectFailure(() -> construction.construct(
                state, builder.id(), BuildingType.LUMBER_MILL, invalid, RULES),
                "Lumber Mill on non-forest terrain must fail");
        check(builder.remainingCharges() == 2, "Invalid construction consumed a charge");

        builder.moveTo(secondHex);
        construction.construct(state, builder.id(), BuildingType.LUMBER_MILL, secondHex, RULES);
        builder.moveTo(thirdHex);
        construction.construct(state, builder.id(), BuildingType.LUMBER_MILL, thirdHex, RULES);
        check(!builder.isActive() && builder.remainingCharges() == 0,
                "Builder must be consumed after its final charge");
        check(player.units().stream().noneMatch(unit -> unit.id().equals(builder.id())),
                "Consumed Builder must be removed from active map queries");
        expectFailure(() -> player.requireUnit(builder.id()),
                "Consumed Builder must not remain in the unit registry");
    }

    private static void buildingUpkeepAndDestruction() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), forest(forest)));
        PlayerState player = player(map, center, Set.of(center, forest), emptyStockpile(), 10);
        ProductionBuilding mill = new ProductionBuilding(
                BuildingId.random(), BuildingType.LUMBER_MILL, forest, 1);
        player.addBuilding(mill);
        WorkerUnit worker = new WorkerUnit(UnitId.random(), forest, new ActionPoints(4));
        player.addUnit(worker);
        worker.stationAt(mill.id());
        GameState state = state(map, player);
        ResourceUpkeepService upkeep = new ResourceUpkeepService();

        upkeep.apply(state, RULES);
        player.townHall().stockpile().credit(ResourceBundle.of(ResourceType.WOOD, 1));
        upkeep.apply(state, RULES);
        check(mill.consecutiveUnpaidUpkeepTurns() == 0,
                "A paid upkeep turn must reset the consecutive-failure count");
        upkeep.apply(state, RULES);
        upkeep.apply(state, RULES);
        check(mill.status() == BuildingStatus.ACTIVE, "Building broke before three failures");
        upkeep.apply(state, RULES);
        check(mill.status() == BuildingStatus.BROKEN, "Building did not break after three failures");
        check(!worker.isStationed() && worker.status() == UnitStatus.IDLE,
                "Workers must leave a broken building and remain idle");
    }

    private static void unitCapAndPopulationExpansion() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord settlementHex = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(settlementHex)));
        PlayerState player = player(map, center, Set.of(center, settlementHex), richStockpile(), 2);
        player.addUnit(new WorkerUnit(UnitId.random(), center, new ActionPoints(4)));
        player.addUnit(new WorkerUnit(UnitId.random(), center, new ActionPoints(4)));
        expectFailure(() -> player.addUnit(new WorkerUnit(UnitId.random(), center, new ActionPoints(4))),
                "Unit Cap must block additional units");
        GameState state = state(map, player);
        expectFailure(() -> new ProductionQueueService(new UpgradeService())
                        .queueUnit(state, UnitType.WORKER, RULES),
                "Town Hall production must not bypass Unit Cap");
        player.addBuilding(new Settlement(BuildingId.random(), settlementHex, 3));
        player.addUnit(new WorkerUnit(UnitId.random(), center, new ActionPoints(4)));
        check(player.totalUnitCapacity() == 5 && player.activeUnitCount() == 3,
                "Settlement did not expand population capacity");
    }

    private static void fogOfWarPermanentExplorationAndExplorerVision() {
        GameMap map = radiusMap(4);
        HexCoord center = new HexCoord(0, 0);
        PlayerState player = player(map, center, Set.of(center), richStockpile(), 10);
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        player.addUnit(explorer);
        GameState state = state(map, player);
        VISIBILITY.refresh(state, RULES);

        HexCoord radiusThree = new HexCoord(3, 0);
        HexCoord radiusFour = new HexCoord(4, 0);
        check(player.visibility().isCurrentlyVisible(radiusThree),
                "Explorer vision radius must include distance 3");
        check(!player.visibility().isCurrentlyVisible(radiusFour),
                "Explorer vision radius must not include distance 4");
        explorer.moveTo(new HexCoord(-1, 0));
        VISIBILITY.refresh(state, RULES);
        check(player.visibility().isDiscovered(radiusThree),
                "Previously seen hex must remain permanently explored");
    }

    private static void borderExpansionClaimsSevenAndConsumesUnit() {
        GameMap map = radiusMap(2);
        HexCoord center = new HexCoord(0, 0);
        HexCoord selected = new HexCoord(1, 0);
        PlayerState player = player(map, center, Set.of(center), richStockpile(), 10);
        BorderExpanderUnit expander = new BorderExpanderUnit(UnitId.random(), selected, new ActionPoints(5));
        player.addUnit(expander);
        GameState state = state(map, player);
        expectFailure(() -> new BorderExpansionService(VISIBILITY).expand(
                        state, expander.id(), selected, RULES),
                "Border expansion must reject a cluster containing unexplored hexes");
        player.visibility().discoverAll(new HashSet<>(map.tiles().stream()
                .map(HexTile::coordinate).toList()));
        expectFailure(() -> new BorderExpansionService(VISIBILITY).expand(
                        state, expander.id(), selected, RULES),
                "Discovery by non-Explorer vision must not authorize border expansion");
        player.visibility().discoverAllByExplorer(new HashSet<>(map.tiles().stream()
                .map(HexTile::coordinate).toList()));

        HexCoord contested = selected.neighbors().get(0);
        state.world().territories().claim(contested, new FactionId("test-tribe"), map);
        expectFailure(() -> new BorderExpansionService(VISIBILITY).expand(
                        state, expander.id(), selected, RULES),
                "Border expansion must not overwrite tribe/enemy territory");
        check(!player.border().owns(contested) && expander.isActive()
                        && expander.actionPoints().current() == 5,
                "Rejected territory capture mutated ownership, unit state, or AP");
        state.world().territories().relinquish(contested);

        Set<HexCoord> captured = new BorderExpansionService(VISIBILITY).expand(
                state, expander.id(), selected, RULES);
        check(captured.size() == 7, "Border Expander must capture selected hex plus six neighbors");
        check(captured.stream().allMatch(player.border()::owns), "Captured hex was not owned");
        check(!expander.isActive(), "Border Expander must be consumed after use");
        check(player.units().stream().noneMatch(unit -> unit.id().equals(expander.id())),
                "Consumed Border Expander must be removed from active map queries");
        expectFailure(() -> player.requireUnit(expander.id()),
                "Consumed Border Expander must not remain in the unit registry");
    }

    private static void consumedBorderExpanderStopsProvidingVisionImmediately() {
        GameMap map = radiusMap(4);
        HexCoord center = new HexCoord(0, 0);
        HexCoord selected = new HexCoord(2, 0);
        HexCoord visionOnly = new HexCoord(3, 0);
        PlayerState player = player(map, center, Set.of(center), richStockpile(), 10);
        BorderExpanderUnit expander = new BorderExpanderUnit(
                UnitId.random(), selected, new ActionPoints(5));
        player.addUnit(expander);
        GameState state = state(map, player);
        player.visibility().discoverAllByExplorer(new HashSet<>(map.tiles().stream()
                .map(HexTile::coordinate).toList()));
        VISIBILITY.refresh(state, RULES);
        check(player.visibility().isCurrentlyVisible(visionOnly),
                "Test setup requires the Border Expander to be the only nearby vision source");

        new BorderExpansionService(VISIBILITY).expand(state, expander.id(), selected, RULES);
        check(!player.visibility().isCurrentlyVisible(visionOnly),
                "Consumed Border Expander vision must be removed in the same committed action");
        check(player.visibility().isDiscovered(visionOnly),
                "Removing current vision must not erase permanent discovery");
    }

    private static void upgradePrerequisitesCostsAndEffects() {
        HexCoord center = new HexCoord(0, 0);
        GameMap map = new GameMap(List.of(grass(center)));
        PlayerState player = player(map, center, Set.of(center), richStockpile(), 10);
        GameState state = state(map, player);
        UpgradeService upgrades = new UpgradeService();
        ProductionQueueService queue = new ProductionQueueService(upgrades);

        check(!upgrades.canStart(state, UpgradeId.IRON_MINING, RULES),
                "Iron Mining must require Stone Mining");
        int woodBefore = player.townHall().stockpile().amountOf(ResourceType.WOOD);
        queue.queueUpgrade(state, UpgradeId.STONE_MINING, RULES);
        check(player.townHall().stockpile().amountOf(ResourceType.WOOD) == woodBefore - 8,
                "Upgrade resources were not deducted when queued");
        queue.advance(state, RULES);
        queue.advance(state, RULES);
        check(player.technologies().isCompleted(UpgradeId.STONE_MINING),
                "Stone Mining completion was not applied");
        check(upgrades.canStart(state, UpgradeId.IRON_MINING, RULES),
                "Iron Mining did not unlock after Stone Mining");

        int capacityBefore = player.townHall().stockpile().capacityOf(ResourceType.WOOD);
        queue.queueUpgrade(state, UpgradeId.STORAGE_1, RULES);
        queue.advance(state, RULES);
        queue.advance(state, RULES);
        check(player.townHall().stockpile().capacityOf(ResourceType.WOOD) == capacityBefore + 25,
                "Storage upgrade effect was not applied");

        professionalToolsMultiplyStoneAndIron();
    }

    private static void professionalToolsMultiplyStoneAndIron() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord stoneHex = new HexCoord(1, 0);
        HexCoord ironHex = new HexCoord(0, 1);
        GameMap map = new GameMap(List.of(
                grass(center), mountain(stoneHex, false), mountain(ironHex, true)));
        TownHall hall = new TownHall(
                BuildingId.random(), center, emptyStockpile(), new ProductionQueue());
        TechnologyState technologies = new TechnologyState(Set.of(
                UpgradeId.STONE_MINING, UpgradeId.IRON_MINING, UpgradeId.PROFESSIONAL_TOOLS));
        PlayerState player = new PlayerState(
                hall,
                new UnitCapacity(10),
                new EmpireBorder(Set.of(center, stoneHex, ironHex), map),
                new VisibilityState(),
                technologies);
        ProductionBuilding stoneMine = new ProductionBuilding(
                BuildingId.random(), BuildingType.STONE_MINE, stoneHex, 2);
        ProductionBuilding ironMine = new ProductionBuilding(
                BuildingId.random(), BuildingType.IRON_MINE, ironHex, 2);
        player.addBuilding(stoneMine);
        player.addBuilding(ironMine);
        WorkerUnit stoneWorker = new WorkerUnit(UnitId.random(), stoneHex, new ActionPoints(4));
        WorkerUnit ironWorker = new WorkerUnit(UnitId.random(), ironHex, new ActionPoints(4));
        player.addUnit(stoneWorker);
        player.addUnit(ironWorker);
        stoneWorker.stationAt(stoneMine.id());
        ironWorker.stationAt(ironMine.id());

        var production = new ResourceProductionService().produce(state(map, player), RULES);
        check(production.produced().amountOf(ResourceType.STONE) == 6,
                "Professional Tools must multiply Stone output by exactly 1.5");
        check(production.produced().amountOf(ResourceType.IRON) == 3,
                "Professional Tools must multiply Iron output by exactly 1.5");
    }

    private static void townHallQueueAndExactTurnOrdering() {
        HexCoord center = new HexCoord(0, 0);
        GameMap map = new GameMap(List.of(grass(center)));
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(20, 20, 20, 20), ResourceBundle.of(100, 100, 100, 100));
        PlayerState player = player(map, center, Set.of(center), stockpile, 10);
        GameState state = state(map, player);
        ProductionQueueService queue = new ProductionQueueService(new UpgradeService());
        queue.queueUnit(state, UnitType.WORKER, RULES);

        var resolution = turnProcessor(queue).endTurn(state, RULES);
        check(resolution.phaseTrace().equals(List.of(
                        "SEASON",
                        "DISASTER_CLEANUP",
                        "NATURAL_DISASTER",
                        "AP_REFRESH",
                        "RESOURCE_PRODUCTION",
                        "TOWN_HALL_QUEUE",
                        "UPKEEP",
                        "STARVATION",
                        "VISIBILITY",
                        "WILDLIFE_COMBAT",
                        "TRIBE_TURN")),
                "Turn phases executed in the wrong order: " + resolution.phaseTrace());
        check(resolution.completedUnit() != null
                        && resolution.completedUnit().type() == UnitType.WORKER,
                "Town Hall did not complete its one-turn Worker order");
        check(resolution.completedUnit().actionPoints().current() == 4,
                "A newly completed unit must enter the action phase with full AP");
        check(resolution.economyReport().consumed().amountOf(ResourceType.FOOD) == 1,
                "Newly completed unit must pay Food upkeep in the same transition");
        check(state.turn().turnNumber() == 2, "Turn number did not advance");
    }

    private static TurnProcessor turnProcessor() {
        return turnProcessor(new ProductionQueueService(new UpgradeService()));
    }

    private static void actionPreviewsRejectInvalidTurnPhase() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord neighbor = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(neighbor)));
        PlayerState player = player(map, center, Set.of(center, neighbor), richStockpile(), 10);
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        player.addUnit(explorer);
        GameState state = state(map, player);
        state.turn().beginNextTurnResolution();
        AvailableActionsQuery previews = new AvailableActionsQuery();

        check(!previews.canMove(state, explorer.id(), neighbor, RULES).allowed(),
                "Movement preview must reject actions outside the player action phase");
        check(!previews.canQueueUnit(state, UnitType.WORKER, RULES).allowed(),
                "Town Hall preview must reject actions outside the player action phase");
    }

    private static void previewAndExecutionShareAuthoritativeValidation() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord neighbor = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(neighbor)));
        PlayerState player = player(map, center, Set.of(center, neighbor), richStockpile(), 10);
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        explorer.actionPoints().spend(8);
        player.addUnit(explorer);
        GameState state = state(map, player);

        String previewReason = new AvailableActionsQuery()
                .canMove(state, explorer.id(), neighbor, RULES).reason();
        String commandReason;
        try {
            new MovementService(VISIBILITY).move(state, explorer.id(), neighbor, RULES);
            throw new AssertionError("Movement should have failed with no AP");
        } catch (ActionFailure expected) {
            commandReason = expected.getMessage();
        }
        check(previewReason.equals(commandReason),
                "Movement preview and command must report the same authoritative rejection");
        check(explorer.position().equals(center) && explorer.actionPoints().current() == 0,
                "Rejected movement must not mutate unit state");
    }

    private static TurnProcessor turnProcessor(ProductionQueueService queueService) {
        ActionPointService ap = new ActionPointService();
        return new TurnProcessor(
                ap,
                new ResourceProductionService(),
                queueService,
                new ResourceUpkeepService(),
                new StarvationService(ap),
                VISIBILITY);
    }

    private static PlayerState player(
            GameMap map,
            HexCoord center,
            Set<HexCoord> territory,
            ResourceStockpile stockpile,
            int unitCap) {
        TownHall hall = new TownHall(BuildingId.random(), center, stockpile, new ProductionQueue());
        return new PlayerState(
                hall,
                new UnitCapacity(unitCap),
                new EmpireBorder(territory, map),
                new VisibilityState(),
                new TechnologyState());
    }

    private static GameState state(GameMap map, PlayerState player) {
        return new GameState(map, player, new TurnState(), new CrisisState(), 1L);
    }

    private static ResourceStockpile richStockpile() {
        return new ResourceStockpile(
                ResourceBundle.of(100, 100, 100, 100),
                ResourceBundle.of(200, 200, 200, 200));
    }

    private static ResourceStockpile emptyStockpile() {
        return new ResourceStockpile(ResourceBundle.ZERO, ResourceBundle.of(100, 100, 100, 100));
    }

    private static HexTile grass(HexCoord coordinate) {
        return new HexTile(coordinate, TerrainType.GRASSLAND);
    }

    private static HexTile forest(HexCoord coordinate) {
        return new HexTile(coordinate, TerrainType.FOREST,
                new ResourceDeposit(NaturalResourceType.WOOD, 100));
    }

    private static HexTile mountain(HexCoord coordinate, boolean iron) {
        List<ResourceDeposit> deposits = new ArrayList<>();
        deposits.add(new ResourceDeposit(NaturalResourceType.STONE, 100));
        if (iron) {
            deposits.add(new ResourceDeposit(NaturalResourceType.IRON, 50));
        }
        return new HexTile(coordinate, TerrainType.MOUNTAIN, deposits);
    }

    private static GameMap radiusMap(int radius) {
        List<HexTile> tiles = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            int minR = Math.max(-radius, -q - radius);
            int maxR = Math.min(radius, -q + radius);
            for (int r = minR; r <= maxR; r++) {
                tiles.add(grass(new HexCoord(q, r)));
            }
        }
        return new GameMap(tiles);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (ActionFailure | IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
