package civilization.client.state;

import civilization.client.event.UiEventDispatcher;
import civilization.protocol.ChatMessagePush;
import civilization.protocol.GameStartedPush;
import civilization.protocol.GameStatePush;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.ConnectionStatusPush;
import civilization.protocol.NotificationPush;
import civilization.protocol.BattleReportPush;
import civilization.protocol.TradeOfferPush;
import civilization.protocol.AllianceRequestPush;
import civilization.protocol.DiplomacyStatePush;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// PHASE 3 - Added in Phase 3
public final class ClientStateStore {
    // PHASE 3 - Added in Phase 3
    private final UiEventDispatcher uiDispatcher;
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<LobbyStatePush> lobby = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<GameStartedPush> gameStarted = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<GameStatePush> gameState = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<ClientGameRenderModel> gameModel = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<LobbyStatePush>> lobbyListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<ChatMessagePush>> chatListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<GameStartedPush>> startListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<GameStatePush>> gameListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<ClientGameRenderModel>> gameModelListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<ConnectionStatusPush>> connectionStatusListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<NotificationPush>> notificationListeners = new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<BattleReportPush>> battleReportListeners =
            new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, TradeOfferPush> tradeOffers = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<TradeOfferPush>> tradeOfferListeners =
            new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, DiplomacyStatePush> diplomacy = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, AllianceRequestPush> allianceRequests = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<DiplomacyStatePush>> diplomacyListeners =
            new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<Consumer<AllianceRequestPush>> allianceRequestListeners =
            new CopyOnWriteArrayList<>();

    // PHASE 3 - Added in Phase 3
    public ClientStateStore(UiEventDispatcher uiDispatcher) {
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
    }

    // PHASE 3 - Added in Phase 3
    public boolean apply(LobbyStatePush push) {
        Objects.requireNonNull(push); LobbyStatePush existing = lobby.get();
        // PHASE 3 - Added in Phase 3
        while (existing == null || push.lobbyVersion() > existing.lobbyVersion()) {
            if (lobby.compareAndSet(existing, push)) { uiDispatcher.dispatch(() -> lobbyListeners.forEach(listener -> listener.accept(push))); return true; }
            existing = lobby.get();
        }
        return false;
    }
    // PHASE 3 - Added in Phase 3
    public void apply(ChatMessagePush push) { Objects.requireNonNull(push); uiDispatcher.dispatch(() -> chatListeners.forEach(listener -> listener.accept(push))); }
    // PHASE 3 - Added in Phase 3
    public void apply(GameStartedPush push) { Objects.requireNonNull(push); gameStarted.set(push); uiDispatcher.dispatch(() -> startListeners.forEach(listener -> listener.accept(push))); }
    // PHASE 3 - Added in Phase 3
    public synchronized void apply(GameStatePush push) {
        Objects.requireNonNull(push); GameStatePush old = gameState.get();
        // PHASE 3 - Added in Phase 3
        if (old != null && old.snapshot().matchId().equals(push.snapshot().matchId())
                && old.stateVersion() >= push.stateVersion()) return;
        ClientGameRenderModel replacement = new ClientGameRenderModel(push.stateVersion(), push.snapshot());
        gameState.set(push); gameModel.set(replacement);
        // PHASE 3 - Added in Phase 3
        uiDispatcher.dispatch(() -> { gameModelListeners.forEach(listener -> listener.accept(replacement)); gameListeners.forEach(listener -> listener.accept(push)); });
    }
    // PHASE 3 - Added in Phase 3
    public void apply(ConnectionStatusPush push) { Objects.requireNonNull(push); uiDispatcher.dispatch(() -> connectionStatusListeners.forEach(listener -> listener.accept(push))); }
    // PHASE 3 - Added in Phase 3
    public void apply(NotificationPush push) { Objects.requireNonNull(push); uiDispatcher.dispatch(() -> notificationListeners.forEach(listener -> listener.accept(push))); }
    // PHASE 3 - Added in Phase 3
    public void apply(BattleReportPush push) { Objects.requireNonNull(push); uiDispatcher.dispatch(() -> battleReportListeners.forEach(listener -> listener.accept(push))); }
    // PHASE 3 - Added in Phase 3
    public void apply(TradeOfferPush push) {
        Objects.requireNonNull(push, "push");
        tradeOffers.compute(push.offerId(), (ignored, existing) ->
                existing == null || push.stateVersion() > existing.stateVersion() ? push : existing);
        TradeOfferPush currentOffer = tradeOffers.get(push.offerId());
        // PHASE 3 - Added in Phase 3
        if (currentOffer == push) {
            uiDispatcher.dispatch(() -> tradeOfferListeners.forEach(listener -> listener.accept(push)));
        }
    }
    // PHASE 3 - Added in Phase 3
    public void apply(DiplomacyStatePush push) {
        Objects.requireNonNull(push, "push");
        String key = pairKey(push.firstPlayerId(), push.secondPlayerId());
        diplomacy.compute(key, (ignored, existing) ->
                existing == null || push.stateVersion() > existing.stateVersion() ? push : existing);
        if (diplomacy.get(key) == push) {
            uiDispatcher.dispatch(() -> diplomacyListeners.forEach(listener -> listener.accept(push)));
        }
    }
    // PHASE 3 - Added in Phase 3
    public void apply(AllianceRequestPush push) {
        Objects.requireNonNull(push, "push");
        allianceRequests.compute(push.requestId(), (ignored, existing) ->
                existing == null || push.stateVersion() > existing.stateVersion() ? push : existing);
        if (allianceRequests.get(push.requestId()) == push) {
            uiDispatcher.dispatch(() -> allianceRequestListeners.forEach(listener -> listener.accept(push)));
        }
    }
    // PHASE 3 - Added in Phase 3
    public Optional<LobbyStatePush> lobby() { return Optional.ofNullable(lobby.get()); }
    // PHASE 3 - Added in Phase 3
    public Optional<GameStartedPush> gameStarted() { return Optional.ofNullable(gameStarted.get()); }
    // PHASE 3 - Added in Phase 3
    public Optional<GameStatePush> gameState() { return Optional.ofNullable(gameState.get()); }
    // PHASE 3 - Added in Phase 3
    public Optional<ClientGameRenderModel> gameModel() { return Optional.ofNullable(gameModel.get()); }
    // PHASE 3 - Added in Phase 3
    public List<TradeOfferPush> tradeOffers() {
        return tradeOffers.values().stream()
                .sorted(java.util.Comparator.comparing(TradeOfferPush::createdAt))
                .toList();
    }
    // PHASE 3 - Added in Phase 3
    public List<DiplomacyStatePush> diplomacyRelations() { return List.copyOf(diplomacy.values()); }
    // PHASE 3 - Added in Phase 3
    public List<AllianceRequestPush> allianceRequests() {
        return allianceRequests.values().stream()
                .sorted(java.util.Comparator.comparing(AllianceRequestPush::createdAt)).toList();
    }
    // PHASE 3 - Added in Phase 3
    public void addLobbyListener(Consumer<LobbyStatePush> listener) { lobbyListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addChatListener(Consumer<ChatMessagePush> listener) { chatListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addGameStartedListener(Consumer<GameStartedPush> listener) { startListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addGameStateListener(Consumer<GameStatePush> listener) { gameListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addGameModelListener(Consumer<ClientGameRenderModel> listener) { gameModelListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addConnectionStatusListener(Consumer<ConnectionStatusPush> listener) { connectionStatusListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addNotificationListener(Consumer<NotificationPush> listener) { notificationListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addBattleReportListener(Consumer<BattleReportPush> listener) { battleReportListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addTradeOfferListener(Consumer<TradeOfferPush> listener) { tradeOfferListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addDiplomacyListener(Consumer<DiplomacyStatePush> listener) { diplomacyListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    public void addAllianceRequestListener(Consumer<AllianceRequestPush> listener) { allianceRequestListeners.add(Objects.requireNonNull(listener)); }
    // PHASE 3 - Added in Phase 3
    private static String pairKey(String first, String second) {
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }
}
