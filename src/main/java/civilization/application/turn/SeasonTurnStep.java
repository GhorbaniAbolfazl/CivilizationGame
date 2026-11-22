package civilization.application.turn;

/** Keeps the season calendar aligned with the already-incremented game turn. */
public final class SeasonTurnStep implements TurnStep {
    @Override public String id() { return "SEASON"; }

    @Override public void execute(TurnContext context) {
        context.state().season().advanceOneTurn();
    }
}
