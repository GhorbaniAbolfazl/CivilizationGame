package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.BuildRequest;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerHallTownController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;

    // PHASE 3 - Added in Phase 3
    public MultiplayerHallTownController(ClientCommandPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> establish(ClientGameRenderModel state,
                                                         String builderId, int q, int r) {
        Objects.requireNonNull(state, "state");
        return commands.send(new BuildRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), builderId, "HALL_TOWN",
                new HexCoordinate(q, r), state.stateVersion()));
    }
}
