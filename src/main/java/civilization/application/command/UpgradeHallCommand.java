package civilization.application.command;

import civilization.application.action.ActionPreview;
import civilization.application.validation.ProductionValidator;
import civilization.model.building.HallLevel;
import civilization.model.economy.ResourceBundle;
import civilization.model.game.GameState;
import civilization.model.technology.HallUpgradeOrder;
import civilization.model.technology.ProductionOrder;
import civilization.rules.GameRules;
import java.util.Objects;

public record UpgradeHallCommand(HallLevel targetLevel) implements HallProductionCommand {
    public UpgradeHallCommand { Objects.requireNonNull(targetLevel, "targetLevel"); }
    @Override public ActionPreview validate(ProductionValidator validator, GameState state, GameRules rules) {
        return validator.validateHallUpgrade(state, targetLevel, rules);
    }
    @Override public ResourceBundle cost(GameRules rules) { return rules.hallUpgrade(targetLevel).cost(); }
    @Override public ProductionOrder createOrder(GameRules rules) {
        var definition = rules.hallUpgrade(targetLevel);
        return new HallUpgradeOrder(targetLevel, definition.cost(), definition.productionTurns());
    }
}
