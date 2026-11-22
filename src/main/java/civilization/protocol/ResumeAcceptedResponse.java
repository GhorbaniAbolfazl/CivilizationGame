package civilization.protocol;

import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record ResumeAcceptedResponse(MessageMetadata metadata, UUID replyTo, String sessionId,
                                     String playerId, int udpPort, long stateVersion)
        implements ResponseMessage {
    // PHASE 3 - Added in Phase 3
    public ResumeAcceptedResponse {
        ProtocolValidation.metadata(metadata);
        if (replyTo == null) throw new NullPointerException("replyTo");
        sessionId = ProtocolValidation.id(sessionId, "sessionId");
        playerId = ProtocolValidation.id(playerId, "playerId");
        if (udpPort < 0 || udpPort > 65_535) {
            throw new IllegalArgumentException("udpPort must be between 0 and 65535");
        }
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.RESUME_ACCEPTED_RESPONSE; }
}
