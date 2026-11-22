package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.technology.UpgradeId;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;

import java.util.Objects;

/** Authoritative construction validation used by both preview and execution. */
public final class ConstructionValidator {
    private final civilization.rules.tribe.TribeDockDiscountPolicy dockDiscount =
            new civilization.rules.tribe.TribeDockDiscountPolicy();
    public ActionPreview validate(
            GameState state, UnitId builderId, BuildingType type, HexCoord target, GameRules rules) {
        try {
            Objects.requireNonNull(builderId, "builderId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(rules, "rules");
            ActionPreview phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) {
                return phase;
            }
            if (type == BuildingType.TOWN_HALL) {
                return ActionPreview.reject("Town Hall cannot be constructed");
            }
            var unit = state.player().requireUnit(builderId);
            if (!(unit instanceof BuilderUnit builder) || !builder.isActive()) {
                return ActionPreview.reject("An active Builder is required");
            }
            if (!builder.position().equals(target)) {
                return ActionPreview.reject("Builder must stand on the target hex");
            }
            var tile = state.map().requireTile(target);
            if (tile.terrain() == TerrainType.SEA || tile.terrain() == TerrainType.MOUNTAIN_RANGE) {
                return ActionPreview.reject("Buildings cannot be constructed on " + tile.terrain());
            }
            if (!state.player().visibility().isDiscovered(target)) {
                return ActionPreview.reject("Construction target must be explored");
            }
            if (!state.player().border().owns(target)) {
                return ActionPreview.reject("Construction target is outside the empire");
            }
            if (state.world().territories().ownerAt(target).isPresent()) {
                return ActionPreview.reject("Cannot build in uncaptured tribe or enemy territory");
            }
            if (state.world().tradingPosts().all().stream()
                    .anyMatch(post -> post.position().equals(target))) {
                return ActionPreview.reject("A neutral Trading Post occupies the hex");
            }
            var existing = state.player().buildingAt(target).orElse(null);
            if (existing != null && existing.isActive()) {
                return ActionPreview.reject("An active building already occupies the hex");
            }
            if (existing != null && existing.type() != type) {
                return ActionPreview.reject(
                        "A broken building must be rebuilt as the same building type");
            }
            if (type == BuildingType.SETTLEMENT && !state.crises().isPopulationGrowthAllowed()) {
                return ActionPreview.reject("Settlement construction is blocked during Starvation");
            }
            if (type == BuildingType.MILITARY_STABLE
                    && state.player().townHall().level().number() < 2) {
                return ActionPreview.reject("Military Stable requires Town Hall level 2");
            }
            if (type == BuildingType.MILITARY_STABLE
                    && tile.terrain() != TerrainType.PLAINS
                    && !tile.hasNaturalResource(civilization.model.map.NaturalResourceType.HORSE)) {
                return ActionPreview.reject("Military Stable requires Plains or a Horse resource");
            }
            if (type == BuildingType.BAZAAR
                    && state.player().townHall().level().number() < 2) {
                return ActionPreview.reject("Bazaar requires Town Hall level 2");
            }
            // PHASE 3 - Added in Phase 3
            if (type == BuildingType.APOTHECARY
                    && state.player().activeHallTowns().stream()
                    .noneMatch(hall -> hall.level().number() >= 2)) {
                return ActionPreview.reject("Apothecary requires an active Hall Town at level 2");
            }
            if (type == BuildingType.DOCK) {
                if (state.player().townHall().level().number() < 2) {
                    return ActionPreview.reject("Dock requires Town Hall level 2");
                }
                if (new civilization.application.service.DockFishSourceResolver()
                        .availableSources(state, target, true).isEmpty()) {
                    return ActionPreview.reject("Dock requires adjacent discovered, non-depleted Fish");
                }
            }
            UpgradeId technology = requiredTechnology(type);
            if (technology != null && !state.player().technologies().isCompleted(technology)) {
                return ActionPreview.reject("Required technology is not completed: " + technology);
            }
            if (type.requiredTerrain().isPresent()
                    && type.requiredTerrain().orElseThrow() != tile.terrain()) {
                return ActionPreview.reject(type + " requires "
                        + type.requiredTerrain().orElseThrow());
            }
            if (type != BuildingType.DOCK && !type.eligibleNaturalResources().isEmpty()
                    && type.eligibleNaturalResources().stream()
                    .noneMatch(tile::hasAvailableNaturalResource)) {
                return ActionPreview.reject(type + " requires a matching non-depleted resource");
            }
            if (type == BuildingType.SETTLEMENT && !tile.isResourceFree()) {
                return ActionPreview.reject("Settlement requires a resource-free hex");
            }
            var definition = rules.building(type);
            if (!builder.actionPoints().canSpend(definition.constructionApCost())) {
                return ActionPreview.reject("Insufficient Builder AP");
            }
            var effectiveCost = dockDiscount.effectiveCost(state, type,
                    definition.constructionCost());
            return state.player().townHall().stockpile().canAfford(effectiveCost)
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient construction resources");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static UpgradeId requiredTechnology(BuildingType type) {
        return switch (type) {
            case STONE_MINE -> UpgradeId.STONE_MINING;
            case IRON_MINE -> UpgradeId.IRON_MINING;
            case SETTLEMENT -> UpgradeId.SETTLEMENT_CONSTRUCTION;
            default -> null;
        };
    }
}
