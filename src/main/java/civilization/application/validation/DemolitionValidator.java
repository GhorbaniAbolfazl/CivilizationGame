package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.game.GameState;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import java.util.Objects;

/** Validates irreversible owner-requested demolition before any AP/state mutation. */
public final class DemolitionValidator {
    public static final int AP_COST = 1;

    public ActionPreview building(GameState state, UnitId builderId, BuildingId buildingId) {
        try {
            BuilderUnit builder = requireBuilder(state, builderId);
            var phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) return phase;
            var building = state.player().requireBuilding(buildingId);
            if (building instanceof TownHall) return ActionPreview.reject("Town Hall cannot be demolished");
            if (!building.ownerId().equals(state.player().factionId())) {
                return ActionPreview.reject("Only an owned building can be demolished");
            }
            if (building.status() == civilization.model.building.BuildingStatus.DESTROYED) {
                return ActionPreview.reject("Building is already destroyed");
            }
            if (!builder.position().equals(building.position())
                    && !builder.position().isAdjacentTo(building.position())) {
                return ActionPreview.reject("Builder must be on or adjacent to the building");
            }
            return ap(builder);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public ActionPreview infrastructure(GameState state, UnitId builderId, InfrastructureId id) {
        try {
            BuilderUnit builder = requireBuilder(state, builderId);
            var phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) return phase;
            var infrastructure = state.world().infrastructure().byId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown infrastructure: " + id));
            if (infrastructure instanceof Road road) {
                if (!road.ownerId().equals(state.player().factionId())) {
                    return ActionPreview.reject("Only an owned Road can be demolished");
                }
                if (!builder.position().equals(road.position())
                        && !builder.position().isAdjacentTo(road.position())) {
                    return ActionPreview.reject("Builder must be on or adjacent to the Road");
                }
            } else if (infrastructure instanceof Wall wall) {
                if (!wall.ownerId().equals(state.player().factionId())) {
                    return ActionPreview.reject("Only an owned Wall can be demolished");
                }
                if (!wall.edge().touches(builder.position())) {
                    return ActionPreview.reject("Builder must stand on a Wall endpoint");
                }
            } else {
                return ActionPreview.reject("Infrastructure cannot be demolished");
            }
            return ap(builder);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static BuilderUnit requireBuilder(GameState state, UnitId id) {
        Objects.requireNonNull(state, "state");
        var unit = state.player().requireUnit(Objects.requireNonNull(id, "builderId"));
        if (!(unit instanceof BuilderUnit builder) || !builder.isActive()) {
            throw new IllegalArgumentException("An active Builder is required");
        }
        return builder;
    }

    private static ActionPreview ap(BuilderUnit builder) {
        return builder.actionPoints().canSpend(AP_COST)
                ? ActionPreview.allow() : ActionPreview.reject("Insufficient Builder AP");
    }
}
