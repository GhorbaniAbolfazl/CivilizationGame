package civilization.phase2;

import civilization.application.service.BearAttackService;
import civilization.application.service.DisasterEligibilityPolicy;
import civilization.application.service.DisasterNotificationProjector;
import civilization.application.service.DisasterResolver;
import civilization.application.service.DisasterResult;
import civilization.application.service.DisasterSelector;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Road;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.random.GameplayRandom;
import civilization.model.random.GameplayRandomSnapshot;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.time.Season;
import civilization.model.time.SeasonState;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.UnitId;
import civilization.rules.SeasonPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Focused deterministic acceptance checks for mandatory seasons and disasters. */
public final class SeasonDisasterSystemTestSuite {
    private SeasonDisasterSystemTestSuite() { }

    public static void main(String[] args) {
        seasonTransitionsAreExactlyTenTurns();
        globalSeasonModifiersArePure();
        disasterProbabilityHasAnExactFivePercentBoundary();
        disasterSelectionUsesOnlyEligibleLocations();
        hiddenNotificationsLeakNoTypeOrLocation();
        earthquakeAppliesItsFootprintAndDamage();
        floodAppliesDamageDestructionAndCleanup();
        bearAttackSpawnsScalesCoolsDownAndCleansUp();
        bearAttackUsesOneDeterministicThirtyFivePercentRoll();
        System.out.println("SeasonDisasterSystemTestSuite: all checks passed");
    }

    private static void seasonTransitionsAreExactlyTenTurns() {
        SeasonState calendar = new SeasonState();
        Season[] expected = {Season.SPRING, Season.SUMMER, Season.AUTUMN, Season.WINTER};
        for (Season season : expected) {
            check(calendar.season() == season && calendar.turnWithinSeason() == 1,
                    "Wrong season start: " + season);
            for (int turn = 2; turn <= 10; turn++) {
                check(!calendar.advanceOneTurn(), "Season changed before turn 10");
                check(calendar.turnWithinSeason() == turn, "Season turn drifted");
            }
            check(calendar.advanceOneTurn(), "Season did not change after exactly 10 turns");
        }
        check(calendar.season() == Season.SPRING && calendar.turnWithinSeason() == 1,
                "Calendar did not wrap after four seasons");
    }

    private static void globalSeasonModifiersArePure() {
        SeasonPolicy policy = new SeasonPolicy();
        check(policy.productionAdjustment(Season.SPRING, BuildingType.FARM) == 1,
                "Spring Farm bonus missing");
        check(policy.productionAdjustment(Season.SPRING, BuildingType.STABLE) == 1,
                "Spring Stable bonus missing");
        check(policy.productionAdjustment(Season.WINTER, BuildingType.FARM) == -1,
                "Winter Farm penalty missing");
        check(policy.productionAdjustment(Season.SUMMER, BuildingType.FARM) == 0,
                "Summer must be baseline");
        check(policy.movementAdjustment(Season.AUTUMN, TerrainType.SEA) == 1,
                "Autumn Sea penalty missing");
        check(policy.movementAdjustment(Season.WINTER, TerrainType.FOREST) == 1
                && policy.movementAdjustment(Season.WINTER, TerrainType.SEA) == 0,
                "Winter land scope is wrong");
    }

    private static void disasterProbabilityHasAnExactFivePercentBoundary() {
        CountingRandom hit = new CountingRandom(0.049999, 0);
        check(new DisasterSelector().select(state(radius(1, TerrainType.PLAINS),
                new HexCoord(0, 0), Season.SPRING, hit)).isPresent(), "Roll below 5% missed");
        check(hit.doubleCalls == 1, "Disaster chance was not evaluated exactly once");
        CountingRandom miss = new CountingRandom(0.05, 0);
        check(new DisasterSelector().select(state(radius(1, TerrainType.PLAINS),
                new HexCoord(0, 0), Season.SPRING, miss)).isEmpty(), "5% boundary was inclusive");
        check(miss.doubleCalls == 1 && miss.intCalls == 0,
                "A failed chance consumed selection randomness");
    }

    private static void disasterSelectionUsesOnlyEligibleLocations() {
        HexCoord hall = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(tile(hall, TerrainType.SEA),
                tile(forest, TerrainType.FOREST)));
        GameState state = state(map, forest, Season.SUMMER, new CountingRandom(0, 0));
        DisasterSelector.Selection selection = new DisasterSelector().select(state).orElseThrow();
        check(new DisasterEligibilityPolicy().eligible(state, selection.type(), selection.center()),
                "Selector returned an ineligible pair");
        check(selection.center().equals(forest), "Dry-land disaster selected Sea");
        check(selection.type() != civilization.model.disaster.DisasterType.FLOOD,
                "Flood entered a non-Autumn draw");
    }

    private static void hiddenNotificationsLeakNoTypeOrLocation() {
        GameState state = state(radius(2, TerrainType.PLAINS), new HexCoord(-2, 0),
                Season.SPRING, new CountingRandom(1, 0));
        HexCoord hidden = new HexCoord(2, 0);
        DisasterResult result = new DisasterResolver().earthquake(state, hidden);
        var view = new DisasterNotificationProjector().project(state, result);
        check(!view.visible() && view.type().isEmpty() && view.affectedHexes().isEmpty(),
                "Hidden event leaked type or coordinates");
        check(!view.message().toLowerCase().contains("earthquake")
                && !view.message().contains(hidden.toString()), "Hidden message leaked event detail");
    }

    private static void earthquakeAppliesItsFootprintAndDamage() {
        GameState state = state(radius(2, TerrainType.PLAINS), new HexCoord(0, 0),
                Season.SPRING, new CountingRandom(1, 0));
        state.player().townHall().health().damage(180);
        var soldier = new SwordsmanUnit(UnitId.random(), new HexCoord(1, 0),
                new ActionPoints(2), state.player().factionId(), 100, 1);
        state.player().addUnit(soldier);
        var civilian = new ExplorerUnit(UnitId.random(), new HexCoord(0, 1),
                new ActionPoints(8));
        state.player().addUnit(civilian);
        DisasterResult result = new DisasterResolver().earthquake(state, new HexCoord(0, 0));
        check(result.occurrence().affectedHexes().size() == 19,
                "Earthquake radius two must contain 19 hexes on a full map");
        check(state.player().townHall().health().current() == 1,
                "Earthquake bypassed Town Hall minimum HP");
        check(soldier.health().current() == 90, "Earthquake unit damage is not 10");
        check(civilian.healthState().orElseThrow().current() == 90,
                "Earthquake skipped a non-military unit's HP damage");
        check(result.animationPath().equals(List.of(new HexCoord(0, 0))),
                "Earthquake did not expose an animation origin");
    }

    private static void floodAppliesDamageDestructionAndCleanup() {
        GameState state = state(radius(2, TerrainType.GRASSLAND), new HexCoord(-2, 0),
                Season.AUTUMN, new CountingRandom(1, 0));
        HexCoord center = new HexCoord(0, 0);
        state.map().addRiver(center, new HexCoord(1, 0));
        var farm = new ProductionBuilding(BuildingId.random(), BuildingType.FARM, center, 1);
        state.player().addBuilding(farm);
        var road = new Road(InfrastructureId.random(), new HexCoord(0, 1));
        state.world().infrastructure().add(road, state.map());
        var soldier = new SwordsmanUnit(UnitId.random(), new HexCoord(1, 0),
                new ActionPoints(2), state.player().factionId(), 100, 1);
        state.player().addUnit(soldier);
        var civilian = new ExplorerUnit(UnitId.random(), new HexCoord(0, -1),
                new ActionPoints(8));
        state.player().addUnit(civilian);
        DisasterResult result = new DisasterResolver().flood(state, center);
        check(state.player().buildingAt(center).isEmpty(), "Flood did not destroy Farm");
        check(state.world().infrastructure().at(new HexCoord(0, 1)).isEmpty(),
                "Flood did not destroy Road");
        check(soldier.health().current() == 80 && soldier.actionPoints().current() == 0,
                "Flood did not apply unit HP/AP effects");
        check(civilian.healthState().orElseThrow().current() == 80
                        && civilian.actionPoints().current() == 0,
                "Flood skipped non-military HP/AP effects");
        check(state.world().disasters().blocksProductionAt(center),
                "Flood production shutdown was not persisted");
        state.world().disasters().advancePersistentEffects();
        check(!state.world().disasters().blocksProductionAt(center),
                "Flood production shutdown did not clean up");
        check(result.animationPath().size() > 1, "Flood did not expose its spreading path");
    }

    private static void bearAttackSpawnsScalesCoolsDownAndCleansUp() {
        GameState state = state(radius(2, TerrainType.FOREST), new HexCoord(0, 0),
                Season.WINTER, new CountingRandom(1, 0));
        state.player().addUnit(new SwordsmanUnit(UnitId.random(), new HexCoord(1, 0),
                new ActionPoints(2), state.player().factionId(), 100, 1));
        state.player().addUnit(new SwordsmanUnit(UnitId.random(), new HexCoord(0, 1),
                new ActionPoints(2), state.player().factionId(), 100, 1));
        state.player().addUnit(new SwordsmanUnit(UnitId.random(), new HexCoord(-1, 1),
                new ActionPoints(2), state.player().factionId(), 100, 1));
        HexCoord nest = new HexCoord(2, 0);
        DisasterResult result = new DisasterResolver().bearAttack(state, nest);
        check(state.world().wildAnimals().all().size() == 2,
                "Three nearby units must spawn exactly two bears");
        check(state.world().wildAnimals().all().stream().allMatch(animal -> animal.isBear()
                && animal.health().maximum() == 120 && animal.attackStrength() == 35),
                "Bear stats are incorrect");
        check(!new DisasterEligibilityPolicy().eligible(state,
                civilization.model.disaster.DisasterType.BEAR_ATTACK, nest),
                "Five-turn local bear cooldown was not enforced");
        for (var unit : List.copyOf(state.player().units())) state.player().removeUnit(unit.id());
        new BearAttackService().advance(state);
        check(state.world().wildAnimals().all().isEmpty(),
                "Bears were not removed after all local targets left");
        check(result.animationPath().size() == 2, "Bear spawn animation result is incomplete");
    }

    private static void bearAttackUsesOneDeterministicThirtyFivePercentRoll() {
        HexCoord center = new HexCoord(0, 0);
        CountingRandom hitRandom = new CountingRandom(0.349999, 0);
        GameState hit = state(radius(1, TerrainType.FOREST), center, Season.SPRING, hitRandom);
        var defender = new SwordsmanUnit(UnitId.random(), center, new ActionPoints(2),
                hit.player().factionId(), 100, 1);
        hit.player().addUnit(defender);
        var bear = civilization.model.world.WildAnimal.bear(
                civilization.model.world.WildAnimalId.random(), center, center);
        hit.world().wildAnimals().add(bear, hit.map());
        new civilization.application.turn.WildlifeCombatStep(
                new civilization.application.service.CombatService()).execute(
                new civilization.application.turn.TurnContext(hit,
                        civilization.rules.DefaultRulesetFactory.create()));
        check(hitRandom.doubleCalls == 1 && defender.actionPoints().current() == 1,
                "A bear roll below 35% must execute exactly one deterministic attack");

        CountingRandom missRandom = new CountingRandom(0.35, 0);
        GameState miss = state(radius(1, TerrainType.FOREST), center, Season.SPRING, missRandom);
        var untouched = new SwordsmanUnit(UnitId.random(), center, new ActionPoints(2),
                miss.player().factionId(), 100, 1);
        miss.player().addUnit(untouched);
        miss.world().wildAnimals().add(civilization.model.world.WildAnimal.bear(
                civilization.model.world.WildAnimalId.random(), center, center), miss.map());
        new civilization.application.turn.WildlifeCombatStep(
                new civilization.application.service.CombatService()).execute(
                new civilization.application.turn.TurnContext(miss,
                        civilization.rules.DefaultRulesetFactory.create()));
        check(missRandom.doubleCalls == 1 && untouched.actionPoints().current() == 2,
                "A bear roll at 35% must miss without consuming defender AP");
    }

    private static GameState state(GameMap map, HexCoord hallPosition, Season season,
            GameplayRandom random) {
        TownHall hall = new TownHall(BuildingId.random(), hallPosition,
                new ResourceStockpile(ResourceBundle.ZERO, ResourceBundle.of(500, 500, 500, 500)),
                new ProductionQueue());
        Set<HexCoord> land = map.tiles().stream().filter(tile -> tile.terrain() != TerrainType.SEA
                && tile.terrain() != TerrainType.MOUNTAIN_RANGE).map(HexTile::coordinate)
                .collect(java.util.stream.Collectors.toSet());
        PlayerState player = new PlayerState(hall, new UnitCapacity(20),
                new EmpireBorder(land, map), new VisibilityState(Set.of(hallPosition),
                Set.of(hallPosition)), new TechnologyState());
        return new GameState(new civilization.model.world.WorldState(map), player,
                new TurnState(), new CrisisState(), 1L, new SeasonState(season, 1), random);
    }

    private static GameMap radius(int radius, TerrainType terrain) {
        List<HexTile> tiles = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexCoord coord = new HexCoord(q, r);
                if (new HexCoord(0, 0).distanceTo(coord) <= radius) tiles.add(tile(coord, terrain));
            }
        }
        return new GameMap(tiles);
    }

    private static HexTile tile(HexCoord coordinate, TerrainType terrain) {
        return switch (terrain) {
            case FOREST -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.WOOD, 100));
            case MOUNTAIN -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.STONE, 100));
            default -> new HexTile(coordinate, terrain);
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class CountingRandom implements GameplayRandom {
        private static final long serialVersionUID = 1L;
        private final double value;
        private final int selection;
        private int doubleCalls;
        private int intCalls;
        private CountingRandom(double value, int selection) { this.value = value; this.selection = selection; }
        @Override public long nextLong() { return 0; }
        @Override public int nextInt(int bound) { intCalls++; return Math.floorMod(selection, bound); }
        @Override public double nextDouble() { doubleCalls++; return value; }
        @Override public GameplayRandomSnapshot snapshot() {
            return new GameplayRandomSnapshot("test", 1, 0);
        }
    }
}
