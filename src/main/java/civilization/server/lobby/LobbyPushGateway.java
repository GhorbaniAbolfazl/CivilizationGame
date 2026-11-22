package civilization.server.lobby;

import civilization.protocol.PushMessage;
import civilization.server.session.SessionId;

// PHASE 3 - Added in Phase 3
@FunctionalInterface
public interface LobbyPushGateway {
    // PHASE 3 - Added in Phase 3
    void push(SessionId recipient, PushMessage message);
}
