package civilization.protocol.json;

import civilization.protocol.*;

import java.lang.reflect.RecordComponent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public final class MessageCodecRegistry {
    // PHASE 3 - Added in Phase 3
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "protocolVersion", "type", "messageId", "sentAt");
    // PHASE 3 - Added in Phase 3
    private final Map<MessageType, Class<? extends ProtocolMessage>> classesByType;
    // PHASE 3 - Added in Phase 3
    private final Map<Class<? extends ProtocolMessage>, MessageType> typesByClass;
    // PHASE 3 - Added in Phase 3
    private final Map<MessageType, Set<String>> fieldsByType;

    // PHASE 3 - Added in Phase 3
    public MessageCodecRegistry(Map<MessageType, Class<? extends ProtocolMessage>> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        EnumMap<MessageType, Class<? extends ProtocolMessage>> byType =
                new EnumMap<>(MessageType.class);
        Map<Class<? extends ProtocolMessage>, MessageType> byClass = new HashMap<>();
        EnumMap<MessageType, Set<String>> byFields = new EnumMap<>(MessageType.class);
        // PHASE 3 - Added in Phase 3
        for (var entry : registrations.entrySet()) {
            MessageType type = Objects.requireNonNull(entry.getKey(), "message type");
            Class<? extends ProtocolMessage> messageClass =
                    Objects.requireNonNull(entry.getValue(), "message class");
            if (!messageClass.isRecord()) {
                throw new IllegalArgumentException("Protocol DTO must be a record: " + messageClass);
            }
            if (byType.put(type, messageClass) != null
                    || byClass.put(messageClass, type) != null) {
                throw new IllegalArgumentException("Duplicate message registration: " + type);
            }
            byFields.put(type, discoverFields(messageClass));
        }
        if (!byType.keySet().equals(EnumSet.allOf(MessageType.class))) {
            EnumSet<MessageType> missing = EnumSet.allOf(MessageType.class);
            missing.removeAll(byType.keySet());
            throw new IllegalArgumentException("Missing message registrations: " + missing);
        }
        classesByType = Map.copyOf(byType);
        typesByClass = Map.copyOf(byClass);
        fieldsByType = Map.copyOf(byFields);
    }

    // PHASE 3 - Added in Phase 3
    public static MessageCodecRegistry standard() {
        return new MessageCodecRegistry(Map.ofEntries(
                Map.entry(MessageType.HELLO_REQUEST, HelloRequest.class),
                Map.entry(MessageType.RESUME_SESSION_REQUEST, ResumeSessionRequest.class),
                Map.entry(MessageType.DISCONNECT_REQUEST, DisconnectRequest.class),
                Map.entry(MessageType.CREATE_LOBBY_REQUEST, CreateLobbyRequest.class),
                Map.entry(MessageType.JOIN_LOBBY_REQUEST, JoinLobbyRequest.class),
                Map.entry(MessageType.LEAVE_LOBBY_REQUEST, LeaveLobbyRequest.class),
                Map.entry(MessageType.SET_READY_REQUEST, SetReadyRequest.class),
                Map.entry(MessageType.SELECT_MAP_REQUEST, SelectMapRequest.class),
                Map.entry(MessageType.START_GAME_REQUEST, StartGameRequest.class),
                Map.entry(MessageType.SEND_CHAT_REQUEST, SendChatRequest.class),
                Map.entry(MessageType.MOVE_REQUEST, MoveRequest.class),
                Map.entry(MessageType.BUILD_REQUEST, BuildRequest.class),
                Map.entry(MessageType.CRAFT_ITEM_REQUEST, CraftItemRequest.class),
                Map.entry(MessageType.USE_ITEM_REQUEST, UseItemRequest.class),
                Map.entry(MessageType.ATTACK_REQUEST, AttackRequest.class),
                Map.entry(MessageType.CREATE_TRADE_OFFER_REQUEST, CreateTradeOfferRequest.class),
                Map.entry(MessageType.ACCEPT_TRADE_OFFER_REQUEST, AcceptTradeOfferRequest.class),
                Map.entry(MessageType.REJECT_TRADE_OFFER_REQUEST, RejectTradeOfferRequest.class),
                Map.entry(MessageType.CANCEL_TRADE_OFFER_REQUEST, CancelTradeOfferRequest.class),
                Map.entry(MessageType.DECLARE_WAR_REQUEST, DeclareWarRequest.class),
                Map.entry(MessageType.ALLIANCE_REQUEST, AllianceRequest.class),
                Map.entry(MessageType.ALLIANCE_DECISION_REQUEST, AllianceDecisionRequest.class),
                Map.entry(MessageType.BREAK_ALLIANCE_REQUEST, BreakAllianceRequest.class),
                Map.entry(MessageType.END_TURN_REQUEST, EndTurnRequest.class),
                Map.entry(MessageType.WELCOME_RESPONSE, WelcomeResponse.class),
                Map.entry(MessageType.RESUME_ACCEPTED_RESPONSE, ResumeAcceptedResponse.class),
                Map.entry(MessageType.REQUEST_ACCEPTED_RESPONSE, RequestAcceptedResponse.class),
                Map.entry(MessageType.ERROR_RESPONSE, ErrorResponse.class),
                Map.entry(MessageType.LOBBY_STATE_PUSH, LobbyStatePush.class),
                Map.entry(MessageType.GAME_STARTED_PUSH, GameStartedPush.class),
                Map.entry(MessageType.GAME_STATE_PUSH, GameStatePush.class),
                Map.entry(MessageType.CHAT_MESSAGE_PUSH, ChatMessagePush.class),
                Map.entry(MessageType.NOTIFICATION_PUSH, NotificationPush.class),
                Map.entry(MessageType.BATTLE_REPORT_PUSH, BattleReportPush.class),
                Map.entry(MessageType.CONNECTION_STATUS_PUSH, ConnectionStatusPush.class),
                Map.entry(MessageType.TURN_CHANGED_PUSH, TurnChangedPush.class),
                Map.entry(MessageType.DIPLOMACY_STATE_PUSH, DiplomacyStatePush.class),
                Map.entry(MessageType.ALLIANCE_REQUEST_PUSH, AllianceRequestPush.class),
                Map.entry(MessageType.TRADE_OFFER_PUSH, TradeOfferPush.class),
                Map.entry(MessageType.SERVER_SHUTDOWN_PUSH, ServerShutdownPush.class),
                Map.entry(MessageType.HEARTBEAT_PING, HeartbeatPing.class),
                Map.entry(MessageType.HEARTBEAT_PONG, HeartbeatPong.class)));
    }

    // PHASE 3 - Added in Phase 3
    public Class<? extends ProtocolMessage> messageClass(MessageType type) {
        Class<? extends ProtocolMessage> result = classesByType.get(
                Objects.requireNonNull(type, "type"));
        if (result == null) throw new IllegalArgumentException("Unregistered message type: " + type);
        return result;
    }

    // PHASE 3 - Added in Phase 3
    public MessageType messageType(Class<? extends ProtocolMessage> messageClass) {
        MessageType result = typesByClass.get(Objects.requireNonNull(messageClass, "messageClass"));
        if (result == null) {
            throw new IllegalArgumentException("Unregistered message class: " + messageClass);
        }
        return result;
    }

    // PHASE 3 - Added in Phase 3
    public Set<String> allowedFields(MessageType type) {
        return fieldsByType.get(Objects.requireNonNull(type, "type"));
    }

    // PHASE 3 - Added in Phase 3
    public Set<MessageType> messageTypes() {
        return classesByType.keySet();
    }

    // PHASE 3 - Added in Phase 3
    private static Set<String> discoverFields(Class<? extends ProtocolMessage> messageClass) {
        Set<String> fields = new LinkedHashSet<>(ENVELOPE_FIELDS);
        // PHASE 3 - Added in Phase 3
        for (RecordComponent component : messageClass.getRecordComponents()) {
            if (!component.getName().equals("metadata")) fields.add(component.getName());
        }
        return Set.copyOf(fields);
    }
}
