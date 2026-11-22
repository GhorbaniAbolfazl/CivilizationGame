package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.MoveRequest;
import civilization.protocol.ResponseMessage;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerMovementController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public MultiplayerMovementController(ClientCommandPort commands) {
        this(commands, Clock.systemUTC());
    }

    // PHASE 3 - Added in Phase 3
    public MultiplayerMovementController(ClientCommandPort commands, Clock clock) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> move(ClientGameRenderModel currentState,
                                                    String unitId, int q, int r) {
        Objects.requireNonNull(currentState, "currentState");
        MessageMetadata metadata = new MessageMetadata(
                MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now(clock));
        return commands.send(new MoveRequest(metadata, currentState.snapshot().matchId(),
                unitId, new HexCoordinate(q, r), currentState.stateVersion()));
    }
}
