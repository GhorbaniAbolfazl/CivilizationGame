package civilization.application.turn;

import civilization.application.service.ProductionResult;
import civilization.application.service.UpkeepResult;
import civilization.model.economy.TurnEconomyReport;
import civilization.model.game.GameState;
import civilization.model.unit.Unit;
import civilization.rules.GameRules;

import java.util.Objects;
import java.util.Optional;

/** Mutable data shared only by the ordered steps of one turn resolution. */
public final class TurnContext {
    private final GameState state;
    private final GameRules rules;
    private ProductionResult production;
    private UpkeepResult upkeep;
    private Unit completedUnit;

    public TurnContext(GameState state, GameRules rules) {
        this.state = Objects.requireNonNull(state, "state");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public GameState state() {
        return state;
    }

    public GameRules rules() {
        return rules;
    }

    public void recordProduction(ProductionResult production) {
        if (this.production != null) {
            throw new IllegalStateException("Production step already executed");
        }
        this.production = Objects.requireNonNull(production, "production");
    }

    public ProductionResult production() {
        return Objects.requireNonNull(production, "Production step has not executed");
    }

    public void recordUpkeep(UpkeepResult upkeep) {
        if (this.upkeep != null) {
            throw new IllegalStateException("Upkeep step already executed");
        }
        this.upkeep = Objects.requireNonNull(upkeep, "upkeep");
    }

    public UpkeepResult upkeep() {
        return Objects.requireNonNull(upkeep, "Upkeep step has not executed");
    }

    public void recordCompletedUnit(Unit completedUnit) {
        if (this.completedUnit != null) {
            throw new IllegalStateException("A completed unit was already recorded");
        }
        this.completedUnit = completedUnit;
    }

    public Optional<Unit> completedUnit() {
        return Optional.ofNullable(completedUnit);
    }

    public TurnEconomyReport economyReport() {
        ProductionResult produced = production();
        UpkeepResult charged = upkeep();
        return new TurnEconomyReport(
                produced.produced(),
                charged.consumed(),
                produced.accepted(),
                produced.overflow(),
                charged.shortage());
    }
}
