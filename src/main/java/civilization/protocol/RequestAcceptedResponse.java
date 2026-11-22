package civilization.protocol;

import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record RequestAcceptedResponse(MessageMetadata metadata, UUID replyTo,
                                      long committedStateVersion) implements ResponseMessage {
    // PHASE 3 - Added in Phase 3
    public RequestAcceptedResponse {
        ProtocolValidation.metadata(metadata);
        if (replyTo == null) throw new NullPointerException("replyTo");
        ProtocolValidation.nonNegative(committedStateVersion, "committedStateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.REQUEST_ACCEPTED_RESPONSE; }
}
