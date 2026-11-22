package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.model.game.GameState;
import civilization.model.technology.UpgradeId;
import civilization.rules.GameRules;
import civilization.rules.UpgradeDefinition;

public final class UpgradeService {
    public boolean canStart(GameState state, UpgradeId id, GameRules rules) {
        if (state.player().technologies().isCompleted(id)) {
            return false;
        }
        UpgradeDefinition definition = rules.upgrade(id);
        return definition.prerequisites().stream()
                .allMatch(state.player().technologies()::isCompleted);
    }

    public void requireCanStart(GameState state, UpgradeId id, GameRules rules) {
        if (!canStart(state, id, rules)) {
            throw new ActionFailure("Upgrade prerequisites are not satisfied: " + id);
        }
    }

    public void complete(GameState state, UpgradeId id, GameRules rules) {
        requireCanStart(state, id, rules);
        state.player().technologies().complete(id);
        if (id == UpgradeId.STORAGE_1 || id == UpgradeId.STORAGE_2) {
            state.player().townHall().stockpile().increaseCapacity(rules.storageUpgradeIncrement());
        }
    }
}
