package civilization.bonus;

import civilization.application.GameFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.service.AutoExploreService;
import civilization.application.service.MovementService;
import civilization.application.service.VisibilityService;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
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
import civilization.model.unit.ActionPoints;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.UnitId;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic coverage for optional systems; no Swing display is required. */
public final class BonusFeatureTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();

    private BonusFeatureTestSuite() {
    }

    public static void main(String[] args) {
        proceduralGenerationIsSeededCoherentAndPlayable();
        autoExplorePrioritizesNewDiscoveryAndUsesMovementRules();
        autoExploreDoesNotCrossUnsafeFrontier();
        System.out.println("BonusFeatureTestSuite: all checks passed");
    }

    private static void proceduralGenerationIsSeededCoherentAndPlayable() {
        SeededProceduralMapGenerator generator = new SeededProceduralMapGenerator();
        HexCoord center = new HexCoord(0, 0);
        GameMap first = generator.generate(8, 9241L, center);
        GameMap second = generator.generate(8, 9241L, center);
        GameMap different = generator.generate(8, 9242L, center);
        check(signature(first).equals(signature(second)),
                "The same map seed must reproduce the same terrain and resources");
        check(!signature(first).equals(signature(different)),
                "Different seeds should produce different coherent maps");
        check(first.size() == 217, "Radius-eight procedural map has the wrong hex count");
        check(first.tiles().stream().anyMatch(tile -> tile.terrain() == TerrainType.FOREST
                        && tile.coordinate().distanceTo(center) <= 2),
                "Every generated game needs reachable Wood within two hexes of Town Hall");
        check(terrainIsOneConnectedFeature(first, TerrainType.MOUNTAIN),
                "Generated mountain range must form one connected feature");
        check(first.tiles().stream().filter(tile -> tile.terrain() == TerrainType.FOREST)
                        .allMatch(tile -> first.neighborsOf(tile.coordinate()).stream()
                                .anyMatch(neighbor -> neighbor.terrain() == TerrainType.FOREST)),
                "Generated forests must form clusters rather than isolated noise");

        for (long seed = 0; seed < 64; seed++) {
            GameMap map = generator.generate(8, seed, center);
            check(map.tiles().stream().anyMatch(tile -> tile.terrain() == TerrainType.FOREST
                            && tile.coordinate().distanceTo(center) <= 2),
                    "Nearby forest guarantee failed for seed " + seed);
            check(terrainIsOneConnectedFeature(map, TerrainType.MOUNTAIN),
                    "Mountain range disconnected for seed " + seed);
            check(map.tiles().stream().filter(tile -> tile.terrain() == TerrainType.FOREST)
                            .allMatch(tile -> map.neighborsOf(tile.coordinate()).stream()
                                    .anyMatch(neighbor -> neighbor.terrain() == TerrainType.FOREST)),
                    "Isolated forest generated for seed " + seed);
        }
    }

    private static void autoExplorePrioritizesNewDiscoveryAndUsesMovementRules() {
        VisibilityService visibility = new VisibilityService();
        GameMap map = new SeededProceduralMapGenerator().generate(8, 31L, new HexCoord(0, 0));
        GameState state = new GameFactory(visibility).create(
                map, new HexCoord(0, 0), 31L, RULES);
        ExplorerUnit explorer = state.player().units().stream()
                .filter(ExplorerUnit.class::isInstance)
                .map(ExplorerUnit.class::cast)
                .findFirst().orElseThrow();
        AutoExploreService auto = new AutoExploreService(new MovementService(visibility));
        Set<HexCoord> before = Set.copyOf(state.player().visibility().discoveredHexes());
        int apBefore = explorer.actionPoints().current();

        var event = auto.moveNext(state, explorer.id(), RULES).orElseThrow();
        check(event.from().equals(new HexCoord(0, 0))
                        && event.to().equals(explorer.position())
                        && event.from().isAdjacentTo(event.to()),
                "Auto-Explore must commit exactly one normal adjacent movement step");
        check(state.player().visibility().discoveredHexes().size() > before.size(),
                "Auto-Explore must prioritize a step that reveals new territory");
        int expectedCost = RULES.movementCost(map.requireTile(event.to()).terrain());
        check(explorer.actionPoints().current() == apBefore - expectedCost,
                "Auto-Explore must consume the normal destination terrain AP cost");
    }

    private static void autoExploreDoesNotCrossUnsafeFrontier() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord frontier = new HexCoord(1, 0);
        HexCoord beyond = new HexCoord(2, 0);
        GameMap map = new GameMap(List.of(
                new HexTile(center, TerrainType.GRASSLAND),
                new HexTile(frontier, TerrainType.MOUNTAIN,
                        new ResourceDeposit(NaturalResourceType.STONE, 20)),
                new HexTile(beyond, TerrainType.GRASSLAND)));
        TownHall hall = new TownHall(
                BuildingId.random(), center,
                new ResourceStockpile(ResourceBundle.ZERO, ResourceBundle.of(20, 20, 20, 20)),
                new ProductionQueue());
        PlayerState player = new PlayerState(
                hall, new UnitCapacity(5), new EmpireBorder(Set.of(center), map),
                new VisibilityState(Set.of(center), Set.of(center)), new TechnologyState());
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8, 4));
        player.addUnit(explorer);
        GameState state = new GameState(map, player, new TurnState(), new CrisisState(), 2L);
        AutoExploreService auto = new AutoExploreService(
                new MovementService(new VisibilityService()));

        check(auto.planNext(state, explorer.id(), RULES).isEmpty(),
                "Auto-Explore must not enter an unknown mountain when AP cannot also pay return cost");
        check(explorer.position().equals(center) && explorer.actionPoints().current() == 4,
                "Rejected unsafe Auto-Explore planning must not mutate position or AP");
    }

    private static boolean terrainIsOneConnectedFeature(GameMap map, TerrainType terrain) {
        Set<HexCoord> feature = new HashSet<>();
        map.tiles().stream().filter(tile -> tile.terrain() == terrain)
                .map(HexTile::coordinate).forEach(feature::add);
        if (feature.isEmpty()) {
            return false;
        }
        Set<HexCoord> reached = new HashSet<>();
        ArrayDeque<HexCoord> queue = new ArrayDeque<>();
        queue.add(feature.iterator().next());
        while (!queue.isEmpty()) {
            HexCoord coordinate = queue.remove();
            if (!reached.add(coordinate)) {
                continue;
            }
            coordinate.neighbors().stream().filter(feature::contains).forEach(queue::add);
        }
        return reached.equals(feature);
    }

    private static List<String> signature(GameMap map) {
        List<String> result = new ArrayList<>();
        map.tiles().stream().sorted(java.util.Comparator
                        .comparingInt((HexTile tile) -> tile.coordinate().q())
                        .thenComparingInt(tile -> tile.coordinate().r()))
                .forEach(tile -> result.add(tile.coordinate() + ":" + tile.terrain() + ":"
                        + tile.resourceDeposits().keySet()));
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
