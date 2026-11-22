package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.protocol.HelloRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;
import civilization.protocol.ResumeSessionRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class ClientSessionController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commandPort;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public ClientSessionController(ClientCommandPort commandPort) {
        this(commandPort, Clock.systemUTC());
    }

    // PHASE 3 - Added in Phase 3
    public ClientSessionController(ClientCommandPort commandPort, Clock clock) {
        this.commandPort = Objects.requireNonNull(commandPort, "commandPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> requestSession(String username) {
        MessageMetadata metadata = new MessageMetadata(
                MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now(clock));
        return commandPort.send(new HelloRequest(metadata, username));
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> resumeSession(String sessionId, String resumeSecret) {
        MessageMetadata metadata = new MessageMetadata(
                MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now(clock));
        return commandPort.send(new ResumeSessionRequest(metadata, sessionId, resumeSecret));
    }
}
