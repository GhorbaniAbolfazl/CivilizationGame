package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record GameStatePush(MessageMetadata metadata, long stateVersion,
                            GameSnapshotDto snapshot) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public GameStatePush {
        ProtocolValidation.metadata(metadata);
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        if (snapshot == null) throw new NullPointerException("snapshot");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.GAME_STATE_PUSH; }
}
