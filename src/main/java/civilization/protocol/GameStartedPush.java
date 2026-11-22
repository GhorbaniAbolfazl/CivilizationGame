package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record GameStartedPush(MessageMetadata metadata, String lobbyId, String matchId,
                              String mapId, long stateVersion) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public GameStartedPush {
        ProtocolValidation.metadata(metadata);
        lobbyId = ProtocolValidation.id(lobbyId, "lobbyId");
        matchId = ProtocolValidation.id(matchId, "matchId");
        mapId = ProtocolValidation.id(mapId, "mapId");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.GAME_STARTED_PUSH; }
}
