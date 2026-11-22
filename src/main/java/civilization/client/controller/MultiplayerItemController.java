package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.BuildRequest;
import civilization.protocol.CraftItemRequest;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;
import civilization.protocol.UseItemRequest;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerItemController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;

    // PHASE 3 - Added in Phase 3
    public MultiplayerItemController(ClientCommandPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> buildApothecary(ClientGameRenderModel state,
                                                               String builderId, int q, int r) {
        return commands.send(new BuildRequest(MessageMetadata.createNow(), state.snapshot().matchId(),
                builderId, "APOTHECARY", new HexCoordinate(q, r), state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> craft(ClientGameRenderModel state,
                                                     String apothecaryId, String itemType) {
        return commands.send(new CraftItemRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), apothecaryId, itemType, 1, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> use(ClientGameRenderModel state, String itemId,
                                                   String unitId, int q, int r) {
        return commands.send(new UseItemRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), itemId, unitId, new HexCoordinate(q, r),
                state.stateVersion()));
    }
}
