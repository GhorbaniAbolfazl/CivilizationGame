package civilization.application.command;

import civilization.application.action.ActionPreview;
import civilization.application.validation.ProductionValidator;
import civilization.model.economy.ResourceBundle;
import civilization.model.game.GameState;
import civilization.model.technology.ProductionOrder;
import civilization.rules.GameRules;

/** Command object for one exclusive Town Hall production-slot request. */
public sealed interface HallProductionCommand
        permits ProduceUnitCommand, UpgradeHallCommand, ResearchTechnologyCommand {
    ActionPreview validate(ProductionValidator validator, GameState state, GameRules rules);
    ResourceBundle cost(GameRules rules);
    ProductionOrder createOrder(GameRules rules);
}
