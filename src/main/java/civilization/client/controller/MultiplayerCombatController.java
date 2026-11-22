package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.AttackRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class MultiplayerCombatController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;

    // PHASE 3 - Added in Phase 3
    public MultiplayerCombatController(ClientCommandPort commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> attack(ClientGameRenderModel state,
                                                     List<String> attackerUnitIds,
                                                     String targetId) {
        Objects.requireNonNull(state, "state");
        return commands.send(new AttackRequest(MessageMetadata.createNow(),
                state.snapshot().matchId(), attackerUnitIds, targetId, state.stateVersion()));
    }
}
