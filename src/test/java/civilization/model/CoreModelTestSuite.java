package civilization.model;

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
import civilization.model.game.GameState;
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
import civilization.model.technology.UnitProductionOrder;
import civilization.model.technology.UpgradeId;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;

import java.util.List;
import java.util.Set;

/** Dependency-free smoke tests that can run with plain java assertions. */
public final class CoreModelTestSuite {
    private CoreModelTestSuite() {
    }

    public static void main(String[] args) {
        hexCoordinatesHaveSixNeighborsAndCorrectDistance();
        terrainAndDepositsEnforceSpecificationInvariants();
        stockpileOperationsAreAtomicAndBounded();
        visibilityKeepsDiscoverySeparateFromCurrentVision();
        actionPointsAndBuilderChargesAreBounded();
        buildingBreakageAndCapacityAreRepresented();
        productionQueueAndUpgradeStateProtectTheirInvariants();
        gameAggregateValidatesMapTerritoryAndOwnership();
        System.out.println("CoreModelTestSuite: all checks passed");
    }

    private static void hexCoordinatesHaveSixNeighborsAndCorrectDistance() {
        HexCoord origin = new HexCoord(0, 0);
        check(origin.neighbors().size() == 6, "A hex must have six coordinate neighbors");
        check(origin.neighbors().stream().distinct().count() == 6, "Neighbors must be unique");
        check(origin.distanceTo(new HexCoord(2, -1)) == 2, "Axial distance is incorrect");
        check(origin.isAdjacentTo(new HexCoord(1, 0)), "Adjacent hex was not recognized");
    }

    private static void terrainAndDepositsEnforceSpecificationInvariants() {
        HexTile mountain = new HexTile(new HexCoord(0, 0), TerrainType.MOUNTAIN, List.of(
                new ResourceDeposit(NaturalResourceType.STONE, 10),
                new ResourceDeposit(NaturalResourceType.IRON, 3)));
        check(mountain.hasNaturalResource(NaturalResourceType.STONE), "Mountain must retain stone");
        check(mountain.hasNaturalResource(NaturalResourceType.IRON), "Mountain may also retain iron");
        ResourceDeposit iron = mountain.resourceDeposit(NaturalResourceType.IRON).orElseThrow();
        check(iron.extract(5) == 3 && iron.isDepleted(), "Deposit extraction must be finite");

        expectFailure(() -> new HexTile(new HexCoord(1, 0), TerrainType.FOREST),
                "Forest without wood must be rejected");
        expectFailure(() -> new HexTile(
                new HexCoord(1, 0),
                TerrainType.PLAINS,
                new ResourceDeposit(NaturalResourceType.IRON, 2)),
                "Resource on the wrong terrain must be rejected");
    }

    private static void stockpileOperationsAreAtomicAndBounded() {
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(2, 5, 1, 0),
                ResourceBundle.of(10, 10, 10, 10));
        ResourceBundle unaffordable = ResourceBundle.of(0, 6, 0, 0);
        check(!stockpile.canAfford(unaffordable), "Unaffordable cost was accepted");
        expectFailure(() -> stockpile.debit(unaffordable), "Unaffordable debit must fail");
        check(stockpile.amountOf(ResourceType.WOOD) == 5, "Failed debit partially mutated storage");

        stockpile.debit(ResourceBundle.of(1, 2, 0, 0));
        ResourceBundle overflow = stockpile.credit(ResourceBundle.of(20, 1, 0, 0));
        check(stockpile.amountOf(ResourceType.FOOD) == 10, "Credit must stop at capacity");
        check(overflow.amountOf(ResourceType.FOOD) == 11, "Overflow must be reported");
        stockpile.increaseCapacity(ResourceBundle.of(2, 0, 0, 0));
        check(stockpile.capacityOf(ResourceType.FOOD) == 12, "Capacity upgrade was not applied");
    }

    private static void actionPointsAndBuilderChargesAreBounded() {
        ActionPoints ap = new ActionPoints(5);
        ap.spend(3);
        check(ap.current() == 2 && !ap.canSpend(3), "AP spending is incorrect");
        check(ap.applyPenalty(10) == 2 && ap.current() == 0, "AP penalty must floor at zero");
        ap.refill();
        check(ap.current() == 5, "AP refill must restore capacity");

        BuilderUnit builder = new BuilderUnit(UnitId.random(), new HexCoord(0, 0), ap, 2);
        builder.consumeCharge();
        check(builder.isActive() && builder.remainingCharges() == 1, "Builder consumed too early");
        builder.consumeCharge();
        check(!builder.isActive() && builder.remainingCharges() == 0, "Builder must be removed at zero");
    }

    private static void visibilityKeepsDiscoverySeparateFromCurrentVision() {
        HexCoord first = new HexCoord(0, 0);
        HexCoord second = new HexCoord(1, 0);
        VisibilityState visibility = new VisibilityState(Set.of(first), Set.of(first));
        visibility.replaceCurrentlyVisible(Set.of(second));
        check(visibility.isDiscovered(first), "Previously discovered hex must remain discovered");
        check(!visibility.isCurrentlyVisible(first), "Old vision must be replaced");
        check(visibility.isCurrentlyVisible(second) && visibility.isDiscovered(second),
                "Visible hex must also become discovered");
        check(!visibility.isDiscoveredByExplorer(second),
                "Generic vision must not be mislabeled as Explorer discovery");
        visibility.discoverAllByExplorer(Set.of(second));
        check(visibility.isDiscoveredByExplorer(second),
                "Explorer discovery provenance must be permanent");
    }

    private static void buildingBreakageAndCapacityAreRepresented() {
        ProductionBuilding mill = new ProductionBuilding(
                BuildingId.random(), BuildingType.LUMBER_MILL, new HexCoord(1, 0), 2);
        check(!mill.recordUnpaidUpkeep(), "Building broke before three failures");
        check(!mill.recordUnpaidUpkeep(), "Building broke before three failures");
        check(mill.recordUnpaidUpkeep(), "Building did not break on the third failure");
        check(mill.status() == BuildingStatus.BROKEN, "Broken status was not stored");

        Settlement settlement = new Settlement(BuildingId.random(), new HexCoord(0, 1), 3);
        UnitCapacity capacity = new UnitCapacity(5);
        check(capacity.totalCapacity(List.of(settlement)) == 8, "Settlement must increase Unit Cap");
    }

    private static void productionQueueAndUpgradeStateProtectTheirInvariants() {
        ProductionQueue queue = new ProductionQueue();
        UnitProductionOrder order = new UnitProductionOrder(UnitType.EXPLORER, ResourceBundle.ZERO, 2);
        queue.enqueue(order);
        order.advanceOneTurn();
        check(order.remainingTurns() == 1, "Queue order did not advance");
        expectFailure(queue::removeCompletedOrder, "Incomplete queue item must not be removed");
        order.advanceOneTurn();
        check(queue.removeCompletedOrder() == order && queue.isEmpty(), "Completed item was not removed");

        TechnologyState technologies = new TechnologyState();
        expectFailure(() -> technologies.complete(UpgradeId.STORAGE_2),
                "Storage upgrade 2 must not precede upgrade 1");
        technologies.complete(UpgradeId.STORAGE_1);
        technologies.complete(UpgradeId.STORAGE_2);
        check(technologies.storageUpgradeLevel() == 2, "Storage upgrade level is incorrect");
        expectFailure(() -> technologies.complete(UpgradeId.IRON_MINING),
                "Iron mining must not precede stone mining");
        technologies.complete(UpgradeId.STONE_MINING);
        technologies.complete(UpgradeId.IRON_MINING);
        technologies.complete(UpgradeId.PROFESSIONAL_TOOLS);
    }

    private static void gameAggregateValidatesMapTerritoryAndOwnership() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(
                new HexTile(center, TerrainType.GRASSLAND),
                new HexTile(forest, TerrainType.FOREST,
                        new ResourceDeposit(NaturalResourceType.WOOD, 20))));
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(2, 2, 2, 0),
                ResourceBundle.of(20, 20, 20, 20));
        TownHall townHall = new TownHall(BuildingId.random(), center, stockpile, new ProductionQueue());
        PlayerState player = new PlayerState(
                townHall,
                new UnitCapacity(5),
                new EmpireBorder(Set.of(center, forest), map),
                new VisibilityState(Set.of(center), Set.of(center)),
                new TechnologyState());
        WorkerUnit worker = new WorkerUnit(UnitId.random(), center, new ActionPoints(3));
        player.addUnit(worker);
        ProductionBuilding mill = new ProductionBuilding(
                BuildingId.random(), BuildingType.LUMBER_MILL, forest, 1);
        player.addBuilding(mill);
        worker.stationAt(mill.id());
        check(player.stationedWorkerCount(mill.id()) == 1, "Worker assignment was not derived");

        GameState state = new GameState(map, player, new TurnState(), new CrisisState(), 42L);
        state.incrementVersion();
        check(state.stateVersion() == 1, "State version did not increment");

        PlayerState invalidPlayer = new PlayerState(
                new TownHall(
                        BuildingId.random(), center,
                        new ResourceStockpile(ResourceBundle.ZERO, ResourceBundle.of(1, 1, 1, 1)),
                        new ProductionQueue()),
                new UnitCapacity(5),
                new EmpireBorder(Set.of(center, forest), map),
                new VisibilityState(Set.of(center), Set.of(center)),
                new TechnologyState());
        invalidPlayer.addBuilding(new ProductionBuilding(
                BuildingId.random(), BuildingType.STONE_MINE, forest, 1));
        expectFailure(() -> new GameState(map, invalidPlayer, new TurnState(), new CrisisState(), 0L),
                "Production building on an invalid resource/terrain must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
