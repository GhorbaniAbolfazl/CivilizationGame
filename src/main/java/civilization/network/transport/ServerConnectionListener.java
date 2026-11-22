package civilization.network.transport;

import civilization.protocol.ProtocolMessage;

// PHASE 3 - Added in Phase 3
public interface ServerConnectionListener {
    // PHASE 3 - Added in Phase 3
    void onConnected(TcpMessageConnection connection);

    // PHASE 3 - Added in Phase 3
    void onMessage(TcpMessageConnection connection, ProtocolMessage message);

    // PHASE 3 - Added in Phase 3
    void onDisconnected(TcpMessageConnection connection, Throwable cause);
}
