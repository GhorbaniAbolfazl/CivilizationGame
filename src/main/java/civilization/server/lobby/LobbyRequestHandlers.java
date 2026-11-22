package civilization.server.lobby;

import civilization.protocol.ErrorResponse;
import civilization.protocol.JoinLobbyRequest;
import civilization.protocol.LeaveLobbyRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.MessageType;
import civilization.protocol.RequestAcceptedResponse;
import civilization.protocol.RequestMessage;
import civilization.protocol.ResponseMessage;
import civilization.protocol.SelectMapRequest;
import civilization.protocol.SendChatRequest;
import civilization.protocol.SetReadyRequest;
import civilization.protocol.StartGameRequest;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.MoveRequest;
import civilization.protocol.AttackRequest;
import civilization.protocol.AcceptTradeOfferRequest;
import civilization.protocol.CancelTradeOfferRequest;
import civilization.protocol.CreateTradeOfferRequest;
import civilization.protocol.RejectTradeOfferRequest;
import civilization.server.match.MatchException;
import civilization.server.chat.ChatException;
import civilization.server.trade.PlayerTradeException;
import civilization.protocol.AllianceDecisionRequest;
import civilization.protocol.AllianceRequest;
import civilization.protocol.BreakAllianceRequest;
import civilization.protocol.DeclareWarRequest;
import civilization.server.diplomacy.DiplomacyException;
import civilization.protocol.BuildRequest;
import civilization.server.hall.HallTownException;
import civilization.protocol.CraftItemRequest;
import civilization.protocol.UseItemRequest;
import civilization.server.item.ItemException;
import civilization.server.dispatch.RequestDispatcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

// PHASE 3 - Added in Phase 3
public final class LobbyRequestHandlers {
    // PHASE 3 - Added in Phase 3
    private final LobbyServerService service;
    // PHASE 3 - Added in Phase 3
    public LobbyRequestHandlers(LobbyServerService service) { this.service = service; }

    // PHASE 3 - Added in Phase 3
    public void registerWith(RequestDispatcher dispatcher) {
        dispatcher.register(MessageType.CREATE_LOBBY_REQUEST, (session, request) -> accepted(request, () -> service.create(session).thenApply(change -> change.view().version())));
        dispatcher.register(MessageType.JOIN_LOBBY_REQUEST, (session, request) -> { JoinLobbyRequest value = (JoinLobbyRequest) request; return accepted(request, () -> service.join(session, value.lobbyId()).thenApply(change -> change.view().version())); });
        dispatcher.register(MessageType.LEAVE_LOBBY_REQUEST, (session, request) -> { LeaveLobbyRequest value = (LeaveLobbyRequest) request; return accepted(request, () -> service.leave(session, value.lobbyId()).thenApply(change -> change.view().version())); });
        dispatcher.register(MessageType.SET_READY_REQUEST, (session, request) -> { SetReadyRequest value = (SetReadyRequest) request; return accepted(request, () -> service.ready(session, value.lobbyId(), value.ready()).thenApply(change -> change.view().version())); });
        dispatcher.register(MessageType.SELECT_MAP_REQUEST, (session, request) -> { SelectMapRequest value = (SelectMapRequest) request; return accepted(request, () -> service.selectMap(session, value.lobbyId(), value.mapId()).thenApply(change -> change.view().version())); });
        dispatcher.register(MessageType.START_GAME_REQUEST, (session, request) -> { StartGameRequest value = (StartGameRequest) request; return accepted(request, () -> service.start(session, value.lobbyId()).thenApply(result -> result.view().version())); });
        dispatcher.register(MessageType.SEND_CHAT_REQUEST, (session, request) -> { SendChatRequest value = (SendChatRequest) request; return accepted(request, () -> service.chat(session, value.scopeId(), value.content()).thenApply(ignored -> 0L)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.MOVE_REQUEST, (session, request) -> { MoveRequest value = (MoveRequest) request; return accepted(request, () -> service.move(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.ATTACK_REQUEST, (session, request) -> { AttackRequest value = (AttackRequest) request; return accepted(request, () -> service.attack(session, value).thenApply(result -> result.stateVersion())); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.BUILD_REQUEST, (session, request) -> { BuildRequest value = (BuildRequest) request; return accepted(request, () -> service.build(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.CRAFT_ITEM_REQUEST, (session, request) -> { CraftItemRequest value = (CraftItemRequest) request; return accepted(request, () -> service.craftItem(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.USE_ITEM_REQUEST, (session, request) -> { UseItemRequest value = (UseItemRequest) request; return accepted(request, () -> service.useItem(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.CREATE_TRADE_OFFER_REQUEST, (session, request) -> { CreateTradeOfferRequest value = (CreateTradeOfferRequest) request; return accepted(request, () -> service.createTradeOffer(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.ACCEPT_TRADE_OFFER_REQUEST, (session, request) -> { AcceptTradeOfferRequest value = (AcceptTradeOfferRequest) request; return accepted(request, () -> service.acceptTradeOffer(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.REJECT_TRADE_OFFER_REQUEST, (session, request) -> { RejectTradeOfferRequest value = (RejectTradeOfferRequest) request; return accepted(request, () -> service.rejectTradeOffer(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.CANCEL_TRADE_OFFER_REQUEST, (session, request) -> { CancelTradeOfferRequest value = (CancelTradeOfferRequest) request; return accepted(request, () -> service.cancelTradeOffer(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.DECLARE_WAR_REQUEST, (session, request) -> { DeclareWarRequest value = (DeclareWarRequest) request; return accepted(request, () -> service.declareWar(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.ALLIANCE_REQUEST, (session, request) -> { AllianceRequest value = (AllianceRequest) request; return accepted(request, () -> service.requestAlliance(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.ALLIANCE_DECISION_REQUEST, (session, request) -> { AllianceDecisionRequest value = (AllianceDecisionRequest) request; return accepted(request, () -> service.decideAlliance(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.BREAK_ALLIANCE_REQUEST, (session, request) -> { BreakAllianceRequest value = (BreakAllianceRequest) request; return accepted(request, () -> service.breakAlliance(session, value).thenApply(result -> value.observedStateVersion() + 1)); });
        // PHASE 3 - Added in Phase 3
        dispatcher.register(MessageType.END_TURN_REQUEST, (session, request) -> { EndTurnRequest value = (EndTurnRequest) request; return accepted(request, () -> service.endTurn(session, value).thenApply(result -> result.stateVersion())); });
    }

    // PHASE 3 - Added in Phase 3
    private CompletableFuture<? extends ResponseMessage> accepted(RequestMessage request, Supplier<CompletableFuture<Long>> operation) {
        try {
            return operation.get().<ResponseMessage>handle((version, failure) -> {
                if (failure == null) return new RequestAcceptedResponse(MessageMetadata.createNow(), request.metadata().messageId(), version);
                Throwable cause = unwrap(failure); String code = code(cause);
                return new ErrorResponse(MessageMetadata.createNow(), request.metadata().messageId(), code, cause.getMessage() == null ? code : cause.getMessage(), false);
            });
        } catch (RuntimeException failure) {
            String code = code(failure);
            return CompletableFuture.completedFuture(new ErrorResponse(MessageMetadata.createNow(), request.metadata().messageId(), code, failure.getMessage() == null ? code : failure.getMessage(), false));
        }
    }

    // PHASE 3 - Added in Phase 3
    private static Throwable unwrap(Throwable failure) { return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure; }
    // PHASE 3 - Added in Phase 3
    private static String code(Throwable failure) { if (failure instanceof LobbyException lobby) return lobby.code(); if (failure instanceof MatchException match) return match.code(); if (failure instanceof ChatException chat) return chat.code(); if (failure instanceof PlayerTradeException trade) return trade.code(); if (failure instanceof DiplomacyException diplomacy) return diplomacy.code(); if (failure instanceof HallTownException hall) return hall.code(); if (failure instanceof ItemException item) return item.code(); return "REQUEST_REJECTED"; }
}
