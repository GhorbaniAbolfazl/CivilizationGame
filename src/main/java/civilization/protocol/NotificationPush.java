package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record NotificationPush(MessageMetadata metadata, String code, String message,
                               long stateVersion) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public NotificationPush {
        ProtocolValidation.metadata(metadata);
        code = ProtocolValidation.text(code, "code", 64);
        message = ProtocolValidation.text(message, "message", 2_000);
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.NOTIFICATION_PUSH; }
}
