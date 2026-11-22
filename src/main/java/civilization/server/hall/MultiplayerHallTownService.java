package civilization.server.hall;

import civilization.application.service.VisibilityService;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.technology.ProductionQueue;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.Unit;
import civilization.rules.GameRules;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.session.PlayerId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class MultiplayerHallTownService {
    // PHASE 3 - Added in Phase 3
    public static final ResourceBundle HALL_TOWN_COST = ResourceBundle.of(30, 60, 50, 20);
    // PHASE 3 - Added in Phase 3
    public static final int HALL_TOWN_AP_COST = 2;
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final VisibilityService visibility = new VisibilityService();

    // PHASE 3 - Added in Phase 3
    public MultiplayerHallTownService(GameRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // PHASE 3 - Added in Phase 3
    public HallTownBuildResult build(MultiplayerGameState state, PlayerId playerId,
                                     String builderValue, HexCoord target) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(target, "target");
        MultiplayerPlayerState player = state.requirePlayer(playerId);
        BuilderUnit builder = requireOwnedBuilder(player, builderValue);
        validatePlacement(state, player, builder, target);
        if (!builder.actionPoints().canSpend(HALL_TOWN_AP_COST)) {
            throw new HallTownException("INSUFFICIENT_BUILDER_AP", "Builder requires 2 AP to establish a Hall Town");
        }
        var treasury = player.empire().townHall().stockpile();
        if (!treasury.canAfford(HALL_TOWN_COST)) {
            throw new HallTownException("INSUFFICIENT_HALL_RESOURCES", "Insufficient resources to establish a Hall Town");
        }
        Set<HexCoord> region = controlledRegion(state, playerId, target);
        BuildingId hallId = uniqueHallId(state);
        TownHall hall = new TownHall(hallId, target, treasury, new ProductionQueue(),
                player.empire().factionId());
        // PHASE 3 - Added in Phase 3
        treasury.debit(HALL_TOWN_COST);
        builder.actionPoints().spend(HALL_TOWN_AP_COST);
        player.empire().addTownHall(hall, region, state.map());
        builder.consumeCharge();
        if (!builder.isActive()) player.empire().removeUnit(builder.id());
        visibility.refresh(state.activePlayerView(playerId), rules);
        state.activePlayerView(playerId).validateSpatialReferences();
        return new HallTownBuildResult(playerId, hallId, target, HALL_TOWN_COST, region);
    }

    // PHASE 3 - Added in Phase 3
    private static BuilderUnit requireOwnedBuilder(MultiplayerPlayerState player,
                                                   String builderValue) {
        UUID builderId;
        try { builderId = UUID.fromString(builderValue); }
        catch (RuntimeException failure) {
            throw new HallTownException("INVALID_BUILDER", "Builder ID is invalid");
        }
        Unit unit;
        try { unit = player.empire().requireUnit(new civilization.model.unit.UnitId(builderId)); }
        catch (IllegalArgumentException failure) {
            throw new HallTownException("BUILDER_NOT_OWNED", "Builder must be owned by the requesting player");
        }
        if (!(unit instanceof BuilderUnit builder) || !builder.isActive()
                || !builder.ownerId().equals(player.empire().factionId())) {
            throw new HallTownException("INVALID_BUILDER", "An active owned Builder is required");
        }
        return builder;
    }

    // PHASE 3 - Added in Phase 3
    private static void validatePlacement(MultiplayerGameState state,
                                          MultiplayerPlayerState player,
                                          BuilderUnit builder, HexCoord target) {
        if (!state.map().contains(target)) {
            throw new HallTownException("INVALID_HALL_LOCATION", "Hall Town location is outside the map");
        }
        if (!builder.position().equals(target)) {
            throw new HallTownException("BUILDER_NOT_AT_LOCATION", "Builder must stand on the Hall Town hex");
        }
        TerrainType terrain = state.map().requireTile(target).terrain();
        if (terrain == TerrainType.SEA || terrain == TerrainType.MOUNTAIN_RANGE) {
            throw new HallTownException("INVALID_HALL_TERRAIN", "Hall Town requires traversable land");
        }
        if (!state.discoveredFor(player.playerId()).contains(target)) {
            throw new HallTownException("HALL_LOCATION_UNEXPLORED", "Hall Town location must be explored");
        }
        // PHASE 3 - Added in Phase 3
        if (state.players().values().stream().flatMap(owner -> owner.empire().allBuildings().stream())
                .anyMatch(building -> building.position().equals(target))) {
            throw new HallTownException("HALL_LOCATION_OCCUPIED", "A building already occupies the Hall Town hex");
        }
        if (state.players().values().stream().filter(owner -> !owner.playerId().equals(player.playerId()))
                .anyMatch(owner -> owner.empire().border().owns(target))) {
            throw new HallTownException("FOREIGN_TERRITORY", "Hall Town cannot be established in another player's territory");
        }
        if (state.players().values().stream().filter(owner -> !owner.playerId().equals(player.playerId()))
                .flatMap(owner -> owner.empire().units().stream())
                .anyMatch(unit -> unit.position().equals(target))) {
            throw new HallTownException("HALL_LOCATION_OCCUPIED", "A foreign unit occupies the Hall Town hex");
        }
        if (state.world().territories().ownerAt(target).isPresent()
                || !state.world().units().at(target).isEmpty()
                || state.world().structures().at(target).isPresent()
                || state.world().wildAnimals().at(target).isPresent()
                || state.world().tribes().atCamp(target).isPresent()
                || state.world().tradingPosts().all().stream()
                    .anyMatch(post -> post.position().equals(target))) {
            throw new HallTownException("HALL_LOCATION_OCCUPIED", "A non-player entity occupies the Hall Town hex");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static Set<HexCoord> controlledRegion(MultiplayerGameState state,
                                                   PlayerId ownerId, HexCoord center) {
        HashSet<HexCoord> region = new HashSet<>();
        // PHASE 3 - Added in Phase 3
        for (HexCoord coordinate : state.map().coordinatesWithin(center, 1)) {
            if (state.map().requireTile(coordinate).terrain() == TerrainType.MOUNTAIN_RANGE) continue;
            if (state.world().territories().ownerAt(coordinate).isPresent()) continue;
            boolean foreign = state.players().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(ownerId))
                    .anyMatch(entry -> entry.getValue().empire().border().owns(coordinate));
            if (!foreign) region.add(coordinate);
        }
        if (!region.contains(center)) {
            throw new HallTownException("INVALID_HALL_REGION", "Hall Town cannot create a controlled region here");
        }
        return Set.copyOf(region);
    }

    // PHASE 3 - Added in Phase 3
    private static BuildingId uniqueHallId(MultiplayerGameState state) {
        BuildingId id;
        // PHASE 3 - Added in Phase 3
        do { id = BuildingId.from(state.activePlayerView(state.activePlayerId()).gameplayRandom()); }
        while (containsBuilding(state, id));
        return id;
    }

    // PHASE 3 - Added in Phase 3
    private static boolean containsBuilding(MultiplayerGameState state, BuildingId id) {
        return state.players().values().stream().flatMap(player -> player.empire().allBuildings().stream())
                .anyMatch(building -> building.id().equals(id));
    }
}
