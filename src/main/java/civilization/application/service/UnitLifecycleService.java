package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.model.game.GameState;
import civilization.model.unit.UnitId;

/** Applies unit HP damage and performs immediate authoritative removal at zero. */
public final class UnitLifecycleService {
    public int damage(GameState state, UnitId unitId, int amount) {
        var unit = state.player().requireUnit(unitId);
        var health = unit.healthState().orElseThrow(
                () -> new ActionFailure("Unit does not have authoritative health"));
        int applied = health.damage(amount);
        if (health.isDepleted()) state.player().removeUnit(unitId);
        state.incrementVersion();
        return applied;
    }
}
