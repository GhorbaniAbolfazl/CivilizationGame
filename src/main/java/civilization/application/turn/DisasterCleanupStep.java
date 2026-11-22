package civilization.application.turn;

/** Expires temporary effects from earlier turns before selecting a new event. */
public final class DisasterCleanupStep implements TurnStep {
    @Override public String id() { return "DISASTER_CLEANUP"; }
    @Override public void execute(TurnContext context) {
        context.state().world().disasters().advancePersistentEffectsAndPrune(
                context.state().turn().turnNumber());
    }
}
