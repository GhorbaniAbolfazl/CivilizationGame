package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.game.GameState;
import civilization.model.infrastructure.InfrastructureType;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.TerrainType;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import java.util.Objects;

/** Shared authoritative placement validation for Road and Wall construction. */
public final class InfrastructureConstructionValidator {
    public ActionPreview validateRoad(GameState state, UnitId builderId, HexCoord target,
            GameRules rules) {
        try {
            BuilderUnit builder = requireBuilder(state, builderId);
            Objects.requireNonNull(rules, "rules");
            var phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) return phase;
            var tile = state.map().requireTile(target);
            if (!builder.position().equals(target)) {
                return ActionPreview.reject("Builder must stand on the Road hex");
            }
            if (!state.player().visibility().isDiscovered(target)) {
                return ActionPreview.reject("Road hex must be explored");
            }
            if (!state.player().border().owns(target)) {
                return ActionPreview.reject("Road hex is outside the empire");
            }
            if (state.world().territories().ownerAt(target).isPresent()) {
                return ActionPreview.reject("Road cannot use uncaptured tribe or enemy territory");
            }
            if (tile.terrain() == TerrainType.SEA || tile.terrain() == TerrainType.MOUNTAIN_RANGE) {
                return ActionPreview.reject("Road cannot be built on " + tile.terrain());
            }
            if (state.world().infrastructure().at(target).isPresent()) {
                return ActionPreview.reject("Road already occupies this hex");
            }
            return affordAndAct(state, builder, InfrastructureType.ROAD, rules);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public ActionPreview validateWall(GameState state, UnitId builderId, HexEdge edge,
            GameRules rules) {
        try {
            BuilderUnit builder = requireBuilder(state, builderId);
            Objects.requireNonNull(rules, "rules");
            var phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) return phase;
            edge = state.map().requireEdge(edge.first(), edge.second());
            if (!edge.touches(builder.position())) {
                return ActionPreview.reject("Builder must stand on a Wall endpoint");
            }
            if (!state.player().visibility().isDiscovered(edge.first())
                    || !state.player().visibility().isDiscovered(edge.second())) {
                return ActionPreview.reject("Both Wall endpoint hexes must be explored");
            }
            if (!state.player().border().owns(edge.first())
                    && !state.player().border().owns(edge.second())) {
                return ActionPreview.reject("At least one Wall endpoint must be in the empire");
            }
            if (state.world().territories().ownerAt(edge.first()).isPresent()
                    || state.world().territories().ownerAt(edge.second()).isPresent()) {
                return ActionPreview.reject("Wall cannot be built inside another faction's territory");
            }
            TerrainType first = state.map().requireTile(edge.first()).terrain();
            TerrainType second = state.map().requireTile(edge.second()).terrain();
            if (first == TerrainType.SEA || second == TerrainType.SEA
                    || first == TerrainType.MOUNTAIN_RANGE
                    || second == TerrainType.MOUNTAIN_RANGE) {
                return ActionPreview.reject("Wall cannot border Sea or Mountain Range");
            }
            if (state.world().infrastructure().on(edge).isPresent()) {
                return ActionPreview.reject("Wall already occupies this edge");
            }
            return affordAndAct(state, builder, InfrastructureType.WALL, rules);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static BuilderUnit requireBuilder(GameState state, UnitId builderId) {
        Objects.requireNonNull(state, "state");
        var unit = state.player().requireUnit(Objects.requireNonNull(builderId, "builderId"));
        if (!(unit instanceof BuilderUnit builder) || !builder.isActive()) {
            throw new IllegalArgumentException("An active Builder is required");
        }
        return builder;
    }

    private static ActionPreview affordAndAct(GameState state, BuilderUnit builder,
            InfrastructureType type, GameRules rules) {
        var definition = rules.infrastructure(type);
        if (!builder.actionPoints().canSpend(definition.constructionApCost())) {
            return ActionPreview.reject("Insufficient Builder AP");
        }
        return state.player().townHall().stockpile().canAfford(definition.constructionCost())
                ? ActionPreview.allow() : ActionPreview.reject("Insufficient construction resources");
    }
}
