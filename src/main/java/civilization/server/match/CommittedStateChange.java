package civilization.server.match;

// PHASE 3 - Added in Phase 3
public record CommittedStateChange<R>(R result, MultiplayerStateUpdate update) {
    // PHASE 3 - Added in Phase 3
    public CommittedStateChange { if (update == null) throw new NullPointerException("update"); }
}
