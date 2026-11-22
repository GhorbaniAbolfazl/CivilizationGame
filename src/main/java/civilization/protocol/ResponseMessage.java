package civilization.protocol;

import java.util.UUID;

// PHASE 3 - Added in Phase 3
public non-sealed interface ResponseMessage extends ProtocolMessage {

    // PHASE 3 - Added in Phase 3
    UUID replyTo();
}
