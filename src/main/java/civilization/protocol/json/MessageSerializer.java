package civilization.protocol.json;

import civilization.protocol.ProtocolMessage;

// PHASE 3 - Added in Phase 3
public interface MessageSerializer {

    // PHASE 3 - Added in Phase 3
    String serialize(ProtocolMessage message);

    // PHASE 3 - Added in Phase 3
    ProtocolMessage deserialize(String json);
}
