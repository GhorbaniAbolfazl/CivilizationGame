package civilization.network.transport;

import civilization.protocol.HeartbeatMessage;

import java.net.SocketAddress;

// PHASE 3 - Added in Phase 3
public interface HeartbeatListener {
    // PHASE 3 - Added in Phase 3
    void onHeartbeat(HeartbeatMessage message, SocketAddress remoteAddress);

    // PHASE 3 - Added in Phase 3
    void onTransportFailure(Throwable cause);
}
