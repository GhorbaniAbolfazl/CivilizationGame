package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record LobbyStatePush(MessageMetadata metadata, long lobbyVersion,
                             LobbySnapshotDto snapshot) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public LobbyStatePush {
        ProtocolValidation.metadata(metadata);
        ProtocolValidation.nonNegative(lobbyVersion, "lobbyVersion");
        if (snapshot == null) throw new NullPointerException("snapshot");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.LOBBY_STATE_PUSH; }
}
