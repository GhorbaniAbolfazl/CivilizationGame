package civilization.application.service;

import civilization.application.factory.BuildingFactory;
import civilization.application.validation.ConstructionValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.building.Building;
import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;

import java.util.Objects;

public final class ConstructionService {
    private final VisibilityService visibilityService;
    private final ConstructionValidator validator;
    private final BuildingFactory buildingFactory;
    private final civilization.rules.tribe.TribeDockDiscountPolicy dockDiscount =
            new civilization.rules.tribe.TribeDockDiscountPolicy();

    public ConstructionService(VisibilityService visibilityService) {
        this(visibilityService, new ConstructionValidator(), new BuildingFactory());
    }

    public ConstructionService(
            VisibilityService visibilityService,
            ConstructionValidator validator,
            BuildingFactory buildingFactory) {
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.buildingFactory = Objects.requireNonNull(buildingFactory, "buildingFactory");
    }

    public Building construct(
            GameState state, UnitId builderId, BuildingType type, HexCoord target, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validate(state, builderId, type, target, rules));
        BuilderUnit builder = (BuilderUnit) state.player().requireUnit(builderId);
        Building existing = state.player().buildingAt(target).orElse(null);
        var definition = rules.building(type);
        Building building = buildingFactory.create(
                civilization.model.building.BuildingId.from(state.gameplayRandom()),
                type, target, rules, state.player().factionId());
        var effectiveCost = dockDiscount.effectiveCost(state, type,
                definition.constructionCost());
        state.player().townHall().stockpile().debit(effectiveCost);
        builder.actionPoints().spend(definition.constructionApCost());
        if (existing == null) {
            state.player().addBuilding(building);
        } else {
            state.player().releaseWorkersFromBuilding(existing.id());
            state.player().replaceBuilding(building);
        }
        if (type == BuildingType.SETTLEMENT) {
            state.player().happiness().applyOnce("settlement:" + building.id().value(), -1);
        }
        new RelationEventPolicy().resourceConstruction(state, target);
        builder.consumeCharge();
        dockDiscount.consumeIfApplied(state, type);
        if (!builder.isActive()) {
            state.player().removeUnit(builder.id());
        }
        visibilityService.refresh(state, rules);
        state.validateSpatialReferences();
        state.incrementVersion();
        return building;
    }

}
