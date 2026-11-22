package civilization.protocol;

import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record ChatMessagePush(MessageMetadata metadata, String chatMessageId, String scopeId,
                              String senderPlayerId, String senderUsername, String content,
                              Instant createdAt) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public ChatMessagePush {
        ProtocolValidation.metadata(metadata);
        chatMessageId = ProtocolValidation.id(chatMessageId, "chatMessageId");
        scopeId = ProtocolValidation.id(scopeId, "scopeId");
        senderPlayerId = ProtocolValidation.id(senderPlayerId, "senderPlayerId");
        senderUsername = ProtocolValidation.text(senderUsername, "senderUsername", 64);
        content = ProtocolValidation.text(content, "content", ProtocolValidation.MAX_CHAT_LENGTH);
        Objects.requireNonNull(createdAt, "createdAt");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.CHAT_MESSAGE_PUSH; }
}
