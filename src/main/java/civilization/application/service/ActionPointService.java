package civilization.application.service;

import civilization.model.player.PlayerState;
import civilization.model.unit.Unit;
import civilization.model.unit.WorkerUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.model.game.GameState;

import java.util.Objects;

public final class ActionPointService {
    public void refillLivingUnits(PlayerState player) {
        player.units().stream().filter(Unit::isActive).forEach(unit -> unit.actionPoints().refill());
    }

    public void refillLivingUnits(GameState state) {
        Objects.requireNonNull(state, "state");
        int penalty = new HappinessService().workerAndMilitaryApPenalty(state);
        state.player().units().stream().filter(Unit::isActive).forEach(unit -> {
            boolean affected = unit instanceof WorkerUnit || unit instanceof MilitaryUnit;
            int effectiveCapacity = Math.max(0,
                    unit.actionPoints().capacity() - (affected ? penalty : 0));
            unit.actionPoints().refillTo(effectiveCapacity);
        });
    }

    public void applyPenalty(PlayerState player, int amount) {
        player.units().stream().filter(Unit::isActive)
                .forEach(unit -> unit.actionPoints().applyPenalty(amount));
    }
}
