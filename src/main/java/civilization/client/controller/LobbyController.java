package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.protocol.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class LobbyController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    public LobbyController(ClientCommandPort commands) { this(commands, Clock.systemUTC()); }
    // PHASE 3 - Added in Phase 3
    public LobbyController(ClientCommandPort commands, Clock clock) { this.commands = Objects.requireNonNull(commands); this.clock = Objects.requireNonNull(clock); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> create() { return commands.send(new CreateLobbyRequest(metadata())); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> join(String id) { return commands.send(new JoinLobbyRequest(metadata(), id)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> leave(String id) { return commands.send(new LeaveLobbyRequest(metadata(), id)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> setReady(String id, boolean ready) { return commands.send(new SetReadyRequest(metadata(), id, ready)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> selectMap(String id, String map) { return commands.send(new SelectMapRequest(metadata(), id, map)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> start(String id) { return commands.send(new StartGameRequest(metadata(), id)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> chat(String id, String content) { return commands.send(new SendChatRequest(metadata(), id, content)); }
    // PHASE 3 - Added in Phase 3
    private MessageMetadata metadata() { return new MessageMetadata(MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now(clock)); }
}
