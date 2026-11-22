package civilization.application.turn;

/** One deterministic, ordered part of turn resolution. */
public interface TurnStep {
    String id();

    void execute(TurnContext context);
}
