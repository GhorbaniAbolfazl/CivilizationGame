package civilization.server.match;

import civilization.protocol.GameStatePush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.TurnChangedPush;
import civilization.protocol.ConnectionStatus;
import civilization.protocol.ConnectionStatusPush;
import civilization.protocol.NotificationPush;
import civilization.server.lobby.LobbyPushGateway;
import civilization.server.session.SessionId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import civilization.server.combat.BattleReportMapper;
import civilization.server.combat.PvpCombatResult;
import civilization.server.trade.PlayerTradeProtocolMapper;
import civilization.server.trade.PlayerTradeResult;
import civilization.server.trade.PlayerTradeStatus;
import civilization.server.diplomacy.DiplomacyActionKind;
import civilization.server.diplomacy.DiplomacyProtocolMapper;
import civilization.server.diplomacy.DiplomacyResult;

// PHASE 3 - Added in Phase 3
public final class GameStateBroadcaster implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final LobbyPushGateway gateway;
    // PHASE 3 - Added in Phase 3
    private final ExecutorService deliveryExecutor;
    // PHASE 3 - Added in Phase 3
    private final BattleReportMapper battleReports = new BattleReportMapper();
    // PHASE 3 - Added in Phase 3
    private final PlayerTradeProtocolMapper trades = new PlayerTradeProtocolMapper();
    // PHASE 3 - Added in Phase 3
    private final DiplomacyProtocolMapper diplomacy = new DiplomacyProtocolMapper();

    // PHASE 3 - Added in Phase 3
    public GameStateBroadcaster(LobbyPushGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        // PHASE 3 - Added in Phase 3
        deliveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "authoritative-state-broadcast");
            thread.setDaemon(true);
            return thread;
        });
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastState(MultiplayerStateUpdate update) {
        Objects.requireNonNull(update, "update");
        return CompletableFuture.runAsync(() -> sendPersonalizedState(update), deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastTurnUpdate(MultiplayerStateUpdate update,
                                                        MultiplayerTurnResult result) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(result, "result");
        return CompletableFuture.runAsync(() -> {
            sendPersonalizedState(update);
            // PHASE 3 - Added in Phase 3
            update.clientSnapshots().keySet().forEach(recipient -> safePush(recipient,
                    new TurnChangedPush(MessageMetadata.createNow(), update.matchId(),
                            update.stateVersion(), update.turnNumber(),
                            update.activePlayerId().toString(), update.phase(), null)));
            // PHASE 3 - Added in Phase 3
            result.battleReports().forEach(report -> safePush(report.defenderSessionId(),
                    battleReports.toPush(update.stateVersion(), report)));
        }, deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastCombat(MultiplayerStateUpdate update,
                                                   PvpCombatResult result) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(result, "result");
        return CompletableFuture.runAsync(() -> {
            sendPersonalizedState(update);
            safePush(result.report().attackerSessionId(),
                    battleReports.toPush(update.stateVersion(), result.report()));
            // PHASE 3 - Added in Phase 3
            update.clientSnapshots().values().stream().findFirst()
                    .filter(snapshot -> snapshot.matchStatus().equals("FINISHED"))
                    .ifPresent(snapshot -> update.clientSnapshots().keySet().forEach(recipient ->
                            safePush(recipient, new NotificationPush(MessageMetadata.createNow(),
                                    "MATCH_WON", "The final opposing Hall Town was destroyed. Winner: "
                                    + snapshot.viewerAttributes().get("winnerPlayerId"),
                                    update.stateVersion()))));
        }, deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastTrade(MultiplayerStateUpdate update,
                                                   PlayerTradeResult result) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(result, "result");
        return CompletableFuture.runAsync(() -> {
            sendPersonalizedState(update);
            var push = trades.toPush(update.matchId(), update.stateVersion(), result.offer());
            safePush(result.senderSessionId(), push);
            safePush(result.recipientSessionId(), push);
            // PHASE 3 - Added in Phase 3
            if (result.offer().status() == PlayerTradeStatus.PENDING) {
                safePush(result.recipientSessionId(), new NotificationPush(
                        MessageMetadata.createNow(), "TRADE_OFFER_RECEIVED",
                        "A new player trade offer arrived", update.stateVersion()));
            }
        }, deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastDiplomacy(MultiplayerStateUpdate update,
                                                       DiplomacyResult result) {
        Objects.requireNonNull(update, "update");
        Objects.requireNonNull(result, "result");
        return CompletableFuture.runAsync(() -> {
            sendPersonalizedState(update);
            // PHASE 3 - Added in Phase 3
            if (result.kind() != DiplomacyActionKind.ALLIANCE_REQUESTED
                    && result.kind() != DiplomacyActionKind.ALLIANCE_REJECTED) {
                var relation = diplomacy.relation(update.matchId(), update.stateVersion(), result);
                update.clientSnapshots().keySet().forEach(recipient -> safePush(recipient, relation));
            }
            // PHASE 3 - Added in Phase 3
            if (result.allianceRequest() != null) {
                var request = diplomacy.allianceRequest(update.matchId(), update.stateVersion(),
                        result.allianceRequest());
                safePush(result.firstSessionId(), request);
                safePush(result.secondSessionId(), request);
            }
            publishDiplomacyNotification(update, result);
        }, deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    private void publishDiplomacyNotification(MultiplayerStateUpdate update,
                                               DiplomacyResult result) {
        String message = switch (result.kind()) {
            case WAR_DECLARED -> result.firstUsername() + " declared war on " + result.secondUsername();
            case ALLIANCE_REQUESTED -> result.firstUsername() + " requested an alliance";
            case ALLIANCE_ACCEPTED -> result.firstUsername() + " and " + result.secondUsername() + " formed an alliance";
            case ALLIANCE_REJECTED -> result.secondUsername() + " rejected the alliance request";
            case ALLIANCE_BROKEN -> result.firstUsername() + " broke the alliance with " + result.secondUsername();
        };
        NotificationPush notification = new NotificationPush(MessageMetadata.createNow(),
                result.kind().name(), message, update.stateVersion());
        // PHASE 3 - Added in Phase 3
        if (result.kind() == DiplomacyActionKind.ALLIANCE_REQUESTED
                || result.kind() == DiplomacyActionKind.ALLIANCE_REJECTED) {
            safePush(result.firstSessionId(), notification);
            safePush(result.secondSessionId(), notification);
        } else {
            update.clientSnapshots().keySet().forEach(recipient -> safePush(recipient, notification));
        }
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> broadcastConnectionUpdate(MultiplayerStateUpdate update,
                                                              PlayerConnectionChange change,
                                                              ConnectionStatus status) {
        Objects.requireNonNull(update, "update"); Objects.requireNonNull(change, "change"); Objects.requireNonNull(status, "status");
        return CompletableFuture.runAsync(() -> {
            sendPersonalizedState(update);
            String verb = status == ConnectionStatus.RECONNECTED ? " reconnected" : " disconnected";
            // PHASE 3 - Added in Phase 3
            update.clientSnapshots().keySet().forEach(recipient -> {
                safePush(recipient, new ConnectionStatusPush(MessageMetadata.createNow(),
                        change.playerId().toString(), status, update.stateVersion()));
                safePush(recipient, new NotificationPush(MessageMetadata.createNow(),
                        "PLAYER_" + status.name(), change.username() + verb, update.stateVersion()));
                safePush(recipient, new TurnChangedPush(MessageMetadata.createNow(), update.matchId(),
                        update.stateVersion(), update.turnNumber(), update.activePlayerId().toString(),
                        update.phase(), null));
            });
        }, deliveryExecutor);
    }

    // PHASE 3 - Added in Phase 3
    private void sendPersonalizedState(MultiplayerStateUpdate update) {
        // PHASE 3 - Added in Phase 3
        update.clientSnapshots().forEach((recipient, snapshot) -> safePush(recipient,
                new GameStatePush(MessageMetadata.createNow(), update.stateVersion(), snapshot)));
    }

    // PHASE 3 - Added in Phase 3
    private void safePush(SessionId recipient, civilization.protocol.PushMessage message) {
        try {
            gateway.push(recipient, message);
        } catch (RuntimeException ignored) {
            // PHASE 3 - Added in Phase 3
            // Connection lifecycle detection owns delivery failures; one failed client must not stop the broadcast.
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        deliveryExecutor.shutdown();
        // PHASE 3 - Added in Phase 3
        try {
            if (!deliveryExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                deliveryExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            deliveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
