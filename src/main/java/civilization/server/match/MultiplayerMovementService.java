package civilization.server.match;

import civilization.application.ActionFailure;
import civilization.application.event.UnitMovedEvent;
import civilization.application.service.MovementService;
import civilization.application.service.VisibilityService;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import civilization.server.session.PlayerId;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class MultiplayerMovementService {
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final MovementService movement;

    // PHASE 3 - Added in Phase 3
    public MultiplayerMovementService(GameRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.movement = new MovementService(new VisibilityService());
    }

    // PHASE 3 - Added in Phase 3
    public UnitMovedEvent move(MultiplayerGameState state, PlayerId actor,
                               String requestedUnitId, HexCoord destination) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(destination, "destination");
        UnitId unitId = parseUnitId(requestedUnitId);
        Unit unit;
        try {
            unit = state.requirePlayer(actor).empire().requireUnit(unitId);
        } catch (IllegalArgumentException failure) {
            throw new MatchException("UNIT_NOT_OWNED",
                    "The requested unit is not owned by this player");
        }
        // PHASE 3 - Added in Phase 3
        boolean occupiedByAnotherUnit = state.players().values().stream()
                .flatMap(player -> player.empire().units().stream())
                .filter(Unit::isActive)
                .filter(candidate -> !candidate.id().equals(unit.id()))
                .anyMatch(candidate -> candidate.position().equals(destination));
        if (occupiedByAnotherUnit) {
            throw new MatchException("DESTINATION_OCCUPIED",
                    "Another unit occupies the destination hex");
        }
        // PHASE 3 - Added in Phase 3
        try {
            return movement.move(state.activePlayerView(actor), unit.id(), destination, rules);
        } catch (ActionFailure failure) {
            String message = failure.getMessage() == null ? "Movement is not legal" : failure.getMessage();
            String code = message.contains("AP") ? "INSUFFICIENT_AP" : "INVALID_MOVE";
            throw new MatchException(code, message);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new MatchException("INVALID_MOVE",
                    failure.getMessage() == null ? "Movement is not legal" : failure.getMessage());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static UnitId parseUnitId(String value) {
        try {
            return new UnitId(UUID.fromString(Objects.requireNonNull(value, "unitId")));
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new MatchException("INVALID_UNIT_ID", "Unit ID is not valid");
        }
    }
}
