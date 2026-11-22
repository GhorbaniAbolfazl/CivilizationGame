package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.model.common.Health;
import civilization.model.game.GameState;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Wall;
import civilization.model.technology.TechnologyId;
import civilization.model.map.TerrainType;
import civilization.rules.GameRules;

public final class TechnologyResearchService {
    private static final int DEFENSIVE_WALL_HP = 100;

    public boolean canStart(GameState state, TechnologyId id, GameRules rules) {
        var definition = rules.technology(id);
        return state.player().townHall().level().number() >= definition.requiredHallLevel().number()
                && rules.technologyCatalog().canResearch(
                        id, state.player().technologies().researchedTechnologies());
    }

    public void complete(GameState state, TechnologyId id, GameRules rules) {
        if (!canStart(state, id, rules)) {
            throw new ActionFailure("Technology prerequisites are no longer satisfied: " + id.value());
        }
        state.player().technologies().research(id, rules.technologyCatalog());
        if (id.equals(TechnologyId.DEFENSIVE_ARCHITECTURE)) {
            applyDefensiveArchitecture(state);
        }
    }

    private static void applyDefensiveArchitecture(GameState state) {
        var hall = state.player().townHall();
        hall.applyDefensiveArchitecture();
        for (var neighbor : state.map().neighborsOf(hall.position())) {
            if (neighbor.terrain() == TerrainType.SEA
                    || neighbor.terrain() == TerrainType.MOUNTAIN_RANGE) continue;
            var edge = state.map().requireEdge(hall.position(), neighbor.coordinate());
            if (state.world().infrastructure().on(edge).isEmpty()) {
                state.world().infrastructure().add(new Wall(
                        InfrastructureId.from(state.gameplayRandom()), edge, hall.ownerId(),
                        new Health(DEFENSIVE_WALL_HP)), state.map());
            }
        }
    }
}
