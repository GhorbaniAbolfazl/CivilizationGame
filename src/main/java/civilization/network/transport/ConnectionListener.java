package civilization.network.transport;

import civilization.protocol.ProtocolMessage;

// PHASE 3 - Added in Phase 3
public interface ConnectionListener {
    // PHASE 3 - Added in Phase 3
    void onMessage(ProtocolMessage message);

    // PHASE 3 - Added in Phase 3
    void onDisconnected(Throwable cause);
}
