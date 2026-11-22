package civilization.server.match;

import civilization.application.ActionFailure;
import civilization.application.service.ConstructionService;
import civilization.application.service.VisibilityService;
import civilization.model.building.Building;
import civilization.model.building.BuildingType;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import civilization.server.session.PlayerId;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class MultiplayerConstructionService {
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final ConstructionService construction =
            new ConstructionService(new VisibilityService());

    // PHASE 3 - Added in Phase 3
    public MultiplayerConstructionService(GameRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // PHASE 3 - Added in Phase 3
    public Building build(MultiplayerGameState state, PlayerId actor, String builderId,
                          String requestedType, HexCoord target) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        BuildingType type = parseType(requestedType);
        if (type == BuildingType.TOWN_HALL || type == BuildingType.APOTHECARY) {
            throw new MatchException("INVALID_BUILD_TYPE",
                    "Use the dedicated Hall Town or Apothecary action for this structure");
        }
        // PHASE 3 - Added in Phase 3
        boolean occupiedByOpponent = state.players().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(actor))
                .map(java.util.Map.Entry::getValue)
                .anyMatch(player -> player.empire().units().stream().filter(Unit::isActive)
                        .anyMatch(unit -> unit.position().equals(target))
                        || player.empire().allBuildings().stream().filter(Building::isActive)
                        .anyMatch(building -> building.position().equals(target)));
        if (occupiedByOpponent) {
            throw new MatchException("BUILD_LOCATION_OCCUPIED",
                    "An opposing asset occupies the construction hex");
        }
        // PHASE 3 - Added in Phase 3
        try {
            return construction.construct(state.activePlayerView(actor), parseUnitId(builderId),
                    type, target, rules);
        } catch (ActionFailure failure) {
            throw new MatchException("BUILD_REJECTED",
                    failure.getMessage() == null ? "Construction is not legal" : failure.getMessage());
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new MatchException("BUILD_REJECTED",
                    failure.getMessage() == null ? "Construction is not legal" : failure.getMessage());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static BuildingType parseType(String value) {
        try {
            return BuildingType.valueOf(Objects.requireNonNull(value, "buildingType"));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new MatchException("INVALID_BUILD_TYPE", "Building type is not valid");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static UnitId parseUnitId(String value) {
        try {
            return new UnitId(UUID.fromString(Objects.requireNonNull(value, "builderId")));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new MatchException("INVALID_BUILDER_ID", "Builder ID is not valid");
        }
    }
}
