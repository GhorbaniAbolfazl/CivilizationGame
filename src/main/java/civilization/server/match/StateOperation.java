package civilization.server.match;

// PHASE 3 - Added in Phase 3
@FunctionalInterface
public interface StateOperation<S, R> {
    // PHASE 3 - Added in Phase 3
    R apply(S state);
}
