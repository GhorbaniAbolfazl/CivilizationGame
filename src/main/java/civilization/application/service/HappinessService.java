package civilization.application.service;

import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.happiness.HappinessTier;
import civilization.model.unit.MilitaryUnit;
import java.util.Objects;

/** Sole calculator for accumulated and conditional empire Happiness. */
public final class HappinessService {
    public static final int MONUMENT_CONTRIBUTION = 2;
    public static final int HALL_GARRISON_CONTRIBUTION = 1;

    public HappinessEvaluation evaluate(GameState state) {
        Objects.requireNonNull(state, "state");
        int accumulated = state.player().happiness().accumulatedValue();
        int monuments = Math.multiplyExact(MONUMENT_CONTRIBUTION,
                Math.toIntExact(state.player().constructedBuildings().stream()
                        .filter(building -> building.type() == BuildingType.MONUMENT)
                        .filter(building -> building.isActive()).count()));
        int garrison = state.player().units().stream()
                .filter(MilitaryUnit.class::isInstance)
                .anyMatch(unit -> unit.position().equals(state.player().townHall().position()))
                ? HALL_GARRISON_CONTRIBUTION : 0;
        int current = Math.addExact(accumulated, Math.addExact(monuments, garrison));
        return new HappinessEvaluation(current, HappinessTier.fromValue(current),
                accumulated, monuments, garrison);
    }

    public int workerProductionPenalty(GameState state) {
        HappinessTier tier = evaluate(state).tier();
        return tier == HappinessTier.DISPLEASED || tier == HappinessTier.REVOLT ? 1 : 0;
    }

    public int workerAndMilitaryApPenalty(GameState state) {
        return evaluate(state).tier() == HappinessTier.REVOLT ? 1 : 0;
    }

    public int applyBuildingProductionModifier(GameState state, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Production cannot be negative");
        return evaluate(state).tier() == HappinessTier.GOLDEN_AGE
                ? Math.multiplyExact(amount, 110) / 100 : amount;
    }
}
