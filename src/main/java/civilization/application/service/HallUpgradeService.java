package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.model.building.HallLevel;
import civilization.model.game.GameState;
import civilization.rules.GameRules;

public final class HallUpgradeService {
    public boolean canStart(GameState state, HallLevel target, GameRules rules) {
        var hall = state.player().townHall();
        return hall.level() != HallLevel.CAPITAL
                && hall.level().next() == target
                && hall.stockpile().canAfford(rules.hallUpgrade(target).cost());
    }

    public void complete(GameState state, HallLevel target, GameRules rules) {
        var definition = rules.hallUpgrade(target);
        var hall = state.player().townHall();
        if (hall.level() == HallLevel.CAPITAL || hall.level().next() != target) {
            throw new ActionFailure("Town Hall upgrade prerequisites are no longer satisfied");
        }
        hall.upgradeTo(target);
        hall.stockpile().increaseCapacity(definition.storageIncrease());
        hall.health().heal(definition.completionHeal());
    }
}
