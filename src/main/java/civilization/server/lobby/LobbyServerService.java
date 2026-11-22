package civilization.server.lobby;

import civilization.protocol.ChatMessagePush;
import civilization.protocol.GameStartedPush;
import civilization.protocol.GameStatePush;
import civilization.protocol.LobbyMemberDto;
import civilization.protocol.LobbySnapshotDto;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.TurnChangedPush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ConnectionStatus;
import civilization.protocol.ConnectionStatusPush;
import civilization.protocol.NotificationPush;
import civilization.server.map.PredefinedMapCatalog;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.server.session.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import civilization.server.match.FilteredGameStateProjector;
import civilization.server.match.GameStateBroadcaster;
import civilization.server.match.MultiplayerGameRuntime;
import civilization.server.match.MultiplayerMatchRegistry;
import civilization.server.match.MultiplayerTurnService;
import civilization.server.match.MultiplayerTurnResult;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.match.MultiplayerStateUpdate;
import civilization.protocol.EndTurnRequest;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.match.StateOperation;
import civilization.server.match.PlayerConnectionChange;
import civilization.server.chat.PublicChatService;
import civilization.server.combat.PvpCombatService;
import civilization.server.combat.ServerAttackResult;
import civilization.model.random.DiceSource;
import civilization.protocol.AcceptTradeOfferRequest;
import civilization.protocol.CancelTradeOfferRequest;
import civilization.protocol.CreateTradeOfferRequest;
import civilization.protocol.RejectTradeOfferRequest;
import civilization.server.trade.PlayerTradeResult;
import civilization.server.trade.PlayerTradeService;
import civilization.protocol.AllianceDecisionRequest;
import civilization.protocol.AllianceRequest;
import civilization.protocol.BreakAllianceRequest;
import civilization.protocol.DeclareWarRequest;
import civilization.server.diplomacy.DiplomacyProtocolMapper;
import civilization.server.diplomacy.DiplomacyResult;
import civilization.server.diplomacy.PlayerDiplomacyService;
import civilization.protocol.BuildRequest;
import civilization.protocol.MoveRequest;
import civilization.model.map.HexCoord;
import civilization.server.hall.HallTownBuildResult;
import civilization.server.hall.MultiplayerHallTownService;
import civilization.protocol.CraftItemRequest;
import civilization.protocol.UseItemRequest;
import civilization.server.item.ItemCraftResult;
import civilization.server.item.ItemUseResult;
import civilization.server.item.MultiplayerItemService;
import civilization.application.event.UnitMovedEvent;
import civilization.server.match.MultiplayerMovementService;
import civilization.server.match.MultiplayerConstructionService;

// PHASE 3 - Added in Phase 3
public final class LobbyServerService implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private static final int LOBBY_DELIVERY_CAPACITY = 1_024;
    // PHASE 3 - Added in Phase 3
    private final LobbyRegistry lobbies = new LobbyRegistry();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, LobbyId> membership = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, LobbyId> resumableLobbyMembership = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<LobbyId, LobbyView> startedLobbyViews =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final PredefinedMapCatalog maps;
    // PHASE 3 - Added in Phase 3
    private final MatchLaunchFactory launches;
    // PHASE 3 - Added in Phase 3
    private final LobbyPushGateway gateway;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerMatchRegistry matches = new MultiplayerMatchRegistry();
    // PHASE 3 - Added in Phase 3
    private final FilteredGameStateProjector gameProjector = new FilteredGameStateProjector();
    // PHASE 3 - Added in Phase 3
    private final MultiplayerTurnService turnService;
    // PHASE 3 - Added in Phase 3
    private final GameStateBroadcaster gameBroadcaster;
    // PHASE 3 - Added in Phase 3
    private final PublicChatService chatService;
    // PHASE 3 - Added in Phase 3
    private final PvpCombatService combatService;
    // PHASE 3 - Added in Phase 3
    private final PlayerTradeService playerTradeService;
    // PHASE 3 - Added in Phase 3
    private final PlayerDiplomacyService diplomacyService;
    // PHASE 3 - Added in Phase 3
    private final DiplomacyProtocolMapper diplomacyMapper = new DiplomacyProtocolMapper();
    // PHASE 3 - Added in Phase 3
    private final MultiplayerHallTownService hallTownService =
            new MultiplayerHallTownService(DefaultRulesetFactory.create());
    // PHASE 3 - Added in Phase 3
    private final MultiplayerItemService itemService;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerMovementService movementService;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerConstructionService constructionService;
    // PHASE 3 - Added in Phase 3
    private final ThreadPoolExecutor lobbyDelivery = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(LOBBY_DELIVERY_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "phase3-lobby-delivery");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    // PHASE 3 - Added in Phase 3
    public LobbyServerService(LobbyPushGateway gateway) { this(gateway, PredefinedMapCatalog.standard(), Clock.systemUTC(), null); }
    // PHASE 3 - Added in Phase 3
    public LobbyServerService(LobbyPushGateway gateway, PredefinedMapCatalog maps, Clock clock) {
        this(gateway, maps, clock, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyServerService(LobbyPushGateway gateway, PredefinedMapCatalog maps, Clock clock,
                              DiceSource combatDice) {
        this.gateway = Objects.requireNonNull(gateway); this.maps = Objects.requireNonNull(maps); this.clock = Objects.requireNonNull(clock); launches = new MatchLaunchFactory(clock);
        // PHASE 3 - Added in Phase 3
        itemService = new MultiplayerItemService(DefaultRulesetFactory.create(), clock);
        // PHASE 3 - Added in Phase 3
        movementService = new MultiplayerMovementService(DefaultRulesetFactory.create());
        // PHASE 3 - Added in Phase 3
        constructionService = new MultiplayerConstructionService(DefaultRulesetFactory.create());
        turnService = new MultiplayerTurnService(DefaultRulesetFactory.create(), itemService);
        // PHASE 3 - Added in Phase 3
        gameBroadcaster = new GameStateBroadcaster(gateway);
        // PHASE 3 - Added in Phase 3
        chatService = new PublicChatService(gateway, clock);
        // PHASE 3 - Added in Phase 3
        combatService = new PvpCombatService(combatDice, clock);
        // PHASE 3 - Added in Phase 3
        playerTradeService = new PlayerTradeService(clock);
        // PHASE 3 - Added in Phase 3
        diplomacyService = new PlayerDiplomacyService(clock);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> create(ClientSession creator) {
        if (membership.containsKey(creator.sessionId())) return CompletableFuture.failedFuture(new LobbyException("ALREADY_IN_LOBBY", "Session already belongs to a lobby"));
        LobbyId id = LobbyId.create(); LobbyRuntime runtime = new LobbyRuntime(id, Instant.now(clock), creator, maps, launches); lobbies.add(id, runtime);
        if (membership.putIfAbsent(creator.sessionId(), id) != null) { lobbies.remove(id); return CompletableFuture.failedFuture(new LobbyException("ALREADY_IN_LOBBY", "Session already belongs to a lobby")); }
        return runtime.view().thenApplyAsync(view -> publish(new LobbyChange(
                view, java.util.List.of(creator.sessionId()))), lobbyDelivery);
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> join(ClientSession session, String lobbyId) {
        LobbyId id = parse(lobbyId); LobbyRuntime runtime = requireOpenLobby(id);
        if (membership.putIfAbsent(session.sessionId(), id) != null) return CompletableFuture.failedFuture(new LobbyException("ALREADY_IN_LOBBY", "Session already belongs to a lobby"));
        return runtime.join(session).thenApplyAsync(this::publish, lobbyDelivery)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) membership.remove(session.sessionId(), id);
                });
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> leave(ClientSession session, String lobbyId) {
        LobbyId id = requireMembership(session, lobbyId);
        return requireOpenLobby(id).leave(session.playerId()).thenApplyAsync(change -> {
            membership.remove(session.sessionId(), id); publish(change);
            if (change.view().lifecycle() == LobbyLifecycle.CLOSED) lobbies.remove(id);
            return change;
        }, lobbyDelivery);
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> ready(ClientSession session, String lobbyId, boolean ready) { LobbyId id = requireMembership(session, lobbyId); return requireOpenLobby(id).ready(session.playerId(), ready).thenApplyAsync(this::publish, lobbyDelivery); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> selectMap(ClientSession session, String lobbyId, String mapId) { LobbyId id = requireMembership(session, lobbyId); return requireOpenLobby(id).selectMap(session.playerId(), mapId).thenApplyAsync(this::publish, lobbyDelivery); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyStartResult> start(ClientSession session, String lobbyId) {
        LobbyId id = requireMembership(session, lobbyId);
        return requireOpenLobby(id).start(session.playerId()).thenApplyAsync(result -> {
            MultiplayerStateUpdate initialUpdate = gameProjector.projectConnected(
                    result.launch().game());
            var initialRelations = List.copyOf(result.launch().game().diplomacy().relations());
            // PHASE 3 - Added in Phase 3
            matches.add(result.launch().game());
            publish(new LobbyChange(result.view(), result.recipients()));
            publishStart(result, initialUpdate, initialRelations);
            startedLobbyViews.put(id, result.view());
            lobbies.remove(id);
            return result;
        }, lobbyDelivery);
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ChatMessagePush> chat(ClientSession session, String scopeId, String content) {
        Objects.requireNonNull(session, "session");
        LobbyId lobbyId = membership.get(session.sessionId());
        // PHASE 3 - Added in Phase 3
        if (lobbyId != null && lobbyId.toString().equals(scopeId)) {
            return view(lobbyId.toString()).thenCompose(view -> {
                if (view.lifecycle() != LobbyLifecycle.OPEN) {
                    throw new LobbyException("LOBBY_NOT_OPEN", "Lobby chat is closed");
                }
                return chatService.publish(session, scopeId, content,
                        view.members().stream().filter(LobbyMember::connected)
                                .map(LobbyMember::sessionId).toList());
            });
        }
        // PHASE 3 - Added in Phase 3
        return chatService.publish(session, scopeId, content,
                matches.requireMemberSessions(session.sessionId(), scopeId));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> disconnect(SessionId sessionId) {
        LobbyId id = membership.remove(sessionId); if (id == null) return disconnectMatch(sessionId);
        return lobbies.find(id).map(runtime -> runtime.view().thenCompose(view -> {
            // PHASE 3 - Added in Phase 3
            if (view.lifecycle() != LobbyLifecycle.OPEN) return disconnectMatch(sessionId);
            return view.members().stream().filter(m -> m.sessionId().equals(sessionId)).findFirst()
                    .map(m -> {
                        resumableLobbyMembership.put(sessionId, id);
                        return runtime.leave(m.playerId()).thenAcceptAsync(change -> {
                            publish(change); publishLobbyConnection(change, m, ConnectionStatus.DISCONNECTED);
                            if (change.view().lifecycle() == LobbyLifecycle.CLOSED) lobbies.remove(id);
                        }, lobbyDelivery);
                    })
                    .orElseGet(() -> CompletableFuture.completedFuture(null));
        })).orElseGet(() -> disconnectMatch(sessionId));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Long> reconnect(ClientSession session) {
        var match = matches.findFor(session.sessionId());
        // PHASE 3 - Added in Phase 3
        if (match.isPresent()) {
            return match.orElseThrow().mutateAndProject(state -> {
                MultiplayerPlayerState player = state.requirePlayer(session.playerId());
                boolean changed = state.reconnect(player.playerId());
                return new PlayerConnectionChange(changed, player.playerId(), player.username(), false);
            }, gameProjector).thenCompose(committed -> {
                CompletableFuture<Void> broadcast = committed.result().changed()
                        ? gameBroadcaster.broadcastConnectionUpdate(committed.update(), committed.result(), ConnectionStatus.RECONNECTED)
                        : CompletableFuture.completedFuture(null);
                return broadcast.thenApply(ignored -> committed.update().stateVersion());
            });
        }
        LobbyId lobbyId = resumableLobbyMembership.remove(session.sessionId());
        if (lobbyId == null) return CompletableFuture.completedFuture(0L);
        LobbyRuntime runtime = lobbies.find(lobbyId).orElse(null);
        if (runtime == null || membership.putIfAbsent(session.sessionId(), lobbyId) != null) return CompletableFuture.completedFuture(0L);
        return runtime.join(session).thenApplyAsync(change -> {
            publish(change);
            LobbyMember member = change.view().members().stream().filter(value -> value.playerId().equals(session.playerId())).findFirst().orElseThrow();
            publishLobbyConnection(change, member, ConnectionStatus.RECONNECTED);
            return change.view().version();
        }, lobbyDelivery).whenComplete((ignored, failure) -> {
            // PHASE 3 - Added in Phase 3
            if (failure != null) { membership.remove(session.sessionId(), lobbyId); resumableLobbyMembership.put(session.sessionId(), lobbyId); }
        });
    }
    // PHASE 3 - Added in Phase 3
    public int purgeExpiredReconnects(SessionRegistry sessions) {
        Objects.requireNonNull(sessions, "sessions"); int before = resumableLobbyMembership.size();
        // PHASE 3 - Added in Phase 3
        resumableLobbyMembership.keySet().removeIf(sessionId -> sessions.find(sessionId)
                .map(session -> session.status() == SessionStatus.CLOSED).orElse(true));
        return before - resumableLobbyMembership.size();
    }
    // PHASE 3 - Added in Phase 3
    public int resumableLobbyCount() { return resumableLobbyMembership.size(); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyView> view(String lobbyId) {
        LobbyId id = parse(lobbyId);
        LobbyView started = startedLobbyViews.get(id);
        return started == null ? lobbies.require(id).view()
                : CompletableFuture.completedFuture(started);
    }
    // PHASE 3 - Added in Phase 3
    public MultiplayerGameRuntime match(String matchId) { return matches.require(matchId); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<MultiplayerTurnResult> endTurn(ClientSession session, EndTurnRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.mutateAndProject(state -> turnService.endTurn(state, session.playerId(), request.observedStateVersion()), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastTurnUpdate(change.update(), change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ServerAttackResult> attack(ClientSession session,
                                                        civilization.protocol.AttackRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> combatService.attack(state, session.playerId(),
                                request.attackerUnitIds(), request.targetId()), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastCombat(change.update(),
                        change.result()).thenApply(ignored -> new ServerAttackResult(
                                change.update().stateVersion(), change.result())));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<UnitMovedEvent> move(ClientSession session, MoveRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        HexCoord destination = new HexCoord(request.destination().q(), request.destination().r());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> movementService.move(state, session.playerId(),
                                request.unitId(), destination), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                        .thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<PlayerTradeResult> createTradeOffer(ClientSession session,
                                                                  CreateTradeOfferRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> playerTradeService.create(state, session.playerId(),
                                request.recipientPlayerId(), request.offered(), request.requested()),
                        gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastTrade(change.update(),
                        change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<PlayerTradeResult> acceptTradeOffer(ClientSession session,
                                                                  AcceptTradeOfferRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> playerTradeService.accept(state, session.playerId(), request.offerId()),
                        gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastTrade(change.update(),
                        change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<PlayerTradeResult> rejectTradeOffer(ClientSession session,
                                                                  RejectTradeOfferRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> playerTradeService.reject(state, session.playerId(), request.offerId()),
                        gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastTrade(change.update(),
                        change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<PlayerTradeResult> cancelTradeOffer(ClientSession session,
                                                                  CancelTradeOfferRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> playerTradeService.cancel(state, session.playerId(), request.offerId()),
                        gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastTrade(change.update(),
                        change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<DiplomacyResult> declareWar(ClientSession session,
                                                         DeclareWarRequest request) {
        return diplomacy(session, request.matchId(), request.observedStateVersion(),
                state -> diplomacyService.declareWar(state, session.playerId(),
                        request.targetPlayerId()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<DiplomacyResult> requestAlliance(ClientSession session,
                                                              AllianceRequest request) {
        return diplomacy(session, request.matchId(), request.observedStateVersion(),
                state -> diplomacyService.requestAlliance(state, session.playerId(),
                        request.targetPlayerId()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<DiplomacyResult> decideAlliance(ClientSession session,
                                                             AllianceDecisionRequest request) {
        return diplomacy(session, request.matchId(), request.observedStateVersion(),
                state -> diplomacyService.decideAlliance(state, session.playerId(),
                        request.requestId(), request.decision()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<DiplomacyResult> breakAlliance(ClientSession session,
                                                            BreakAllianceRequest request) {
        return diplomacy(session, request.matchId(), request.observedStateVersion(),
                state -> diplomacyService.breakAlliance(state, session.playerId(),
                        request.targetPlayerId()));
    }
    // PHASE 3 - Added in Phase 3
    private CompletableFuture<DiplomacyResult> diplomacy(ClientSession session, String matchId,
                                                         long observedVersion,
                                                         StateOperation<civilization.server.match.MultiplayerGameState,DiplomacyResult> action) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), matchId);
        return runtime.commitAndProject(session.playerId(), observedVersion, action, gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastDiplomacy(change.update(),
                        change.result()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<HallTownBuildResult> buildHallTown(ClientSession session,
                                                                BuildRequest request) {
        if (!request.buildingType().equals("HALL_TOWN")
                && !request.buildingType().equals("TOWN_HALL")) {
            return CompletableFuture.failedFuture(new civilization.server.hall.HallTownException(
                    "UNSUPPORTED_BUILD_REQUEST", "Only Hall Town construction is available through this multiplayer handler"));
        }
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        HexCoord target = new HexCoord(request.location().q(), request.location().r());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> hallTownService.build(state, session.playerId(),
                                request.builderId(), target), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                        .thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<?> build(ClientSession session, BuildRequest request) {
        if (request.buildingType().equals("HALL_TOWN")
                || request.buildingType().equals("TOWN_HALL")) {
            return buildHallTown(session, request);
        }
        if (!request.buildingType().equals("APOTHECARY")) {
            MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
            HexCoord target = new HexCoord(request.location().q(), request.location().r());
            // PHASE 3 - Added in Phase 3
            return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                            state -> constructionService.build(state, session.playerId(),
                                    request.builderId(), request.buildingType(), target), gameProjector)
                    .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                            .thenApply(ignored -> change.result()));
        }
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        HexCoord target = new HexCoord(request.location().q(), request.location().r());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> itemService.buildApothecary(state, session.playerId(),
                                request.builderId(), target), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                        .thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ItemCraftResult> craftItem(ClientSession session,
                                                        CraftItemRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> itemService.craft(state, session.playerId(), request.apothecaryId(),
                                request.itemType(), request.quantity()), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                        .thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ItemUseResult> useItem(ClientSession session,
                                                    UseItemRequest request) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), request.matchId());
        HexCoord destination = request.targetLocation() == null ? null
                : new HexCoord(request.targetLocation().q(), request.targetLocation().r());
        return runtime.commitAndProject(session.playerId(), request.observedStateVersion(),
                        state -> itemService.use(state, session.playerId(), request.itemId(),
                                request.targetId(), destination), gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update())
                        .thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    public <R> CompletableFuture<R> changeGameState(ClientSession session, String matchId,
                                                     long observedVersion, StateOperation<civilization.server.match.MultiplayerGameState,R> action) {
        MultiplayerGameRuntime runtime = matches.requireFor(session.sessionId(), matchId);
        return runtime.commitAndProject(session.playerId(), observedVersion, action, gameProjector)
                .thenCompose(change -> gameBroadcaster.broadcastState(change.update()).thenApply(ignored -> change.result()));
    }
    // PHASE 3 - Added in Phase 3
    private CompletableFuture<Void> disconnectMatch(SessionId sessionId) {
        return matches.findFor(sessionId).map(runtime -> runtime.mutateAndProject(state -> {
            MultiplayerPlayerState player = state.players().values().stream().filter(value -> value.sessionId().equals(sessionId)).findFirst().orElse(null);
            if (player == null) return null;
            boolean ownTurn = state.activePlayerId().equals(player.playerId()) && state.connectedPlayers().contains(player.playerId());
            boolean changed;
            // PHASE 3 - Added in Phase 3
            if (ownTurn) { turnService.endTurnForDisconnect(state, player.playerId()); changed = true; }
            else changed = state.disconnect(player.playerId());
            return new PlayerConnectionChange(changed, player.playerId(), player.username(), ownTurn);
        }, gameProjector).thenCompose(committed -> committed.result() != null && committed.result().changed()
                ? gameBroadcaster.broadcastConnectionUpdate(committed.update(), committed.result(), ConnectionStatus.DISCONNECTED)
                : CompletableFuture.completedFuture(null)))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }
    // PHASE 3 - Added in Phase 3
    private void publishLobbyConnection(LobbyChange change, LobbyMember member, ConnectionStatus status) {
        String verb = status == ConnectionStatus.RECONNECTED ? " reconnected" : " disconnected";
        // PHASE 3 - Added in Phase 3
        change.recipients().forEach(recipient -> {
            safePush(recipient, new ConnectionStatusPush(MessageMetadata.createNow(), member.playerId().toString(), status, change.view().version()));
            safePush(recipient, new NotificationPush(MessageMetadata.createNow(), "PLAYER_" + status.name(), member.username() + verb, change.view().version()));
        });
    }
    // PHASE 3 - Added in Phase 3
    private LobbyChange publish(LobbyChange change) { LobbyStatePush push = new LobbyStatePush(MessageMetadata.createNow(), change.view().version(), snapshot(change.view())); change.recipients().forEach(id -> safePush(id, push)); return change; }
    // PHASE 3 - Added in Phase 3
    private void publishStart(LobbyStartResult result, MultiplayerStateUpdate initialUpdate,
                              List<civilization.server.diplomacy.DiplomaticPairView> relations) {
        MatchLaunch launch = result.launch();
        // PHASE 3 - Added in Phase 3
        for (LobbyMember member : launch.players()) {
            safePush(member.sessionId(), new GameStartedPush(MessageMetadata.createNow(), launch.lobbyId().toString(), launch.matchId(), launch.map().id(), 1));
            // PHASE 3 - Added in Phase 3
            safePush(member.sessionId(), new GameStatePush(MessageMetadata.createNow(),
                    initialUpdate.stateVersion(), initialUpdate.clientSnapshots().get(
                    member.sessionId())));
            // PHASE 3 - Added in Phase 3
            safePush(member.sessionId(), new TurnChangedPush(MessageMetadata.createNow(),
                    launch.matchId(), initialUpdate.stateVersion(), initialUpdate.turnNumber(),
                    initialUpdate.activePlayerId().toString(), initialUpdate.phase(), null));
            // PHASE 3 - Added in Phase 3
            relations.forEach(pair -> safePush(member.sessionId(), diplomacyMapper.relation(
                    launch.matchId(), initialUpdate.stateVersion(), pair)));
        }
    }
    // PHASE 3 - Added in Phase 3
    private LobbySnapshotDto snapshot(LobbyView view) { return new LobbySnapshotDto(view.lobbyId().toString(), view.hostPlayerId() == null ? null : view.hostPlayerId().toString(), view.selectedMapId(), view.lifecycle().name(), view.createdAt(), view.members().stream().map(m -> new LobbyMemberDto(m.playerId().toString(), m.username(), m.createdAt(), m.ready(), m.connected())).toList(), maps.summaries()); }
    // PHASE 3 - Added in Phase 3
    private LobbyId requireMembership(ClientSession session, String requested) { LobbyId actual = membership.get(session.sessionId()); LobbyId parsed = parse(requested); if (actual == null || !actual.equals(parsed)) throw new LobbyException("NOT_IN_LOBBY", "Session does not belong to this lobby"); return actual; }
    // PHASE 3 - Added in Phase 3
    private LobbyRuntime requireOpenLobby(LobbyId id) {
        if (startedLobbyViews.containsKey(id)) {
            throw new LobbyException("LOBBY_NOT_OPEN", "Lobby is not open");
        }
        return lobbies.require(id);
    }
    // PHASE 3 - Added in Phase 3
    private static LobbyId parse(String value) { try { return LobbyId.parse(value); } catch (RuntimeException failure) { throw new LobbyException("INVALID_LOBBY_ID", "Invalid lobby ID"); } }
    // PHASE 3 - Added in Phase 3
    private void safePush(SessionId recipient, civilization.protocol.PushMessage message) { try { gateway.push(recipient, message); } catch (RuntimeException ignored) { /* delivery failure is handled by connection lifecycle */ } }
    // PHASE 3 - Added in Phase 3
    @Override public void close() {
        // PHASE 3 - Added in Phase 3
        lobbyDelivery.shutdown();
        try {
            if (!lobbyDelivery.awaitTermination(2, TimeUnit.SECONDS)) {
                lobbyDelivery.shutdownNow();
                lobbyDelivery.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            lobbyDelivery.shutdownNow();
            Thread.currentThread().interrupt();
        }
        chatService.close(); lobbies.close(); matches.close(); membership.clear();
        resumableLobbyMembership.clear(); startedLobbyViews.clear(); gameBroadcaster.close();
    }
}
