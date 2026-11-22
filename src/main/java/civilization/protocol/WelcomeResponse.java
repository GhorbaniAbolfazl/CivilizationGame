package civilization.protocol;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record WelcomeResponse(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        UUID replyTo,
        // PHASE 3 - Added in Phase 3
        String sessionId,
        // PHASE 3 - Added in Phase 3
        String playerId,
        // PHASE 3 - Added in Phase 3
        String resumeSecret,
        // PHASE 3 - Added in Phase 3
        int udpPort) implements ResponseMessage {

    // PHASE 3 - Added in Phase 3
    public WelcomeResponse(
            MessageMetadata metadata, UUID replyTo, String sessionId, String playerId) {
        this(metadata, replyTo, sessionId, playerId, null, 0);
    }

    // PHASE 3 - Added in Phase 3
    public WelcomeResponse {
        ProtocolValidation.metadata(metadata);
        Objects.requireNonNull(replyTo, "replyTo");
        sessionId = ProtocolValidation.id(sessionId, "sessionId");
        playerId = ProtocolValidation.id(playerId, "playerId");
        // PHASE 3 - Added in Phase 3
        resumeSecret = ProtocolValidation.optionalText(resumeSecret, "resumeSecret", 256);
        if (udpPort < 0 || udpPort > 65_535) {
            throw new IllegalArgumentException("udpPort must be between 0 and 65535");
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public MessageType type() {
        return MessageType.WELCOME_RESPONSE;
    }
}
