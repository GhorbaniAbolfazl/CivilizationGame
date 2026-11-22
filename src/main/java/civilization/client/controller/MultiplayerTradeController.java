package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.AcceptTradeOfferRequest;
import civilization.protocol.CancelTradeOfferRequest;
import civilization.protocol.CreateTradeOfferRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.RejectTradeOfferRequest;
import civilization.protocol.ResourceBundleDto;
import civilization.protocol.ResponseMessage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerTradeController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;

    // PHASE 3 - Added in Phase 3
    public MultiplayerTradeController(ClientCommandPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> create(ClientGameRenderModel state,
                                                     String recipientPlayerId,
                                                     ResourceBundleDto offered,
                                                     ResourceBundleDto requested) {
        Objects.requireNonNull(state, "state");
        return commands.send(new CreateTradeOfferRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), recipientPlayerId, offered, requested,
                state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> accept(ClientGameRenderModel state, String offerId) {
        return commands.send(new AcceptTradeOfferRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), offerId, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> reject(ClientGameRenderModel state, String offerId) {
        return commands.send(new RejectTradeOfferRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), offerId, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> cancel(ClientGameRenderModel state, String offerId) {
        return commands.send(new CancelTradeOfferRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), offerId, state.stateVersion()));
    }
}
