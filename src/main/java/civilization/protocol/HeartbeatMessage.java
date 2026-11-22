package civilization.protocol;

// PHASE 3 - Added in Phase 3
public sealed interface HeartbeatMessage extends ProtocolMessage permits HeartbeatPing,
        HeartbeatPong {

    // PHASE 3 - Added in Phase 3
    String sessionId();

    // PHASE 3 - Added in Phase 3
    long sequence();
}
