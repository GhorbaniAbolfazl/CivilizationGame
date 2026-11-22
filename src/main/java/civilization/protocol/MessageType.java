package civilization.protocol;

// PHASE 3 - Added in Phase 3
public enum MessageType {
    // PHASE 3 - Added in Phase 3
    HELLO_REQUEST,
    // PHASE 3 - Added in Phase 3
    RESUME_SESSION_REQUEST,
    // PHASE 3 - Added in Phase 3
    DISCONNECT_REQUEST,
    // PHASE 3 - Added in Phase 3
    CREATE_LOBBY_REQUEST,
    // PHASE 3 - Added in Phase 3
    JOIN_LOBBY_REQUEST,
    // PHASE 3 - Added in Phase 3
    LEAVE_LOBBY_REQUEST,
    // PHASE 3 - Added in Phase 3
    SET_READY_REQUEST,
    // PHASE 3 - Added in Phase 3
    SELECT_MAP_REQUEST,
    // PHASE 3 - Added in Phase 3
    START_GAME_REQUEST,
    // PHASE 3 - Added in Phase 3
    SEND_CHAT_REQUEST,
    // PHASE 3 - Added in Phase 3
    MOVE_REQUEST,
    // PHASE 3 - Added in Phase 3
    BUILD_REQUEST,
    // PHASE 3 - Added in Phase 3
    CRAFT_ITEM_REQUEST,
    // PHASE 3 - Added in Phase 3
    USE_ITEM_REQUEST,
    // PHASE 3 - Added in Phase 3
    ATTACK_REQUEST,
    // PHASE 3 - Added in Phase 3
    CREATE_TRADE_OFFER_REQUEST,
    // PHASE 3 - Added in Phase 3
    ACCEPT_TRADE_OFFER_REQUEST,
    // PHASE 3 - Added in Phase 3
    REJECT_TRADE_OFFER_REQUEST,
    // PHASE 3 - Added in Phase 3
    CANCEL_TRADE_OFFER_REQUEST,
    // PHASE 3 - Added in Phase 3
    DECLARE_WAR_REQUEST,
    // PHASE 3 - Added in Phase 3
    ALLIANCE_REQUEST,
    // PHASE 3 - Added in Phase 3
    ALLIANCE_DECISION_REQUEST,
    // PHASE 3 - Added in Phase 3
    BREAK_ALLIANCE_REQUEST,
    // PHASE 3 - Added in Phase 3
    END_TURN_REQUEST,
    // PHASE 3 - Added in Phase 3
    WELCOME_RESPONSE,
    // PHASE 3 - Added in Phase 3
    RESUME_ACCEPTED_RESPONSE,
    // PHASE 3 - Added in Phase 3
    REQUEST_ACCEPTED_RESPONSE,
    // PHASE 3 - Added in Phase 3
    ERROR_RESPONSE,
    // PHASE 3 - Added in Phase 3
    LOBBY_STATE_PUSH,
    // PHASE 3 - Added in Phase 3
    GAME_STARTED_PUSH,
    // PHASE 3 - Added in Phase 3
    GAME_STATE_PUSH,
    // PHASE 3 - Added in Phase 3
    CHAT_MESSAGE_PUSH,
    // PHASE 3 - Added in Phase 3
    NOTIFICATION_PUSH,
    // PHASE 3 - Added in Phase 3
    BATTLE_REPORT_PUSH,
    // PHASE 3 - Added in Phase 3
    CONNECTION_STATUS_PUSH,
    // PHASE 3 - Added in Phase 3
    TURN_CHANGED_PUSH,
    // PHASE 3 - Added in Phase 3
    DIPLOMACY_STATE_PUSH,
    // PHASE 3 - Added in Phase 3
    ALLIANCE_REQUEST_PUSH,
    // PHASE 3 - Added in Phase 3
    TRADE_OFFER_PUSH,
    // PHASE 3 - Added in Phase 3
    SERVER_SHUTDOWN_PUSH,
    // PHASE 3 - Added in Phase 3
    HEARTBEAT_PING,
    // PHASE 3 - Added in Phase 3
    HEARTBEAT_PONG
}
