package civilization.application.service;

import civilization.application.validation.InfrastructureConstructionValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.game.GameState;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.InfrastructureType;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import java.util.Objects;

public final class InfrastructureConstructionService {
    private final InfrastructureConstructionValidator validator;

    public InfrastructureConstructionService() {
        this(new InfrastructureConstructionValidator());
    }

    public InfrastructureConstructionService(InfrastructureConstructionValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public Road buildRoad(GameState state, UnitId builderId, HexCoord target, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validateRoad(state, builderId, target, rules));
        var definition = rules.infrastructure(InfrastructureType.ROAD);
        BuilderUnit builder = (BuilderUnit) state.player().requireUnit(builderId);
        Road road = new Road(InfrastructureId.from(state.gameplayRandom()), target,
                state.player().factionId());
        payAndConsume(state, builder, definition);
        state.world().infrastructure().add(road, state.map());
        finishBuilder(state, builder);
        return road;
    }

    public Wall buildWall(GameState state, UnitId builderId, HexEdge edge, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validateWall(state, builderId, edge, rules));
        var definition = rules.infrastructure(InfrastructureType.WALL);
        BuilderUnit builder = (BuilderUnit) state.player().requireUnit(builderId);
        Wall wall = new Wall(InfrastructureId.from(state.gameplayRandom()), edge,
                state.player().factionId(),
                new civilization.model.common.Health(definition.maximumHealth()));
        payAndConsume(state, builder, definition);
        state.world().infrastructure().add(wall, state.map());
        new RelationEventPolicy().wallBuilt(state, edge);
        finishBuilder(state, builder);
        return wall;
    }

    private static void payAndConsume(GameState state, BuilderUnit builder,
            civilization.rules.InfrastructureDefinition definition) {
        state.player().townHall().stockpile().debit(definition.constructionCost());
        builder.actionPoints().spend(definition.constructionApCost());
        if (definition.consumesBuilderCharge()) builder.consumeCharge();
    }

    private static void finishBuilder(GameState state, BuilderUnit builder) {
        if (!builder.isActive()) state.player().removeUnit(builder.id());
        state.validateSpatialReferences();
        state.incrementVersion();
    }
}
