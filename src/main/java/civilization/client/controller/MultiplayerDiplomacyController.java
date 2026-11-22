package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.AllianceDecision;
import civilization.protocol.AllianceDecisionRequest;
import civilization.protocol.AllianceRequest;
import civilization.protocol.BreakAllianceRequest;
import civilization.protocol.DeclareWarRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerDiplomacyController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;

    // PHASE 3 - Added in Phase 3
    public MultiplayerDiplomacyController(ClientCommandPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> declareWar(ClientGameRenderModel state,
                                                          String targetPlayerId) {
        return commands.send(new DeclareWarRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), targetPlayerId, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> requestAlliance(ClientGameRenderModel state,
                                                               String targetPlayerId) {
        return commands.send(new AllianceRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), targetPlayerId, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> decideAlliance(ClientGameRenderModel state,
                                                              String requestId,
                                                              AllianceDecision decision) {
        return commands.send(new AllianceDecisionRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), requestId, decision, state.stateVersion()));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> breakAlliance(ClientGameRenderModel state,
                                                             String targetPlayerId) {
        return commands.send(new BreakAllianceRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), targetPlayerId, state.stateVersion()));
    }
}
