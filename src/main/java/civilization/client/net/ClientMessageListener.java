package civilization.client.net;

import civilization.protocol.PushMessage;

// PHASE 3 - Added in Phase 3
public interface ClientMessageListener {
    // PHASE 3 - Added in Phase 3
    void onPush(PushMessage message);

    // PHASE 3 - Added in Phase 3
    void onConnectionStateChanged(NetworkConnectionState state, Throwable cause);
}
