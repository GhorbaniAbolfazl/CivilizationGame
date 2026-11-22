package civilization.phase2;

import civilization.application.factory.UnitFactory;
import civilization.application.service.MovementService;
import civilization.application.service.ProductionCalculator;
import civilization.application.service.ProductionQueueService;
import civilization.application.service.UnitLifecycleService;
import civilization.application.service.VisibilityService;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.HallLevel;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.MilitaryStable;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.FactionId;
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
import civilization.model.technology.TechnologyId;
import civilization.model.technology.TechnologyState;
import civilization.model.technology.UpgradeId;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.ArcherUnit;
import civilization.model.unit.CavalryUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Deterministic acceptance tests for the mandatory Hall/technology/military slice. */
public final class HallTechnologyMilitaryTestSuite {
    private HallTechnologyMilitaryTestSuite() { }

    public static void main(String[] args) {
        prerequisitesAndQueueExclusivityAreAuthoritative();
        cancellationSpendsResourcesAndUnlocksSlot();
        hallUpgradesAdvanceExactlyAndApplyEffects();
        militaryProductionUsesDurationsAndCorrectStats();
        militaryMovementUsesSharedApRules();
        militaryCapAndHappinessAreIdempotent();
        zeroHpRemovesMilitaryUnitImmediately();
        technologyCommandsApplyNavigationSteelAndDefenseEffects();
        System.out.println("HallTechnologyMilitaryTestSuite: all checks passed");
    }

    private static void prerequisitesAndQueueExclusivityAreAuthoritative() {
        Scenario scenario = scenario(false);
        var validator = new civilization.application.validation.ProductionValidator(
                new civilization.application.service.UpgradeService());
        check(!validator.validateUnit(scenario.state, UnitType.ARCHER, scenario.rules).allowed(),
                "Archer was available before Hall level 2");
        check(!validator.validateUnit(scenario.state, UnitType.CAVALRY, scenario.rules).allowed(),
                "Cavalry was available before Hall level 2 and Stable");
        check(!validator.validateHallUpgrade(
                scenario.state, HallLevel.CAPITAL, scenario.rules).allowed(),
                "Hall level was skipped");
        check(!validator.validateTechnology(
                scenario.state, TechnologyId.NAVIGATION, scenario.rules).allowed(),
                "Navigation was available before Hall level 2");

        scenario.queue.queueUnit(scenario.state, UnitType.SWORDSMAN, scenario.rules);
        var safeguard = new ProductionCalculator().calculate(scenario.state, scenario.rules).gross();
        check(safeguard.amountOf(ResourceType.FOOD) == 1
                        && safeguard.amountOf(ResourceType.WOOD) == 1,
                "Busy Hall stopped safeguard production");
        int woodAfterFirst = scenario.hall().stockpile().amountOf(ResourceType.WOOD);
        expectFailure(() -> scenario.queue.queueHallUpgrade(
                scenario.state, HallLevel.SETTLEMENT, scenario.rules),
                "Busy Hall accepted another command");
        check(scenario.hall().productionQueue().size() == 1,
                "Queue exclusivity was not retained");
        check(scenario.hall().stockpile().amountOf(ResourceType.WOOD) == woodAfterFirst,
                "Rejected busy command consumed resources");
    }

    private static void cancellationSpendsResourcesAndUnlocksSlot() {
        Scenario scenario = scenario(false);
        int foodBefore = scenario.hall().stockpile().amountOf(ResourceType.FOOD);
        scenario.queue.queueUnit(scenario.state, UnitType.SWORDSMAN, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.FOOD) == foodBefore - 20,
                "Swordsman cost was not paid at submission");
        check(scenario.hall().stockpile().amountOf(ResourceType.WOOD) == 490,
                "Swordsman Wood cost was not paid at submission");
        scenario.queue.cancel(scenario.state);
        check(scenario.hall().productionQueue().isEmpty(), "Cancellation did not unlock Hall");
        check(scenario.hall().stockpile().amountOf(ResourceType.FOOD) == foodBefore - 20,
                "Cancellation incorrectly refunded resources");
        expectFailure(() -> scenario.queue.cancel(scenario.state),
                "Empty queue cancellation succeeded");
    }

    private static void hallUpgradesAdvanceExactlyAndApplyEffects() {
        Scenario scenario = scenario(false);
        scenario.hall().health().damage(100);
        int capacityBefore = scenario.hall().stockpile().capacityOf(ResourceType.FOOD);
        int woodBefore = scenario.hall().stockpile().amountOf(ResourceType.WOOD);
        int stoneBefore = scenario.hall().stockpile().amountOf(ResourceType.STONE);
        scenario.queue.queueHallUpgrade(scenario.state, HallLevel.SETTLEMENT, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.WOOD) == woodBefore - 50
                        && scenario.hall().stockpile().amountOf(ResourceType.STONE) == stoneBefore - 50,
                "Hall level 2 cost was not debited exactly once");
        advance(scenario, 2);
        check(scenario.hall().level() == HallLevel.BASE_CAMP,
                "Hall upgraded before its third production turn");
        advance(scenario, 1);
        check(scenario.hall().level() == HallLevel.SETTLEMENT,
                "Hall level 2 did not complete on turn three");
        check(scenario.hall().health().current() == 150, "Hall level 2 heal is incorrect");
        check(scenario.hall().stockpile().capacityOf(ResourceType.FOOD) == capacityBefore + 50,
                "Hall level 2 storage increase is incorrect");

        stoneBefore = scenario.hall().stockpile().amountOf(ResourceType.STONE);
        int ironBefore = scenario.hall().stockpile().amountOf(ResourceType.IRON);
        scenario.queue.queueHallUpgrade(scenario.state, HallLevel.CAPITAL, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.STONE) == stoneBefore - 100
                        && scenario.hall().stockpile().amountOf(ResourceType.IRON) == ironBefore - 50,
                "Hall level 3 cost was not debited exactly once");
        advance(scenario, 5);
        check(scenario.hall().level() == HallLevel.CAPITAL,
                "Hall level 3 did not complete after five turns");
        check(scenario.hall().stockpile().capacityOf(ResourceType.FOOD) == capacityBefore + 400,
                "Capital storage does not support the mandatory 500-resource Bazaar tier");
        expectFailure(() -> scenario.queue.queueHallUpgrade(
                scenario.state, HallLevel.CAPITAL, scenario.rules),
                "Maximum Hall level was repeated");
    }

    private static void militaryProductionUsesDurationsAndCorrectStats() {
        Scenario scenario = scenario(false);
        scenario.queue.queueUnit(scenario.state, UnitType.SWORDSMAN, scenario.rules);
        check(scenario.queue.advance(scenario.state, scenario.rules).isEmpty(),
                "Swordsman completed before two turns");
        Unit swordsman = scenario.queue.advance(scenario.state, scenario.rules).orElseThrow();
        check(swordsman instanceof SwordsmanUnit, "Factory created wrong Swordsman class");
        assertMilitary((MilitaryUnit) swordsman, 1, 2, 1);

        upgradeToLevelTwo(scenario);
        scenario.queue.queueUnit(scenario.state, UnitType.ARCHER, scenario.rules);
        advance(scenario, 2);
        Unit archer = scenario.queue.advance(scenario.state, scenario.rules).orElseThrow();
        check(archer instanceof ArcherUnit, "Factory created wrong Archer class");
        assertMilitary((MilitaryUnit) archer, 1, 2, 2);
        check(((MilitaryUnit) archer).canAttack(new HexCoord(2, 0)),
                "Archer range-two target was rejected");

        check(!new civilization.application.validation.ProductionValidator(
                new civilization.application.service.UpgradeService())
                .validateUnit(scenario.state, UnitType.CAVALRY, scenario.rules).allowed(),
                "Cavalry ignored missing Stable");
        MilitaryStable brokenStable = addStable(scenario);
        brokenStable.recordUnpaidUpkeep();
        brokenStable.recordUnpaidUpkeep();
        brokenStable.recordUnpaidUpkeep();
        check(!new civilization.application.validation.ProductionValidator(
                new civilization.application.service.UpgradeService())
                .validateUnit(scenario.state, UnitType.CAVALRY, scenario.rules).allowed(),
                "Broken Military Stable enabled Cavalry");
        scenario.state.player().replaceBuilding(new MilitaryStable(
                BuildingId.random(), brokenStable.position()));
        int cavalryFood = scenario.hall().stockpile().amountOf(ResourceType.FOOD);
        int cavalryWood = scenario.hall().stockpile().amountOf(ResourceType.WOOD);
        int cavalryIron = scenario.hall().stockpile().amountOf(ResourceType.IRON);
        scenario.queue.queueUnit(scenario.state, UnitType.CAVALRY, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.FOOD) == cavalryFood - 35
                        && scenario.hall().stockpile().amountOf(ResourceType.WOOD) == cavalryWood - 20
                        && scenario.hall().stockpile().amountOf(ResourceType.IRON) == cavalryIron - 10,
                "Cavalry balance cost was not debited exactly once");
        advance(scenario, 3);
        Unit cavalry = scenario.queue.advance(scenario.state, scenario.rules).orElseThrow();
        check(cavalry instanceof CavalryUnit, "Factory created wrong Cavalry class");
        assertMilitary((MilitaryUnit) cavalry, 2, 4, 1);

        Scenario invalidStable = scenario(false);
        invalidStable.state.player().addBuilding(new MilitaryStable(
                BuildingId.random(), new HexCoord(1, -1)));
        expectFailure(invalidStable.state::validateSpatialReferences,
                "Military Stable was accepted on non-Plains terrain");
    }

    private static void militaryMovementUsesSharedApRules() {
        Scenario scenario = scenario(false);
        Unit swordsman = new UnitFactory().create(
                UnitType.SWORDSMAN, scenario.hall().position(), scenario.rules);
        scenario.state.player().addUnit(swordsman);
        new MovementService(new VisibilityService()).move(
                scenario.state, swordsman.id(), new HexCoord(1, 0), scenario.rules);
        check(swordsman.position().equals(new HexCoord(1, 0)),
                "Military movement did not update the logical hex");
        check(swordsman.actionPoints().current() == 1,
                "Military movement did not consume the destination terrain AP cost");
    }

    private static void militaryCapAndHappinessAreIdempotent() {
        Scenario scenario = scenario(false);
        UnitFactory factory = new UnitFactory();
        List<UnitId> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Unit unit = addSwordsmanAtFirstOpen(scenario, factory);
            ids.add(unit.id());
        }
        check(scenario.state.player().activeMilitaryUnitCount() == 5,
                "Base Camp military cap count is incorrect");
        check(scenario.state.player().happiness().accumulatedValue() == -1,
                "First reaching military cap did not apply exactly -1 Happiness");
        expectFailure(() -> scenario.state.player().addUnit(factory.create(
                UnitType.SWORDSMAN, scenario.hall().position(), scenario.rules)),
                "Military unit exceeded Hall cap");
        scenario.state.player().removeUnit(ids.get(0));
        scenario.state.player().addUnit(factory.create(
                UnitType.SWORDSMAN, scenario.hall().position(), scenario.rules));
        check(scenario.state.player().happiness().accumulatedValue() == -1,
                "Returning to the same cap duplicated Happiness penalty");
        upgradeToLevelTwo(scenario);
        check(scenario.state.player().militaryUnitCapacity() == 10,
                "Settlement did not raise military cap to 10");
        for (int i = 0; i < 5; i++) {
            addSwordsmanAtFirstOpen(scenario, factory);
        }
        check(scenario.state.player().happiness().accumulatedValue() == -2,
                "Settlement cap did not apply one new Happiness event");
        scenario.queue.queueHallUpgrade(scenario.state, HallLevel.CAPITAL, scenario.rules);
        advance(scenario, 5);
        check(scenario.state.player().militaryUnitCapacity() == 15,
                "Capital did not raise military cap to 15");

        Scenario race = scenario(false);
        List<UnitId> raceIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Unit unit = addSwordsmanAtFirstOpen(race, factory);
            raceIds.add(unit.id());
        }
        race.queue.queueUnit(race.state, UnitType.SWORDSMAN, race.rules);
        addSwordsmanAtFirstOpen(race, factory);
        advance(race, 2);
        check(race.state.player().activeMilitaryUnitCount() == 5
                        && !race.hall().productionQueue().isEmpty(),
                "Completion race produced an over-cap duplicate or discarded order");
        race.state.player().removeUnit(raceIds.get(0));
        check(race.queue.advance(race.state, race.rules).isPresent(),
                "Blocked completed order did not finish after capacity became available");
    }

    private static void zeroHpRemovesMilitaryUnitImmediately() {
        Scenario scenario = scenario(false);
        Unit unit = new UnitFactory().create(UnitType.SWORDSMAN,
                scenario.hall().position(), scenario.rules);
        scenario.state.player().addUnit(unit);
        int damage = new UnitLifecycleService().damage(scenario.state, unit.id(), 5);
        check(damage == 1, "Damage was not bounded by remaining HP");
        check(scenario.state.player().units().stream().noneMatch(candidate -> candidate.id().equals(unit.id())),
                "Dead unit remained in active collection");
        expectFailure(() -> scenario.state.player().requireUnit(unit.id()),
                "Dead unit remained addressable");
    }

    private static void technologyCommandsApplyNavigationSteelAndDefenseEffects() {
        Scenario scenario = scenario(false);
        upgradeToLevelTwo(scenario);

        addStoneMineWithWorker(scenario);
        int baseStone = new ProductionCalculator().calculate(scenario.state, scenario.rules)
                .gross().amountOf(ResourceType.STONE);
        check(baseStone == 4, "Unexpected pre-Steel mine output");
        int steelIronBefore = scenario.hall().stockpile().amountOf(ResourceType.IRON);
        scenario.queue.queueTechnology(scenario.state, TechnologyId.STEEL_TOOLS, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.IRON) == steelIronBefore - 40,
                "Steel Tools cost was not debited exactly once");
        advance(scenario, 3);
        check(scenario.state.player().technologies().isResearched(TechnologyId.STEEL_TOOLS),
                "Steel Tools research did not persist");
        int steelStone = new ProductionCalculator().calculate(scenario.state, scenario.rules)
                .gross().amountOf(ResourceType.STONE);
        check(steelStone == 6, "Steel Tools did not apply floored 1.5x output");
        scenario.state.player().technologies().complete(UpgradeId.STONE_MINING);
        scenario.state.player().technologies().complete(UpgradeId.IRON_MINING);
        scenario.state.player().technologies().complete(UpgradeId.PROFESSIONAL_TOOLS);
        check(new ProductionCalculator().calculate(scenario.state, scenario.rules)
                        .gross().amountOf(ResourceType.STONE) == 6,
                "Steel Tools and Professional Tools stacked unexpectedly");

        scenario.queue.queueHallUpgrade(scenario.state, HallLevel.CAPITAL, scenario.rules);
        advance(scenario, 5);
        scenario.hall().health().damage(25);
        int healthBeforeDefense = scenario.hall().health().current();
        int defenseStoneBefore = scenario.hall().stockpile().amountOf(ResourceType.STONE);
        scenario.queue.queueTechnology(
                scenario.state, TechnologyId.DEFENSIVE_ARCHITECTURE, scenario.rules);
        check(scenario.hall().stockpile().amountOf(ResourceType.STONE) == defenseStoneBefore - 100,
                "Defensive Architecture cost was not debited exactly once");
        advance(scenario, 4);
        check(scenario.hall().defense() == 30 && scenario.hall().health().maximum() == 350,
                "Defensive Architecture Hall stats are incorrect");
        check(scenario.hall().health().current() == healthBeforeDefense,
                "Defensive Architecture granted an undocumented heal");
        check(scenario.state.world().infrastructure().edgeInfrastructure().size() == 6,
                "Defensive Architecture did not wall every valid Hall edge");

        Scenario seaScenario = scenario(true);
        upgradeToLevelTwo(seaScenario);
        int navigationWoodBefore = seaScenario.hall().stockpile().amountOf(ResourceType.WOOD);
        seaScenario.queue.queueTechnology(
                seaScenario.state, TechnologyId.NAVIGATION, seaScenario.rules);
        check(seaScenario.hall().stockpile().amountOf(ResourceType.WOOD)
                        == navigationWoodBefore - 80,
                "Navigation cost was not debited exactly once");
        advance(seaScenario, 4);
        Unit sailor = new UnitFactory().create(UnitType.SWORDSMAN,
                seaScenario.hall().position(), seaScenario.rules);
        seaScenario.state.player().addUnit(sailor);
        new MovementService(new VisibilityService()).move(
                seaScenario.state, sailor.id(), new HexCoord(1, 0), seaScenario.rules);
        check(sailor.position().equals(new HexCoord(1, 0)) && sailor.isEmbarked(),
                "Navigation did not permit and mark Sea entry");
        check(sailor.actionPoints().current() == 0,
                "Entering Sea did not consume all remaining AP");
    }

    private static void assertMilitary(
            MilitaryUnit unit, int hp, int ap, int attackRange) {
        check(unit.health().maximum() == hp && unit.health().current() == hp,
                "Military HP is incorrect for " + unit.type());
        check(unit.actionPoints().capacity() == ap, "Military AP is incorrect for " + unit.type());
        check(unit.attackRange() == attackRange, "Attack range is incorrect for " + unit.type());
    }

    private static Unit addSwordsmanAtFirstOpen(Scenario scenario, UnitFactory factory) {
        HexCoord position = scenario.state.map().tiles().stream().map(HexTile::coordinate)
                .sorted(java.util.Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .filter(coord -> scenario.state.player().units().stream()
                        .filter(unit -> unit.type() == UnitType.SWORDSMAN)
                        .filter(unit -> unit.position().equals(coord)).count() < 2)
                .findFirst().orElseThrow();
        Unit unit = factory.create(UnitType.SWORDSMAN, position,
                scenario.rules, FactionId.PLAYER);
        scenario.state.player().addUnit(unit);
        return unit;
    }

    private static void upgradeToLevelTwo(Scenario scenario) {
        scenario.queue.queueHallUpgrade(scenario.state, HallLevel.SETTLEMENT, scenario.rules);
        advance(scenario, 3);
    }

    private static MilitaryStable addStable(Scenario scenario) {
        MilitaryStable stable = new MilitaryStable(BuildingId.random(), new HexCoord(1, 0));
        scenario.state.player().addBuilding(stable);
        scenario.state.validateSpatialReferences();
        return stable;
    }

    private static void addStoneMineWithWorker(Scenario scenario) {
        BuildingId mineId = BuildingId.random();
        scenario.state.player().addBuilding(new ProductionBuilding(
                mineId, BuildingType.STONE_MINE, new HexCoord(1, -1), 2));
        WorkerUnit worker = new WorkerUnit(UnitId.random(), new HexCoord(1, -1),
                new ActionPoints(4));
        worker.stationAt(mineId);
        scenario.state.player().addUnit(worker);
        scenario.state.validateSpatialReferences();
    }

    private static void advance(Scenario scenario, int turns) {
        for (int i = 0; i < turns; i++) scenario.queue.advance(scenario.state, scenario.rules);
    }

    private static Scenario scenario(boolean seaEast) {
        HexCoord center = new HexCoord(0, 0);
        List<HexTile> tiles = new ArrayList<>();
        tiles.add(new HexTile(center, TerrainType.GRASSLAND));
        for (HexCoord neighbor : center.neighbors()) {
            if (neighbor.equals(new HexCoord(1, 0))) {
                tiles.add(seaEast
                        ? new HexTile(neighbor, TerrainType.SEA,
                        new ResourceDeposit(NaturalResourceType.FISH, 100))
                        : new HexTile(neighbor, TerrainType.PLAINS,
                        new ResourceDeposit(NaturalResourceType.CATTLE, 100)));
            } else if (neighbor.equals(new HexCoord(1, -1))) {
                tiles.add(new HexTile(neighbor, TerrainType.MOUNTAIN, List.of(
                        new ResourceDeposit(NaturalResourceType.STONE, 500),
                        new ResourceDeposit(NaturalResourceType.IRON, 500))));
            } else {
                tiles.add(new HexTile(neighbor, TerrainType.PLAINS));
            }
        }
        GameMap map = new GameMap(tiles);
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(500, 500, 500, 500),
                ResourceBundle.of(1_000, 1_000, 1_000, 1_000));
        TownHall hall = new TownHall(BuildingId.random(), center, stockpile,
                new ProductionQueue());
        PlayerState player = new PlayerState(hall, new UnitCapacity(30),
                new EmpireBorder(Set.copyOf(map.tiles().stream()
                        .map(HexTile::coordinate).toList()), map),
                new VisibilityState(Set.copyOf(map.tiles().stream()
                        .map(HexTile::coordinate).toList()), Set.copyOf(map.tiles().stream()
                        .map(HexTile::coordinate).toList())),
                new TechnologyState());
        GameState state = new GameState(map, player, new TurnState(), new CrisisState(), 91L);
        return new Scenario(state, DefaultRulesetFactory.create(), new ProductionQueueService(
                new civilization.application.service.UpgradeService()));
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(GameState state, GameRules rules, ProductionQueueService queue) {
        private TownHall hall() { return state.player().townHall(); }
    }
}
