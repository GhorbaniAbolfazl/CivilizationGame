package civilization.model.game;

import civilization.model.building.Building;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.Dock;
import civilization.model.map.GameMap;
import civilization.model.map.HexTile;
import civilization.model.player.PlayerState;
import civilization.model.unit.Unit;
import civilization.model.random.GameplayRandom;
import civilization.model.random.SplitMix64GameplayRandom;
import civilization.model.time.SeasonState;
import civilization.model.world.WorldState;

import java.util.Objects;

public final class GameState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final WorldState world;
    private final PlayerState player;
    private final TurnState turn;
    private final CrisisState crises;
    private final long mapSeed;
    private final SeasonState season;
    private final GameplayRandom gameplayRandom;
    private long stateVersion;
    private GameOutcome outcome = GameOutcome.IN_PROGRESS;
    private String outcomeReason = "";

    public GameState(
            GameMap map,
            PlayerState player,
            TurnState turn,
            CrisisState crises,
            long mapSeed) {
        this(new WorldState(map), player, turn, crises, mapSeed,
                new SeasonState(), new SplitMix64GameplayRandom(mapSeed));
    }

    public GameState(
            WorldState world,
            PlayerState player,
            TurnState turn,
            CrisisState crises,
            long mapSeed,
            SeasonState season,
            GameplayRandom gameplayRandom) {
        this.world = Objects.requireNonNull(world, "world");
        this.player = Objects.requireNonNull(player, "player");
        this.turn = Objects.requireNonNull(turn, "turn");
        this.crises = Objects.requireNonNull(crises, "crises");
        this.mapSeed = mapSeed;
        this.season = Objects.requireNonNull(season, "season");
        this.gameplayRandom = Objects.requireNonNull(gameplayRandom, "gameplayRandom");
        validateSpatialReferences();
    }

    public GameMap map() {
        return world.map();
    }

    public WorldState world() { return world; }

    public PlayerState player() {
        return player;
    }

    public TurnState turn() {
        return turn;
    }

    public CrisisState crises() {
        return crises;
    }

    public long mapSeed() {
        return mapSeed;
    }

    public SeasonState season() { return season; }

    public GameplayRandom gameplayRandom() { return gameplayRandom; }

    public long stateVersion() {
        return stateVersion;
    }

    public GameOutcome outcome() {
        return outcome == null ? GameOutcome.IN_PROGRESS : outcome;
    }

    public String outcomeReason() {
        return outcomeReason == null ? "" : outcomeReason;
    }

    public boolean isGameOver() {
        return outcome() != GameOutcome.IN_PROGRESS;
    }

    /** Commits the mandatory defeat condition without advancing or otherwise resolving a turn. */
    public void defeat(String reason) {
        if (isGameOver()) return;
        outcome = GameOutcome.DEFEAT;
        outcomeReason = Objects.requireNonNull(reason, "reason");
        turn.finishGame();
    }

    /** Called once after an application service successfully commits an action or turn. */
    public void incrementVersion() {
        stateVersion = Math.incrementExact(stateVersion);
    }

    public void validateSpatialReferences() {
        GameMap map = map();
        if (!map.contains(player.townHall().position())) {
            throw new IllegalArgumentException("Town Hall must be placed on the game map");
        }
        if (!player.border().owns(player.townHall().position())) {
            throw new IllegalArgumentException("Town Hall hex must belong to the empire");
        }
        for (Unit unit : player.units()) {
            if (!map.contains(unit.position())) {
                throw new IllegalArgumentException("Unit is outside the game map: " + unit.id());
            }
            if (map.requireTile(unit.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("Unit cannot occupy a Mountain Range: " + unit.id());
            }
        }
        for (Building building : player.constructedBuildings()) {
            if (!map.contains(building.position())) {
                throw new IllegalArgumentException("Building is outside the game map: " + building.id());
            }
            if (!player.border().owns(building.position())) {
                throw new IllegalArgumentException("Building is outside the empire: " + building.id());
            }
            if (map.requireTile(building.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("Building cannot occupy a Mountain Range: " + building.id());
            }
            validateBuildingPlacement(building);
        }
        for (var post : world.tradingPosts().all()) {
            if (!map.contains(post.position())) {
                throw new IllegalArgumentException("Trading Post is outside the game map: " + post.id());
            }
            if (post.position().equals(player.townHall().position())
                    || player.buildingAt(post.position()).isPresent()) {
                throw new IllegalArgumentException(
                        "Trading Post overlaps a player building: " + post.id());
            }
            if (map.requireTile(post.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("Trading Post cannot occupy Mountain Range");
            }
        }
        for (Unit unit : world.units().all()) {
            if (!map.contains(unit.position())) {
                throw new IllegalArgumentException("World unit is outside the map: " + unit.id());
            }
            if (map.requireTile(unit.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("World unit cannot occupy Mountain Range: " + unit.id());
            }
        }
        for (var structure : world.structures().all()) {
            if (!map.contains(structure.position())) {
                throw new IllegalArgumentException(
                        "World structure is outside the map: " + structure.id());
            }
            if (map.requireTile(structure.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("World structure cannot occupy Mountain Range");
            }
        }
        for (var animal : world.wildAnimals().all()) {
            if (!map.contains(animal.position())) {
                throw new IllegalArgumentException("Wild animal is outside the map: " + animal.id());
            }
            if (map.requireTile(animal.position()).terrain()
                    == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
                throw new IllegalArgumentException("Wild animal cannot occupy Mountain Range");
            }
        }
        if (player.visibility().discoveredHexes().stream().anyMatch(coord -> !map.contains(coord))
                || player.visibility().currentlyVisibleHexes().stream().anyMatch(coord -> !map.contains(coord))
                || player.visibility().explorerDiscoveredHexes().stream()
                .anyMatch(coord -> !map.contains(coord))) {
            throw new IllegalArgumentException("Visibility state references a coordinate outside the map");
        }
    }

    private void validateBuildingPlacement(Building building) {
        HexTile tile = map().requireTile(building.position());
        building.type().requiredTerrain().ifPresent(requiredTerrain -> {
            if (tile.terrain() != requiredTerrain) {
                throw new IllegalArgumentException(
                        building.type() + " requires terrain " + requiredTerrain);
            }
        });
        if (building.type() == civilization.model.building.BuildingType.MILITARY_STABLE
                && tile.terrain() != civilization.model.map.TerrainType.PLAINS
                && !tile.hasNaturalResource(civilization.model.map.NaturalResourceType.HORSE)) {
            throw new IllegalArgumentException("Military Stable requires Plains or Horse");
        }
        if (building instanceof Dock && (map().neighborsOf(building.position()).stream()
                .noneMatch(neighbor -> neighbor.hasNaturalResource(
                        civilization.model.map.NaturalResourceType.FISH)))) {
            throw new IllegalArgumentException("Dock requires adjacent non-depleted Fish");
        }
        if (building instanceof ProductionBuilding
                && !building.type().eligibleNaturalResources().isEmpty()) {
            boolean hasEligibleResource = building.type().eligibleNaturalResources().stream()
                    .anyMatch(tile::hasNaturalResource);
            if (!hasEligibleResource) {
                throw new IllegalArgumentException(
                        building.type() + " requires one of "
                                + building.type().eligibleNaturalResources());
            }
        }
    }
}
