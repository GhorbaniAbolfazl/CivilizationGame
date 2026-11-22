package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record ResumeSessionRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String sessionId,
        // PHASE 3 - Added in Phase 3
        String resumeSecret) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public ResumeSessionRequest {
        ProtocolValidation.metadata(metadata);
        sessionId = ProtocolValidation.id(sessionId, "sessionId");
        resumeSecret = ProtocolValidation.text(resumeSecret, "resumeSecret", 256);
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.RESUME_SESSION_REQUEST; }
}
