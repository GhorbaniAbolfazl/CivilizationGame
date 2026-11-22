package civilization.map;

import civilization.application.ActionFailure;
import civilization.application.query.MapSnapshotFactory;
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
import civilization.view.animation.AnimationManager;
import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;
import civilization.view.render.WorldPoint;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Focused deterministic audit suite for the map, camera, vision, movement, and animation. */
public final class MapMovementTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();

    private MapMovementTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        axialCoordinatesHaveExactlySixCorrectNeighbors();
        terrainAndResourcePlacementAreValidated();
        ownershipRejectsCoordinatesOutsideMap();
        movementLegalityTerrainCostAndApAreAtomic();
        visibilityAndFogPreserveDiscoveryWithoutLeakingSecrets();
        cameraUsesDiscreteZoomAndReversibleCoordinateConversion();
        animationInterpolatesWithoutChangingLogicalPosition();
        System.out.println("MapMovementTestSuite: all checks passed");
    }

    private static void axialCoordinatesHaveExactlySixCorrectNeighbors() {
        HexCoord origin = new HexCoord(0, 0);
        Set<HexCoord> expected = Set.of(
                new HexCoord(1, 0), new HexCoord(1, -1), new HexCoord(0, -1),
                new HexCoord(-1, 0), new HexCoord(-1, 1), new HexCoord(0, 1));
        check(Set.copyOf(origin.neighbors()).equals(expected),
                "Axial neighbor calculation must return the six correct hexes");
        check(expected.stream().allMatch(origin::isAdjacentTo),
                "Every returned neighbor must be axial distance one");
    }

    private static void terrainAndResourcePlacementAreValidated() {
        new HexTile(new HexCoord(0, 0), TerrainType.FOREST,
                new ResourceDeposit(NaturalResourceType.WOOD, 5));
        new HexTile(new HexCoord(1, 0), TerrainType.MOUNTAIN, List.of(
                new ResourceDeposit(NaturalResourceType.STONE, 5),
                new ResourceDeposit(NaturalResourceType.IRON, 2)));
        expectFailure(() -> new HexTile(new HexCoord(0, 0), TerrainType.GRASSLAND,
                        new ResourceDeposit(NaturalResourceType.CATTLE, 5)),
                "A resource on incompatible terrain must be rejected");
        expectFailure(() -> new HexTile(new HexCoord(0, 0), TerrainType.MOUNTAIN),
                "Every Mountain must contain Stone");
    }

    private static void ownershipRejectsCoordinatesOutsideMap() {
        HexCoord center = new HexCoord(0, 0);
        GameMap map = new GameMap(List.of(grass(center)));
        EmpireBorder border = new EmpireBorder(Set.of(center), map);
        check(border.owns(center), "Initial owned coordinate was not retained");
        expectFailure(() -> border.claimAll(Set.of(new HexCoord(1, 0)), map),
                "Ownership must never include a coordinate outside the map");
        check(border.ownedHexes().equals(Set.of(center)),
                "Failed ownership mutation must be atomic");
    }

    private static void movementLegalityTerrainCostAndApAreAtomic() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord forest = new HexCoord(1, 0);
        HexCoord mountain = new HexCoord(0, 1);
        HexCoord remote = new HexCoord(-1, 0);
        GameMap map = new GameMap(List.of(
                grass(center), forest(forest), mountain(mountain), grass(remote)));
        PlayerState player = player(map, center, Set.of(center));
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        player.addUnit(explorer);
        GameState state = state(map, player);
        MovementService movement = new MovementService(new VisibilityService());

        check(movement.movementCost(state, forest, RULES) == 2,
                "Forest terrain must use its configured movement cost");
        check(movement.movementCost(state, mountain, RULES) == 4,
                "Mountain terrain must use its configured movement cost");
        var event = movement.move(state, explorer.id(), forest, RULES);
        check(event.from().equals(center) && event.to().equals(forest),
                "Movement event must preserve exact logical endpoints");
        check(explorer.position().equals(forest) && explorer.actionPoints().current() == 6,
                "Legal movement must commit position and spend AP exactly once");

        int apBefore = explorer.actionPoints().current();
        long versionBefore = state.stateVersion();
        expectFailure(() -> movement.move(state, explorer.id(), remote, RULES),
                "Non-adjacent movement must be rejected");
        expectFailure(() -> movement.move(state, explorer.id(), new HexCoord(4, 4), RULES),
                "Movement outside the map must be rejected");
        check(explorer.position().equals(forest)
                        && explorer.actionPoints().current() == apBefore
                        && state.stateVersion() == versionBefore,
                "Invalid movement must not partially mutate position, AP, or version");

        explorer.actionPoints().spend(6);
        expectFailure(() -> movement.move(state, explorer.id(), center, RULES),
                "Movement must be rejected when terrain AP is unaffordable");
        check(explorer.position().equals(forest) && explorer.actionPoints().current() == 0,
                "Insufficient-AP movement must be atomic");
    }

    private static void visibilityAndFogPreserveDiscoveryWithoutLeakingSecrets() {
        GameMap map = radiusMapWithHiddenWheat(4, new HexCoord(4, 0));
        HexCoord center = new HexCoord(0, 0);
        PlayerState player = player(map, center, Set.of(center));
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), center, new ActionPoints(8));
        player.addUnit(explorer);
        GameState state = state(map, player);
        VisibilityService visibility = new VisibilityService();
        visibility.refresh(state, RULES);

        HexCoord seen = new HexCoord(3, 0);
        HexCoord unknown = new HexCoord(4, 0);
        check(player.visibility().isCurrentlyVisible(seen)
                        && player.visibility().isDiscovered(seen),
                "Explorer radius must reveal and permanently discover distance three");
        check(!player.visibility().isDiscovered(unknown),
                "Hex outside every vision radius must stay unknown");
        var snapshot = new MapSnapshotFactory().create(state);
        var hidden = snapshot.hexes().stream()
                .filter(hex -> hex.coordinate().equals(unknown)).findFirst().orElseThrow();
        check(hidden.discoveredTerrain().isEmpty() && hidden.remainingResources().isEmpty(),
                "Fog snapshot must not leak unknown terrain/resource information");

        explorer.moveTo(new HexCoord(-1, 0));
        visibility.refresh(state, RULES);
        check(player.visibility().isDiscovered(seen)
                        && !player.visibility().isCurrentlyVisible(seen),
                "A previously visible hex must remain discovered after leaving current vision");
    }

    private static void cameraUsesDiscreteZoomAndReversibleCoordinateConversion() {
        HexLayout layout = new HexLayout(40.0);
        CameraState camera = new CameraState();
        check(camera.zoom() == 1.0, "Camera must begin on a configured discrete zoom level");
        camera.zoomIn();
        check(camera.zoom() == 1.25, "Zoom in must choose the next discrete level");
        while (camera.zoomIn()) {
            // advance to upper bound
        }
        check(camera.zoom() == 2.0 && !camera.zoomIn(), "Zoom must clamp to its maximum level");
        camera.panScreenPixels(100, -50);
        check(camera.center().equals(new WorldPoint(50, -25)),
                "Screen-space pan must account for current zoom");

        HexCoord coordinate = new HexCoord(2, -1);
        check(layout.coordinateAt(layout.center(coordinate)).equals(coordinate),
                "Hex world conversion must round-trip axial coordinates");
        var screen = camera.worldToScreen(layout.center(coordinate), 800, 600);
        check(layout.coordinateAt(camera.screenToWorld(screen.x, screen.y, 800, 600)).equals(coordinate),
                "Camera screen/world conversion must preserve target hex");
    }

    private static void animationInterpolatesWithoutChangingLogicalPosition() throws Exception {
        HexCoord from = new HexCoord(0, 0);
        HexCoord to = new HexCoord(1, 0);
        GameMap map = new GameMap(List.of(grass(from), grass(to)));
        PlayerState player = player(map, from, Set.of(from));
        ExplorerUnit explorer = new ExplorerUnit(UnitId.random(), from, new ActionPoints(8));
        player.addUnit(explorer);
        GameState state = state(map, player);
        var event = new MovementService(new VisibilityService()).move(state, explorer.id(), to, RULES);
        check(explorer.position().equals(to), "Logical movement must commit before visual animation");

        HexLayout layout = new HexLayout(30.0);
        AtomicLong clock = new AtomicLong(1_000);
        AnimationManager[] holder = new AnimationManager[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new AnimationManager(
                layout, () -> { }, clock::get, 1_000, 16));
        AnimationManager animations = holder[0];
        AtomicInteger animationStateChanges = new AtomicInteger();
        animations.setAnimationStateChanged(animationStateChanges::incrementAndGet);
        SwingUtilities.invokeAndWait(() -> animations.start(event));
        check(animations.visualPosition(explorer.id()).orElseThrow().equals(layout.center(from)),
                "Animation must begin at the old visual hex, never the committed destination");
        clock.set(1_500);
        WorldPoint middle = animations.visualPosition(explorer.id()).orElseThrow();
        check(!middle.equals(layout.center(from)) && !middle.equals(layout.center(to)),
                "Animation must visibly interpolate through an intermediate position");
        check(explorer.position().equals(to) && explorer.actionPoints().current() == 7,
                "Animation frames must not mutate logical position or AP");
        clock.set(2_000);
        SwingUtilities.invokeAndWait(animations::tick);
        check(!animations.hasActiveAnimations(), "Completed animation overlay must be removed");
        check(animationStateChanges.get() == 2,
                "Animation listeners must receive both input-lock and input-unlock transitions");
        check(explorer.position().equals(to), "Animation completion must not reapply movement");

        expectFailure(() -> animations.startIfCurrent(event, event.stateVersion() + 1),
                "Stale movement events must not start a visual track");
    }

    private static PlayerState player(GameMap map, HexCoord center, Set<HexCoord> territory) {
        TownHall hall = new TownHall(
                BuildingId.random(),
                center,
                new ResourceStockpile(ResourceBundle.ZERO, ResourceBundle.of(100, 100, 100, 100)),
                new ProductionQueue());
        return new PlayerState(
                hall,
                new UnitCapacity(10),
                new EmpireBorder(territory, map),
                new VisibilityState(),
                new TechnologyState());
    }

    private static GameState state(GameMap map, PlayerState player) {
        return new GameState(map, player, new TurnState(), new CrisisState(), 1L);
    }

    private static HexTile grass(HexCoord coordinate) {
        return new HexTile(coordinate, TerrainType.GRASSLAND);
    }

    private static HexTile forest(HexCoord coordinate) {
        return new HexTile(coordinate, TerrainType.FOREST,
                new ResourceDeposit(NaturalResourceType.WOOD, 100));
    }

    private static HexTile mountain(HexCoord coordinate) {
        return new HexTile(coordinate, TerrainType.MOUNTAIN,
                new ResourceDeposit(NaturalResourceType.STONE, 100));
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

    private static GameMap radiusMapWithHiddenWheat(int radius, HexCoord wheatCoordinate) {
        List<HexTile> tiles = new ArrayList<>();
        for (HexTile tile : radiusMap(radius).tiles()) {
            if (tile.coordinate().equals(wheatCoordinate)) {
                tiles.add(new HexTile(
                        wheatCoordinate,
                        TerrainType.GRASSLAND,
                        new ResourceDeposit(NaturalResourceType.WHEAT, 20)));
            } else {
                tiles.add(tile);
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
