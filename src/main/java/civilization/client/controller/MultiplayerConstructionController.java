package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.model.building.BuildingType;
import civilization.protocol.BuildRequest;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerConstructionController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public MultiplayerConstructionController(ClientCommandPort commands) {
        this(commands, Clock.systemUTC());
    }

    // PHASE 3 - Added in Phase 3
    public MultiplayerConstructionController(ClientCommandPort commands, Clock clock) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> build(ClientGameRenderModel state, String builderId,
                                                     BuildingType type, int q, int r) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        MessageMetadata metadata = new MessageMetadata(
                MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now(clock));
        return commands.send(new BuildRequest(metadata, state.snapshot().matchId(), builderId,
                type.name(), new HexCoordinate(q, r), state.stateVersion()));
    }
}
