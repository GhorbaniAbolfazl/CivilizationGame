package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record DiplomacyStatePush(MessageMetadata metadata, String matchId, long stateVersion,
                                 String firstPlayerId, String secondPlayerId, String relation)
        implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public DiplomacyStatePush {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        firstPlayerId = ProtocolValidation.id(firstPlayerId, "firstPlayerId");
        secondPlayerId = ProtocolValidation.id(secondPlayerId, "secondPlayerId");
        relation = ProtocolValidation.text(relation, "relation", 32);
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.DIPLOMACY_STATE_PUSH; }
}
