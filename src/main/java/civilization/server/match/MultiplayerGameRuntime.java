package civilization.server.match;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import civilization.server.session.PlayerId;

// PHASE 3 - Added in Phase 3
public final class MultiplayerGameRuntime implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final SerializedServerState<MultiplayerGameState> state;
    // PHASE 3 - Added in Phase 3
    public MultiplayerGameRuntime(MultiplayerGameState state) { this.state = new SerializedServerState<>(Objects.requireNonNull(state), "multiplayer-match-" + state.matchId()); }
    // PHASE 3 - Added in Phase 3
    public <R> CompletableFuture<R> project(Function<MultiplayerGameState,R> projection) { Objects.requireNonNull(projection); return state.submit(projection::apply); }
    // PHASE 3 - Added in Phase 3
    public <R> CompletableFuture<CommittedStateChange<R>> commitAndProject(
            PlayerId playerId, long observedVersion, StateOperation<MultiplayerGameState,R> operation,
            FilteredGameStateProjector projector) {
        Objects.requireNonNull(playerId); Objects.requireNonNull(operation); Objects.requireNonNull(projector);
        return state.submit(game -> {
            game.requireActive(playerId, observedVersion); R result = operation.apply(game); game.commitAction(playerId);
            return new CommittedStateChange<>(result, projector.projectConnected(game));
        });
    }
    // PHASE 3 - Added in Phase 3
    public <R> CompletableFuture<CommittedStateChange<R>> mutateAndProject(
            StateOperation<MultiplayerGameState,R> operation, FilteredGameStateProjector projector) {
        Objects.requireNonNull(operation); Objects.requireNonNull(projector);
        return state.submit(game -> new CommittedStateChange<>(operation.apply(game), projector.projectConnected(game)));
    }
    // PHASE 3 - Added in Phase 3
    @Override public void close() { state.close(); }
}
