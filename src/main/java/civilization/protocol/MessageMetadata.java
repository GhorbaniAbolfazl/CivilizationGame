package civilization.protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record MessageMetadata(
        // PHASE 3 - Added in Phase 3
        int protocolVersion,
        // PHASE 3 - Added in Phase 3
        UUID messageId,
        // PHASE 3 - Added in Phase 3
        Instant sentAt) {

    // PHASE 3 - Added in Phase 3
    public static final int CURRENT_PROTOCOL_VERSION = 1;

    // PHASE 3 - Added in Phase 3
    public MessageMetadata {
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("Protocol version must be positive");
        }
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    // PHASE 3 - Added in Phase 3
    public static MessageMetadata createNow() {
        return new MessageMetadata(CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), Instant.now());
    }
}
