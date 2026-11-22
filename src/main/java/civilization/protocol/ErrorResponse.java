package civilization.protocol;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record ErrorResponse(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        UUID replyTo,
        // PHASE 3 - Added in Phase 3
        String code,
        // PHASE 3 - Added in Phase 3
        String message,
        // PHASE 3 - Added in Phase 3
        boolean retryable,
        // PHASE 3 - Added in Phase 3
        long authoritativeStateVersion,
        // PHASE 3 - Added in Phase 3
        boolean fatal) implements ResponseMessage {

    // PHASE 3 - Added in Phase 3
    public ErrorResponse(MessageMetadata metadata, UUID replyTo, String code, String message,
                         boolean retryable) {
        this(metadata, replyTo, code, message, retryable, -1L, false);
    }

    // PHASE 3 - Added in Phase 3
    public ErrorResponse {
        ProtocolValidation.metadata(metadata);
        Objects.requireNonNull(replyTo, "replyTo");
        code = ProtocolValidation.text(code, "code", 64);
        message = ProtocolValidation.text(message, "message", 2_000);
        // PHASE 3 - Added in Phase 3
        if (authoritativeStateVersion < -1L) {
            throw new IllegalArgumentException("authoritativeStateVersion must be -1 or newer");
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public MessageType type() {
        return MessageType.ERROR_RESPONSE;
    }
}
