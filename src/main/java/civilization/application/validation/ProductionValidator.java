package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.application.service.UpgradeService;
import civilization.model.game.GameState;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.building.BuildingType;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;

import java.util.Objects;

/** Authoritative Town Hall queue-start validation used by both preview and execution. */
public final class ProductionValidator {
    private final UpgradeService upgradeService;

    public ProductionValidator(UpgradeService upgradeService) {
        this.upgradeService = Objects.requireNonNull(upgradeService, "upgradeService");
    }

    public ActionPreview validateUnit(GameState state, UnitType type, GameRules rules) {
        try {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(rules, "rules");
            ActionPreview common = commonQueueStart(state);
            if (!common.allowed()) {
                return common;
            }
            if (!state.crises().isPopulationGrowthAllowed()) {
                return ActionPreview.reject("Unit production is blocked during Starvation");
            }
            if (!state.player().unitCapacity().allows(
                    Math.toIntExact(state.player().activeUnitCount()),
                    state.player().constructedBuildings())) {
                return ActionPreview.reject("Unit Cap reached");
            }
            if (type.isMilitary()) {
                if (state.player().activeMilitaryUnitCount() >= state.player().militaryUnitCapacity()) {
                    return ActionPreview.reject("Military Unit Cap reached");
                }
                HallLevel minimum = type == UnitType.SWORDSMAN
                        ? HallLevel.BASE_CAMP : HallLevel.SETTLEMENT;
                if (state.player().townHall().level().number() < minimum.number()) {
                    return ActionPreview.reject(type + " requires Town Hall level " + minimum.number());
                }
                if (type == UnitType.CAVALRY && state.player().constructedBuildings().stream()
                        .noneMatch(building -> building.isActive()
                                && building.type() == BuildingType.MILITARY_STABLE)) {
                    return ActionPreview.reject("Cavalry requires an active Stable");
                }
            }
            return state.player().townHall().stockpile().canAfford(rules.unit(type).productionCost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient resources for production");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public ActionPreview validateHallUpgrade(
            GameState state, HallLevel target, GameRules rules) {
        try {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(rules, "rules");
            ActionPreview common = commonQueueStart(state);
            if (!common.allowed()) return common;
            var hall = state.player().townHall();
            if (hall.level() == HallLevel.CAPITAL || hall.level().next() != target) {
                return ActionPreview.reject("Town Hall upgrades must advance exactly one level");
            }
            return hall.stockpile().canAfford(rules.hallUpgrade(target).cost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient resources for Town Hall upgrade");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public ActionPreview validateTechnology(
            GameState state, TechnologyId id, GameRules rules) {
        try {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rules, "rules");
            ActionPreview common = commonQueueStart(state);
            if (!common.allowed()) return common;
            var definition = rules.technology(id);
            if (state.player().townHall().level().number() < definition.requiredHallLevel().number()) {
                return ActionPreview.reject("Technology requires Town Hall level "
                        + definition.requiredHallLevel().number());
            }
            if (!rules.technologyCatalog().canResearch(
                    id, state.player().technologies().researchedTechnologies())) {
                return ActionPreview.reject("Technology is complete or prerequisites are not satisfied");
            }
            return state.player().townHall().stockpile().canAfford(definition.cost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient resources for technology research");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public ActionPreview validateCancellation(GameState state) {
        ActionPreview phase = ValidationSupport.playerActionPhase(state);
        if (!phase.allowed()) return phase;
        return state.player().townHall().productionQueue().isEmpty()
                ? ActionPreview.reject("Town Hall production slot is empty")
                : ActionPreview.allow();
    }

    public ActionPreview validateUpgrade(GameState state, UpgradeId id, GameRules rules) {
        try {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rules, "rules");
            ActionPreview common = commonQueueStart(state);
            if (!common.allowed()) {
                return common;
            }
            if (!upgradeService.canStart(state, id, rules)) {
                return ActionPreview.reject("Upgrade prerequisites are not satisfied: " + id);
            }
            return state.player().townHall().stockpile().canAfford(rules.upgrade(id).cost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient resources for production");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static ActionPreview commonQueueStart(GameState state) {
        ActionPreview phase = ValidationSupport.playerActionPhase(state);
        if (!phase.allowed()) {
            return phase;
        }
        if (!state.player().townHall().isActive()) {
            return ActionPreview.reject("Destroyed Town Hall cannot process production");
        }
        return state.player().townHall().productionQueue().isEmpty()
                ? ActionPreview.allow()
                : ActionPreview.reject("Town Hall production queue is occupied");
    }
}
