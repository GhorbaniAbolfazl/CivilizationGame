package civilization.application.command;

import civilization.application.action.ActionPreview;
import civilization.application.validation.ProductionValidator;
import civilization.model.economy.ResourceBundle;
import civilization.model.game.GameState;
import civilization.model.technology.ProductionOrder;
import civilization.model.technology.TechnologyId;
import civilization.model.technology.TechnologyResearchOrder;
import civilization.rules.GameRules;
import java.util.Objects;

public record ResearchTechnologyCommand(TechnologyId technologyId) implements HallProductionCommand {
    public ResearchTechnologyCommand { Objects.requireNonNull(technologyId, "technologyId"); }
    @Override public ActionPreview validate(ProductionValidator validator, GameState state, GameRules rules) {
        return validator.validateTechnology(state, technologyId, rules);
    }
    @Override public ResourceBundle cost(GameRules rules) { return rules.technology(technologyId).cost(); }
    @Override public ProductionOrder createOrder(GameRules rules) {
        var definition = rules.technology(technologyId);
        return new TechnologyResearchOrder(technologyId, definition.cost(), definition.productionTurns());
    }
}
