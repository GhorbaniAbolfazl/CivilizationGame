package civilization.client.controller;

import civilization.client.ClientCommandPort;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResponseMessage;
import civilization.protocol.SendChatRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public final class ChatController {
    // PHASE 3 - Added in Phase 3
    private final ClientCommandPort commands;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public ChatController(ClientCommandPort commands) {
        this(commands, Clock.systemUTC());
    }

    // PHASE 3 - Added in Phase 3
    public ChatController(ClientCommandPort commands, Clock clock) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> send(String scopeId, String content) {
        return commands.send(new SendChatRequest(new MessageMetadata(
                MessageMetadata.CURRENT_PROTOCOL_VERSION, UUID.randomUUID(),
                Instant.now(clock)), scopeId, content));
    }
}
