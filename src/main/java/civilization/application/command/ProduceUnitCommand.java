package civilization.application.command;

import civilization.application.action.ActionPreview;
import civilization.application.validation.ProductionValidator;
import civilization.model.economy.ResourceBundle;
import civilization.model.game.GameState;
import civilization.model.technology.ProductionOrder;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;
import java.util.Objects;

public record ProduceUnitCommand(UnitType unitType) implements HallProductionCommand {
    public ProduceUnitCommand { Objects.requireNonNull(unitType, "unitType"); }
    @Override public ActionPreview validate(ProductionValidator validator, GameState state, GameRules rules) {
        return validator.validateUnit(state, unitType, rules);
    }
    @Override public ResourceBundle cost(GameRules rules) { return rules.unit(unitType).productionCost(); }
    @Override public ProductionOrder createOrder(GameRules rules) {
        var definition = rules.unit(unitType);
        return new UnitProductionOrder(unitType, definition.productionCost(), definition.productionTurns());
    }
}
