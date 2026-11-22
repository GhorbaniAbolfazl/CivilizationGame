package civilization.phase2;

import civilization.application.factory.BuildingFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.service.ActionPointService;
import civilization.application.service.AdjacencyService;
import civilization.application.service.ConstructionService;
import civilization.application.service.DemolitionService;
import civilization.application.service.HappinessService;
import civilization.application.service.InfrastructureConstructionService;
import civilization.application.service.MovementService;
import civilization.application.service.ProductionCalculator;
import civilization.application.service.ResourceProductionService;
import civilization.application.service.StructureLifecycleService;
import civilization.application.service.VisibilityService;
import civilization.application.service.WorkerAssignmentService;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.Dock;
import civilization.model.building.HallLevel;
import civilization.model.building.Monument;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.GameOutcome;
import civilization.model.game.TurnState;
import civilization.model.happiness.HappinessState;
import civilization.model.happiness.HappinessTier;
import civilization.model.identity.FactionId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.InfrastructureType;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.HexTile;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyId;
import civilization.model.technology.TechnologyState;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.technology.UpgradeId;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic mandatory Phase 2 map, infrastructure, adjacency, and Happiness checks. */
public final class MapInfrastructureEmpireTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();
    private static final VisibilityService VISIBILITY = new VisibilityService();

    private MapInfrastructureEmpireTestSuite() { }

    public static void main(String[] args) {
        generatedMapsContainSafePhaseTwoGeography();
        roadRiverAndTerrainMovementUseOneFormula();
        infrastructureConstructionIsAtomicAndConsumesBuilderState();
        buildingPlacementAndDockProductionAreComplete();
        adjacencyRecalculatesWithoutStoredDerivedState();
        happinessThresholdsProductionAndApModifiersAreCentralized();
        demolitionRelocatesWorkersAndCleansReferences();
        depletedStructureCleanupCancelsQueueAndRemovesWall();
        System.out.println("MapInfrastructureEmpireTestSuite: all checks passed");
    }

    private static void generatedMapsContainSafePhaseTwoGeography() {
        var generator = new SeededProceduralMapGenerator();
        HexCoord center = new HexCoord(0, 0);
        for (long seed = 0; seed < 32; seed++) {
            GameMap map = generator.generate(8, seed, center);
            check(map.tiles().stream().anyMatch(tile -> tile.terrain() == TerrainType.SEA),
                    "Generated map lacks Sea for seed " + seed);
            check(map.tiles().stream().anyMatch(tile -> tile.terrain() == TerrainType.MOUNTAIN_RANGE),
                    "Generated map lacks Mountain Range for seed " + seed);
            check(map.tiles().stream().anyMatch(tile -> tile.terrain() == TerrainType.MOUNTAIN),
                    "Generated map lacks ordinary Mountain for seed " + seed);
            check(!map.riverEdges().isEmpty(), "Generated map lacks River edges for seed " + seed);
            check(map.tiles().stream().filter(tile -> tile.terrain() == TerrainType.MOUNTAIN_RANGE)
                            .allMatch(HexTile::isResourceFree),
                    "Mountain Range contains an extractable resource");
            long exits = map.neighborsOf(center).stream()
                    .filter(tile -> tile.terrain() != TerrainType.SEA
                            && tile.terrain() != TerrainType.MOUNTAIN_RANGE).count();
            check(exits >= 3, "Town Hall has fewer than three usable expansion exits");
        }
    }

    private static void roadRiverAndTerrainMovementUseOneFormula() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        HexCoord sea = new HexCoord(0, 1);
        HexCoord range = new HexCoord(-1, 0);
        GameMap map = new GameMap(List.of(grass(center), forest(forest),
                fishSea(sea, 20), new HexTile(range, TerrainType.MOUNTAIN_RANGE)));
        map.addRiver(center, forest);
        Scenario scenario = scenario(map, center, allCoordinates(map));
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        scenario.state.player().addUnit(explorer);
        MovementService movement = new MovementService(VISIBILITY);

        check(movement.movementCost(scenario.state, explorer.id(), forest, RULES) == 3,
                "River must add exactly +1 to Forest terrain cost");
        scenario.state.world().infrastructure().add(
                new Road(InfrastructureId.random(), center), map);
        check(movement.movementCost(scenario.state, explorer.id(), forest, RULES) == 3,
                "One Road endpoint must not reduce movement");
        scenario.state.world().infrastructure().add(
                new Road(InfrastructureId.random(), forest), map);
        check(movement.movementCost(scenario.state, explorer.id(), forest, RULES) == 1,
                "Two connected Roads must reduce cost to 1 and bridge the River surcharge");
        movement.move(scenario.state, explorer.id(), forest, RULES);
        check(explorer.actionPoints().current() == 7,
                "Road movement consumed the wrong amount of AP");
        expectFailure(() -> movement.move(scenario.state, explorer.id(), range, RULES),
                "Mountain Range movement was accepted");
        explorer.moveTo(center);
        expectFailure(() -> movement.move(scenario.state, explorer.id(), sea, RULES),
                "Sea movement before Navigation was accepted");
    }

    private static void infrastructureConstructionIsAtomicAndConsumesBuilderState() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord east = new HexCoord(1, 0);
        HexCoord range = new HexCoord(-1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(east),
                new HexTile(range, TerrainType.MOUNTAIN_RANGE)));
        map.addRiver(center, east);
        Scenario scenario = scenario(map, center, allCoordinates(map));
        BuilderUnit builder = new BuilderUnit(UnitId.random(), center, new ActionPoints(8), 4);
        scenario.state.player().addUnit(builder);
        InfrastructureConstructionService service = new InfrastructureConstructionService();
        int wood = scenario.hall().stockpile().amountOf(ResourceType.WOOD);
        int stone = scenario.hall().stockpile().amountOf(ResourceType.STONE);

        Road first = service.buildRoad(scenario.state, builder.id(), center, RULES);
        check(first.ownerId().equals(FactionId.PLAYER)
                        && builder.actionPoints().current() == 7
                        && builder.remainingCharges() == 3
                        && scenario.hall().stockpile().amountOf(ResourceType.WOOD) == wood - 2,
                "Road construction cost/AP/charge/ownership is incorrect");
        builder.moveTo(east);
        service.buildRoad(scenario.state, builder.id(), east, RULES);
        Wall wall = service.buildWall(scenario.state, builder.id(),
                new HexEdge(center, east), RULES);
        check(wall.health().maximum() == 100 && wall.health().current() == 100,
                "Constructed Wall HP is incorrect");
        check(scenario.state.map().hasRiver(center, east)
                        && scenario.state.world().infrastructure()
                        .on(new HexEdge(east, center)).filter(Wall.class::isInstance).isPresent(),
                "River and Wall did not coexist on the canonical edge");
        check(builder.actionPoints().current() == 4 && builder.remainingCharges() == 1,
                "Wall construction AP/charge cost is incorrect");
        check(scenario.hall().stockpile().amountOf(ResourceType.WOOD) == wood - 9
                        && scenario.hall().stockpile().amountOf(ResourceType.STONE) == stone - 10,
                "Infrastructure resource costs are incorrect");

        builder.moveTo(range);
        int apBefore = builder.actionPoints().current();
        int chargesBefore = builder.remainingCharges();
        expectFailure(() -> service.buildRoad(scenario.state, builder.id(), range, RULES),
                "Road was built on Mountain Range");
        check(builder.actionPoints().current() == apBefore
                        && builder.remainingCharges() == chargesBefore,
                "Rejected infrastructure construction mutated Builder state");

        builder.moveTo(east);
        new DemolitionService().demolishInfrastructure(
                scenario.state, builder.id(), wall.id());
        check(scenario.state.world().infrastructure().byId(wall.id()).isEmpty()
                        && builder.actionPoints().current() == apBefore - 1
                        && builder.remainingCharges() == chargesBefore,
                "Voluntary Wall demolition must remove both indexes, cost 1 AP, and use no charge");
    }

    private static void buildingPlacementAndDockProductionAreComplete() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord coast = new HexCoord(1, 0);
        HexCoord sea = new HexCoord(2, 0);
        HexCoord inland = new HexCoord(0, 1);
        HexCoord horse = new HexCoord(-1, 0);
        HexCoord monument = new HexCoord(0, -1);
        HexCoord settlement = new HexCoord(-1, -1);
        GameMap map = new GameMap(List.of(grass(center), plains(coast), fishSea(sea, 4),
                plains(inland), new HexTile(horse, TerrainType.GRASSLAND,
                new ResourceDeposit(NaturalResourceType.HORSE, 1)), plains(monument),
                grass(settlement)));
        Scenario scenario = scenario(map, center, allCoordinates(map));
        BuilderUnit builder = new BuilderUnit(UnitId.random(), coast, new ActionPoints(12), 5);
        scenario.state.player().addUnit(builder);
        ConstructionService construction = new ConstructionService(VISIBILITY);

        expectFailure(() -> construction.construct(
                scenario.state, builder.id(), BuildingType.DOCK, coast, RULES),
                "Level-one Hall constructed a Dock");
        scenario.hall().upgradeTo(HallLevel.SETTLEMENT);
        Dock dock = (Dock) construction.construct(
                scenario.state, builder.id(), BuildingType.DOCK, coast, RULES);
        check(dock.workerCapacity() == 2, "Dock Worker capacity is incorrect");
        builder.moveTo(inland);
        expectFailure(() -> construction.construct(
                scenario.state, builder.id(), BuildingType.DOCK, inland, RULES),
                "Inland Dock without Fish was accepted");
        builder.moveTo(horse);
        Building stable = construction.construct(
                scenario.state, builder.id(), BuildingType.MILITARY_STABLE, horse, RULES);
        check(stable.type() == BuildingType.MILITARY_STABLE,
                "Horse resource did not enable Military Stable placement");
        builder.moveTo(monument);
        construction.construct(scenario.state, builder.id(), BuildingType.MONUMENT, monument, RULES);
        scenario.state.player().technologies().complete(UpgradeId.SETTLEMENT_CONSTRUCTION);
        builder.moveTo(settlement);
        construction.construct(scenario.state, builder.id(), BuildingType.SETTLEMENT, settlement, RULES);
        check(scenario.state.player().happiness().accumulatedValue() == -1,
                "New Settlement did not apply its one-time -1 Happiness event");

        WorkerUnit first = new WorkerUnit(UnitId.random(), coast, new ActionPoints(4));
        WorkerUnit second = new WorkerUnit(UnitId.random(), coast, new ActionPoints(4));
        WorkerUnit overflow = new WorkerUnit(UnitId.random(), coast, new ActionPoints(4));
        scenario.state.player().addUnit(first);
        scenario.state.player().addUnit(second);
        scenario.state.player().addUnit(overflow);
        WorkerAssignmentService assignment = new WorkerAssignmentService();
        assignment.station(scenario.state, first.id(), dock.id(), RULES);
        assignment.station(scenario.state, second.id(), dock.id(), RULES);
        expectFailure(() -> assignment.station(scenario.state, overflow.id(), dock.id(), RULES),
                "Dock Worker capacity overflow was accepted");
        int foodBefore = scenario.hall().stockpile().amountOf(ResourceType.FOOD);
        new ResourceProductionService().produce(scenario.state, RULES);
        check(scenario.hall().stockpile().amountOf(ResourceType.FOOD) == foodBefore + 5,
                "Dock/Fish production did not include exactly four Fish plus Hall safeguard");
        check(map.requireTile(sea).resourceDeposit(NaturalResourceType.FISH)
                        .orElseThrow().isDepleted(),
                "Dock did not deplete its finite Fish source");
        check(!first.isStationed() && !second.isStationed(),
                "Exhausted Fish did not idle stationed Dock Workers");
    }

    private static void adjacencyRecalculatesWithoutStoredDerivedState() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord farm1 = new HexCoord(1, 0);
        HexCoord farm2 = new HexCoord(2, 0);
        HexCoord farm3 = new HexCoord(3, 0);
        HexCoord millHex = new HexCoord(0, 1);
        HexCoord sea = new HexCoord(0, 2);
        HexCoord mineHex = new HexCoord(-1, 0);
        HexCoord mountain1 = new HexCoord(-2, 0);
        HexCoord mountain2 = new HexCoord(-1, -1);
        HexCoord range = new HexCoord(-2, 1);
        GameMap map = new GameMap(List.of(grass(center), crop(farm1), crop(farm2), crop(farm3),
                forest(millHex), fishSea(sea, 100), mountain(mineHex), mountain(mountain1),
                mountain(mountain2), new HexTile(range, TerrainType.MOUNTAIN_RANGE)));
        Scenario scenario = scenario(map, center, allCoordinates(map));
        List<ProductionBuilding> farms = List.of(
                production(scenario, BuildingType.FARM, farm1, 2),
                production(scenario, BuildingType.FARM, farm2, 2),
                production(scenario, BuildingType.FARM, farm3, 2));
        ProductionBuilding mill = production(scenario, BuildingType.LUMBER_MILL, millHex, 2);
        ProductionBuilding mine = production(scenario, BuildingType.STONE_MINE, mineHex, 2);
        farms.forEach(farm -> addStationedWorkers(scenario, farm, 1));
        addStationedWorkers(scenario, mill, 1);
        addStationedWorkers(scenario, mine, 1);

        Map<BuildingId, Integer> bonuses = new AdjacencyService().productionBonuses(scenario.state);
        check(bonuses.values().stream().mapToInt(Integer::intValue).sum() == 5,
                "Adjacency total should be Farm +2, Lumber +2, Mine +1");
        check(farms.stream().mapToInt(farm -> bonuses.getOrDefault(farm.id(), 0)).sum() == 2,
                "Three Farms in a line did not count exactly two edges");
        check(bonuses.getOrDefault(mill.id(), 0) == 2,
                "Coastal Lumber Mill bonus is not exactly +2");
        check(bonuses.getOrDefault(mine.id(), 0) == 1,
                "Two ordinary Mountains did not grant Mine +1");

        var firstPlan = new ProductionCalculator().calculate(scenario.state, RULES);
        check(firstPlan.gross().amountOf(ResourceType.FOOD) == 18,
                "Farm adjacency was not added once at final-building level");
        check(firstPlan.gross().amountOf(ResourceType.WOOD) == 7,
                "Lumber adjacency output is incorrect");
        check(firstPlan.gross().amountOf(ResourceType.STONE) == 5,
                "Mine adjacency output is incorrect");

        scenario.state.player().demolishConstructedBuilding(farms.get(1).id());
        var afterRemoval = new ProductionCalculator().calculate(scenario.state, RULES);
        check(afterRemoval.gross().amountOf(ResourceType.FOOD) == 11,
                "Adjacency did not recalculate immediately after removal");
        Scenario reloaded = scenario(map, center, allCoordinates(map));
        ProductionBuilding loadedMill = production(reloaded, BuildingType.LUMBER_MILL, millHex, 2);
        addStationedWorkers(reloaded, loadedMill, 1);
        check(new AdjacencyService().productionBonuses(reloaded.state)
                        .getOrDefault(loadedMill.id(), 0) == 2,
                "Derived adjacency was not rebuilt from loaded authoritative state");
    }

    private static void happinessThresholdsProductionAndApModifiersAreCentralized() {
        check(HappinessTier.fromValue(3) == HappinessTier.GOLDEN_AGE
                        && HappinessTier.fromValue(2) == HappinessTier.NORMAL
                        && HappinessTier.fromValue(-2) == HappinessTier.NORMAL
                        && HappinessTier.fromValue(-3) == HappinessTier.DISPLEASED
                        && HappinessTier.fromValue(-4) == HappinessTier.DISPLEASED
                        && HappinessTier.fromValue(-5) == HappinessTier.REVOLT,
                "Happiness threshold boundaries are incorrect");
        HappinessState restored = new HappinessState(-3, Map.of(
                "settlement:loaded", -1,
                "military-cap:loaded", -2));
        check(restored.accumulatedValue() == -3 && restored.appliedEvents().size() == 2,
                "Loaded Happiness lost its accumulated event ledger");

        HexCoord center = new HexCoord(0, 0);
        HexCoord mineHex = new HexCoord(1, 0);
        HexCoord monument1 = new HexCoord(0, 1);
        HexCoord monument2 = new HexCoord(-1, 1);
        GameMap map = new GameMap(List.of(grass(center), mountain(mineHex),
                plains(monument1), plains(monument2)));
        Scenario scenario = scenario(map, center, allCoordinates(map));
        ProductionBuilding mine = production(scenario, BuildingType.STONE_MINE, mineHex, 2);
        addStationedWorkers(scenario, mine, 2);
        scenario.state.player().technologies().research(
                TechnologyId.STEEL_TOOLS, RULES.technologyCatalog());
        Monument first = new Monument(BuildingId.random(), monument1, FactionId.PLAYER, 100);
        Monument second = new Monument(BuildingId.random(), monument2, FactionId.PLAYER, 100);
        SwordsmanUnit guard = new SwordsmanUnit(UnitId.random(), center,
                new ActionPoints(2), FactionId.PLAYER, 1, 1);
        SwordsmanUnit secondGuard = new SwordsmanUnit(UnitId.random(), center,
                new ActionPoints(2), FactionId.PLAYER, 1, 1);
        scenario.state.player().addUnit(guard);
        scenario.state.player().addUnit(secondGuard);
        HappinessService happiness = new HappinessService();
        check(happiness.evaluate(scenario.state).hallGarrisonContribution() == 1,
                "Town Hall military garrison must contribute exactly +1 regardless of stack size");
        guard.moveTo(monument2);
        secondGuard.moveTo(monument2);
        scenario.state.player().addBuilding(first);
        scenario.state.player().addBuilding(second);
        check(happiness.evaluate(scenario.state).value() == 4
                        && happiness.evaluate(scenario.state).tier() == HappinessTier.GOLDEN_AGE,
                "Active Monuments did not contribute conditional Happiness");
        check(new ProductionCalculator().calculate(scenario.state, RULES)
                        .gross().amountOf(ResourceType.STONE) == 13,
                "Golden Age did not apply 10% after floored Steel Tools output");

        scenario.state.player().demolishConstructedBuilding(second.id());
        check(happiness.evaluate(scenario.state).value() == 2,
                "Destroying Monument did not remove its +2 contribution");
        scenario.state.player().happiness().applyOnce("expansion-test", -5);
        check(happiness.evaluate(scenario.state).tier() == HappinessTier.DISPLEASED,
                "Accumulated Happiness did not enter Displeased tier");
        check(new ProductionCalculator().calculate(scenario.state, RULES)
                        .gross().amountOf(ResourceType.STONE) == 9,
                "Displeased tier did not reduce each Worker's contribution by one");

        scenario.state.player().happiness().applyOnce("revolt-test", -2);
        WorkerUnit worker = scenario.state.player().units().stream()
                .filter(WorkerUnit.class::isInstance).map(WorkerUnit.class::cast).findFirst().orElseThrow();
        guard.actionPoints().spend(guard.actionPoints().current());
        new ActionPointService().refillLivingUnits(scenario.state);
        check(happiness.evaluate(scenario.state).tier() == HappinessTier.REVOLT
                        && worker.actionPoints().current() == 3
                        && guard.actionPoints().current() == 1,
                "Revolt did not reduce Worker and military AP capacity by one");
        scenario.state.player().happiness().applyOnce("recovery-test", 10);
        new ActionPointService().refillLivingUnits(scenario.state);
        check(worker.actionPoints().current() == 4 && guard.actionPoints().current() == 2,
                "AP capacity did not recover when Revolt ended");
    }

    private static void demolitionRelocatesWorkersAndCleansReferences() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord target = new HexCoord(1, 0);
        GameMap map = radiusOneMap(center, target);
        Scenario scenario = scenario(map, center, allCoordinates(map));
        ProductionBuilding mill = production(scenario, BuildingType.LUMBER_MILL, target, 2);
        WorkerUnit first = addWorker(scenario, mill, target);
        WorkerUnit second = addWorker(scenario, mill, target);
        BuilderUnit builder = new BuilderUnit(UnitId.random(), center, new ActionPoints(5), 3);
        scenario.state.player().addUnit(builder);
        int charges = builder.remainingCharges();

        new DemolitionService().demolishBuilding(
                scenario.state, builder.id(), mill.id());
        check(scenario.state.player().buildingAt(target).isEmpty(),
                "Demolished building remains in the registry");
        check(!first.isStationed() && !second.isStationed()
                        && !first.position().equals(second.position()),
                "Released Workers were orphaned or stacked instead of relocated");
        check(builder.actionPoints().current() == 4
                        && builder.remainingCharges() == charges,
                "Demolition must cost 1 AP and no Builder charge");
        Building replacement = new BuildingFactory().create(
                BuildingType.LUMBER_MILL, target, RULES);
        scenario.state.player().addBuilding(replacement);
        check(scenario.state.player().buildingAt(target).isPresent(),
                "Vacated building hex could not be reused in the same turn");
    }

    private static void depletedStructureCleanupCancelsQueueAndRemovesWall() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord east = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(center), grass(east)));
        Scenario scenario = scenario(map, center, allCoordinates(map));
        int food = scenario.hall().stockpile().amountOf(ResourceType.FOOD);
        scenario.hall().productionQueue().enqueue(new UnitProductionOrder(
                UnitType.WORKER, ResourceBundle.of(4, 3, 0, 0), 2));
        scenario.hall().health().damage(scenario.hall().health().current());
        StructureLifecycleService lifecycle = new StructureLifecycleService();
        lifecycle.destroyDepletedBuilding(scenario.state, scenario.hall().id());
        check(scenario.hall().productionQueue().isEmpty()
                        && scenario.hall().stockpile().amountOf(ResourceType.FOOD) == food,
                "Destroyed Hall queue was not cancelled without refund");
        check(scenario.state.outcome() == GameOutcome.DEFEAT
                        && scenario.state.turn().phase()
                        == civilization.model.game.GamePhase.GAME_OVER,
                "Zero-HP Town Hall must commit a persisted terminal defeat outcome");
        expectFailure(() -> scenario.state.turn().beginNextTurnResolution(),
                "A terminal defeat must reject further turn advancement");

        Wall wall = new Wall(InfrastructureId.random(), new HexEdge(center, east),
                FactionId.PLAYER, new civilization.model.common.Health(
                RULES.infrastructure(InfrastructureType.WALL).maximumHealth()));
        scenario.state.world().infrastructure().add(wall, map);
        wall.health().damage(wall.health().current());
        lifecycle.destroyDepletedWall(scenario.state, wall.id());
        check(scenario.state.world().infrastructure().byId(wall.id()).isEmpty(),
                "Zero-HP Wall was not removed from edge and ID registries");
    }

    private static ProductionBuilding production(
            Scenario scenario, BuildingType type, HexCoord position, int capacity) {
        ProductionBuilding building = new ProductionBuilding(
                BuildingId.random(), type, position, capacity);
        scenario.state.player().addBuilding(building);
        return building;
    }

    private static WorkerUnit addWorker(
            Scenario scenario, ProductionBuilding building, HexCoord position) {
        WorkerUnit worker = new WorkerUnit(UnitId.random(), position, new ActionPoints(4));
        worker.stationAt(building.id());
        scenario.state.player().addUnit(worker);
        return worker;
    }

    private static void addStationedWorkers(
            Scenario scenario, ProductionBuilding building, int count) {
        for (int i = 0; i < count; i++) addWorker(scenario, building, building.position());
    }

    private static Scenario scenario(GameMap map, HexCoord center, Set<HexCoord> owned) {
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(500, 500, 500, 500),
                ResourceBundle.of(2_000, 2_000, 2_000, 2_000));
        TownHall hall = new TownHall(BuildingId.random(), center, stockpile, new ProductionQueue());
        VisibilityState visibility = new VisibilityState(allCoordinates(map), allCoordinates(map));
        visibility.discoverAllByExplorer(allCoordinates(map));
        Set<HexCoord> legalOwned = owned.stream()
                .filter(coord -> map.requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        PlayerState player = new PlayerState(hall, new UnitCapacity(40),
                new EmpireBorder(legalOwned, map), visibility, new TechnologyState());
        return new Scenario(new GameState(map, player, new TurnState(),
                new CrisisState(), 123L));
    }

    private static GameMap radiusOneMap(HexCoord center, HexCoord forest) {
        List<HexTile> tiles = new ArrayList<>();
        tiles.add(grass(center));
        for (HexCoord neighbor : center.neighbors()) {
            tiles.add(neighbor.equals(forest) ? forest(neighbor) : plains(neighbor));
        }
        return new GameMap(tiles);
    }

    private static Set<HexCoord> allCoordinates(GameMap map) {
        return Set.copyOf(map.tiles().stream().map(HexTile::coordinate).toList());
    }

    private static HexTile grass(HexCoord coord) {
        return new HexTile(coord, TerrainType.GRASSLAND);
    }

    private static HexTile plains(HexCoord coord) {
        return new HexTile(coord, TerrainType.PLAINS);
    }

    private static HexTile forest(HexCoord coord) {
        return new HexTile(coord, TerrainType.FOREST,
                new ResourceDeposit(NaturalResourceType.WOOD, 500));
    }

    private static HexTile mountain(HexCoord coord) {
        return new HexTile(coord, TerrainType.MOUNTAIN,
                new ResourceDeposit(NaturalResourceType.STONE, 500));
    }

    private static HexTile crop(HexCoord coord) {
        return new HexTile(coord, TerrainType.GRASSLAND,
                new ResourceDeposit(NaturalResourceType.WHEAT, 500));
    }

    private static HexTile fishSea(HexCoord coord, int amount) {
        return new HexTile(coord, TerrainType.SEA,
                new ResourceDeposit(NaturalResourceType.FISH, amount));
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(GameState state) {
        private TownHall hall() { return state.player().townHall(); }
    }
}
