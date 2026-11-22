package civilization.phase3;

import civilization.application.factory.UnitFactory;
import civilization.client.net.NetworkManager;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceStockpile;
import civilization.model.identity.FactionId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.unit.UnitType;
import civilization.protocol.ChatMessagePush;
import civilization.protocol.CreateLobbyRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.GameStartedPush;
import civilization.protocol.HelloRequest;
import civilization.protocol.JoinLobbyRequest;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ProtocolMessage;
import civilization.protocol.RequestAcceptedResponse;
import civilization.protocol.SendChatRequest;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.lobby.LobbyMember;
import civilization.server.lobby.LobbyServerNetworkAdapter;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.FilteredGameStateProjector;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.match.MultiplayerTurnService;
import civilization.server.match.SerializedServerState;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionRegistry;
import civilization.network.transport.TcpServer;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

// PHASE 3 - Added in Phase 3
public final class Phase3ConcurrencyPerformanceAuditTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3ConcurrencyPerformanceAuditTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        serializedStateAppliesBackpressureAndCompletesFuturesOnClose();
        lobbyDeliveryDoesNotOccupyTheLobbyStateLane();
        startupSnapshotsAreCapturedBeforeTheMatchBecomesActionable();
        sixLocalClientsExchangeConcurrentChatAndCleanUp();
        largeMapProjectionAndHighTurnsRemainBounded();
        System.out.println("Phase3ConcurrencyPerformanceAuditTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void serializedStateAppliesBackpressureAndCompletesFuturesOnClose()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SerializedServerState<AtomicInteger> state = new SerializedServerState<>(
                new AtomicInteger(), "phase3-audit-state", 2);
        CompletableFuture<Integer> running = state.submit(value -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Audit command interrupted", interrupted);
            }
            return value.incrementAndGet();
        });
        check(entered.await(2, TimeUnit.SECONDS), "Serialized command did not start");
        CompletableFuture<Integer> queuedOne = state.submit(AtomicInteger::incrementAndGet);
        CompletableFuture<Integer> queuedTwo = state.submit(AtomicInteger::incrementAndGet);
        CompletableFuture<Integer> rejected = state.submit(AtomicInteger::incrementAndGet);
        check(rejected.isCompletedExceptionally(), "Full command queue did not reject work");
        check(state.pendingOperationCount() == 3, "Pending command accounting is incorrect");
        state.close();
        release.countDown();
        await(running::isDone, "running authoritative future cleanup");
        check(queuedOne.isCompletedExceptionally() && queuedTwo.isCompletedExceptionally()
                        && state.pendingOperationCount() == 0,
                "Shutdown abandoned command futures or pending accounting");
    }

    // PHASE 3 - Added in Phase 3
    private static void lobbyDeliveryDoesNotOccupyTheLobbyStateLane() throws Exception {
        AtomicBoolean blockPushes = new AtomicBoolean();
        CountDownLatch pushEntered = new CountDownLatch(1);
        CountDownLatch releasePush = new CountDownLatch(1);
        SessionRegistry sessions = new SessionRegistry();
        ClientSession host = sessions.create("lane-host");
        try (LobbyServerService service = new LobbyServerService((recipient, message) -> {
            if (blockPushes.get()) {
                pushEntered.countDown();
                try {
                    releasePush.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        })) {
            String lobbyId = service.create(host).get(2, TimeUnit.SECONDS)
                    .view().lobbyId().toString();
            blockPushes.set(true);
            CompletableFuture<?> ready = service.ready(host, lobbyId, true);
            check(pushEntered.await(2, TimeUnit.SECONDS), "Slow lobby delivery did not start");
            long started = System.nanoTime();
            service.view(lobbyId).get(250, TimeUnit.MILLISECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            check(elapsedMs < 200, "Slow lobby push occupied the lobby state lane");
            releasePush.countDown();
            ready.get(2, TimeUnit.SECONDS);
            System.out.println("PERF lobby_projection_during_slow_push_ms=" + elapsedMs);
        } finally {
            releasePush.countDown();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void startupSnapshotsAreCapturedBeforeTheMatchBecomesActionable()
            throws Exception {
        CountDownLatch firstStartPush = new CountDownLatch(1);
        CountDownLatch releaseStartPush = new CountDownLatch(1);
        List<GameStatePush> initialStates = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicBoolean blockStart = new AtomicBoolean(true);
        AtomicReference<String> announcedMatchId = new AtomicReference<>();
        SessionRegistry sessions = new SessionRegistry();
        ClientSession host = sessions.create("snapshot-host");
        ClientSession guest = sessions.create("snapshot-guest");
        try (LobbyServerService service = new LobbyServerService((recipient, message) -> {
            if (message instanceof GameStartedPush && blockStart.compareAndSet(true, false)) {
                announcedMatchId.set(((GameStartedPush) message).matchId());
                firstStartPush.countDown();
                try {
                    releaseStartPush.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (message instanceof GameStatePush state && state.stateVersion() == 1L) {
                initialStates.add(state);
            }
        })) {
            String lobbyId = service.create(host).join().view().lobbyId().toString();
            service.join(guest, lobbyId).join();
            service.selectMap(host, lobbyId, "emerald-plains").join();
            service.ready(host, lobbyId, true).join();
            service.ready(guest, lobbyId, true).join();
            CompletableFuture<civilization.server.lobby.LobbyStartResult> starting =
                    service.start(host, lobbyId);
            check(firstStartPush.await(2, TimeUnit.SECONDS), "Startup delivery did not pause");
            String matchId = announcedMatchId.get();
            long currentVersion = service.match(matchId).project(state -> state.stateVersion())
                    .get(2, TimeUnit.SECONDS);
            service.changeGameState(host, matchId, currentVersion, state -> "committed")
                    .get(2, TimeUnit.SECONDS);
            releaseStartPush.countDown();
            starting.get(2, TimeUnit.SECONDS);
            check(initialStates.size() == 2
                            && initialStates.stream().allMatch(push -> push.stateVersion() == 1L
                            && push.snapshot().turnNumber() == 1L),
                    "Initial snapshots observed a mutation committed after match registration");
        } finally {
            releaseStartPush.countDown();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void sixLocalClientsExchangeConcurrentChatAndCleanUp() throws Exception {
        Set<String> baselineThreads = phase3Threads();
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        LobbyServerNetworkAdapter adapter = new LobbyServerNetworkAdapter(sessions, serializer);
        TcpServer server = new TcpServer(serializer, adapter);
        List<NetworkManager> clients = new ArrayList<>();
        List<AtomicReference<LobbyStatePush>> lobbyStates = new ArrayList<>();
        AtomicInteger chatsReceived = new AtomicInteger();
        try {
            server.start(0);
            adapter.startHeartbeat(0);
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < 6; index++) {
                NetworkManager client = new NetworkManager(serializer);
                AtomicReference<LobbyStatePush> lobbyState = new AtomicReference<>();
                client.addListener(new civilization.client.net.ClientMessageListener() {
                    // PHASE 3 - Added in Phase 3
                    @Override public void onPush(civilization.protocol.PushMessage message) {
                        if (message instanceof LobbyStatePush push) lobbyState.set(push);
                        if (message instanceof ChatMessagePush) chatsReceived.incrementAndGet();
                    }
                    // PHASE 3 - Added in Phase 3
                    @Override public void onConnectionStateChanged(
                            civilization.client.net.NetworkConnectionState state,
                            Throwable cause) {}
                });
                client.connect("127.0.0.1", server.localPort()).get(3, TimeUnit.SECONDS);
                ProtocolMessage welcome = client.send(new HelloRequest(
                        MessageMetadata.createNow(), "load-user-" + index))
                        .get(3, TimeUnit.SECONDS);
                check(welcome instanceof WelcomeResponse, "Client authentication failed");
                clients.add(client);
                lobbyStates.add(lobbyState);
            }
            check(clients.get(0).send(new CreateLobbyRequest(MessageMetadata.createNow()))
                    .get(3, TimeUnit.SECONDS) instanceof RequestAcceptedResponse,
                    "Lobby creation failed under load");
            await(() -> lobbyStates.get(0).get() != null, "host lobby snapshot");
            String lobbyId = lobbyStates.get(0).get().snapshot().lobbyId();
            // PHASE 3 - Added in Phase 3
            for (int index = 1; index < clients.size(); index++) {
                clients.get(index).send(new JoinLobbyRequest(
                        MessageMetadata.createNow(), lobbyId)).get(3, TimeUnit.SECONDS);
            }
            List<CompletableFuture<?>> chats = new ArrayList<>();
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < clients.size(); index++) {
                chats.add(clients.get(index).send(new SendChatRequest(
                        MessageMetadata.createNow(), lobbyId, "parallel-" + index)));
            }
            CompletableFuture.allOf(chats.toArray(CompletableFuture[]::new))
                    .get(4, TimeUnit.SECONDS);
            await(() -> chatsReceived.get() == 36, "six-client chat fan-out");
            check(server.connectionCount() == 6 && adapter.authenticatedConnectionCount() == 6,
                    "Concurrent connection registries lost a client");
            AtomicLong edtSendMs = new AtomicLong(Long.MAX_VALUE);
            SwingUtilities.invokeAndWait(() -> {
                long start = System.nanoTime();
                clients.get(0).send(new SendChatRequest(
                        MessageMetadata.createNow(), lobbyId, "edt-enqueue"));
                edtSendMs.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            });
            check(edtSendMs.get() < 100L, "Request enqueue blocked the Swing EDT");
            System.out.println("PERF six_client_chat_fanout_messages=" + chatsReceived.get());
            System.out.println("PERF edt_network_enqueue_ms=" + edtSendMs.get());
        } finally {
            clients.forEach(NetworkManager::close);
            adapter.close();
            server.closeAndAwait(Duration.ofSeconds(3));
        }
        await(() -> phase3Threads().stream().allMatch(baselineThreads::contains),
                "Phase 3 thread cleanup");
        check(server.connectionCount() == 0 && adapter.connectionCount() == 0,
                "Socket or connection registry leaked after shutdown");
    }

    // PHASE 3 - Added in Phase 3
    private static void largeMapProjectionAndHighTurnsRemainBounded() {
        SessionRegistry sessions = new SessionRegistry();
        List<ClientSession> clients = new ArrayList<>();
        List<LobbyMember> members = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < 6; index++) {
            ClientSession session = sessions.create("large-map-" + index);
            clients.add(session);
            members.add(new LobbyMember(session.sessionId(), session.playerId(),
                    session.username(), session.createdAt(), true, true));
        }
        MultiplayerGameState game = createLargeAuditGame(members);
        Set<HexCoord> allCoordinates = new HashSet<>();
        game.map().tiles().forEach(tile -> allCoordinates.add(tile.coordinate()));
        game.players().values().forEach(player -> {
            player.empire().visibility().discoverAll(allCoordinates);
            player.empire().visibility().replaceCurrentlyVisible(allCoordinates);
        });
        FilteredGameStateProjector projector = new FilteredGameStateProjector();
        long projectionStart = System.nanoTime();
        // PHASE 3 - Added in Phase 3
        for (int iteration = 0; iteration < 100; iteration++) {
            check(projector.projectConnected(game).clientSnapshots().size() == 6,
                    "Large-map projection omitted a client");
        }
        long projectionMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - projectionStart);
        MultiplayerTurnService turns = new MultiplayerTurnService(
                DefaultRulesetFactory.create());
        long turnStart = System.nanoTime();
        // PHASE 3 - Added in Phase 3
        for (int turn = 0; turn < 300; turn++) {
            turns.endTurn(game, game.activePlayerId(), game.stateVersion());
        }
        long turnsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - turnStart);
        check(game.turn().turnNumber() >= 301L, "High turn number did not advance");
        check(projectionMs < 8_000L && turnsMs < 8_000L,
                "Large-map projection or high-turn processing exceeded safety budget");
        System.out.println("PERF large_map_tiles=" + game.map().tiles().size());
        System.out.println("PERF large_map_100x6_full_visibility_projection_ms=" + projectionMs);
        System.out.println("PERF large_map_300_turns_ms=" + turnsMs);
    }

    // PHASE 3 - Added in Phase 3
    private static MultiplayerGameState createLargeAuditGame(List<LobbyMember> members) {
        int radius = 20;
        ArrayList<HexTile> tiles = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (int q = -radius; q <= radius; q++) {
            int minimumR = Math.max(-radius, -q - radius);
            int maximumR = Math.min(radius, -q + radius);
            for (int r = minimumR; r <= maximumR; r++) {
                tiles.add(new HexTile(new HexCoord(q, r), TerrainType.PLAINS));
            }
        }
        GameMap map = new GameMap(tiles);
        List<HexCoord> spawns = List.of(new HexCoord(10, 0), new HexCoord(10, -10),
                new HexCoord(0, -10), new HexCoord(-10, 0),
                new HexCoord(-10, 10), new HexCoord(0, 10));
        var rules = DefaultRulesetFactory.create();
        UnitFactory units = new UnitFactory();
        ArrayList<MultiplayerPlayerState> players = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < members.size(); index++) {
            LobbyMember member = members.get(index);
            HexCoord spawn = spawns.get(index);
            FactionId faction = new FactionId("audit:" + member.playerId());
            TownHall hall = new TownHall(BuildingId.random(), spawn,
                    new ResourceStockpile(rules.initialResources(),
                            rules.initialStorageCapacity()), new ProductionQueue(), faction);
            HashSet<HexCoord> territory = new HashSet<>(spawn.neighbors());
            territory.add(spawn);
            PlayerState empire = new PlayerState(faction, hall,
                    new UnitCapacity(rules.initialUnitCap()), new EmpireBorder(territory, map),
                    new VisibilityState(), new TechnologyState());
            for (UnitType type : List.of(UnitType.BUILDER, UnitType.WORKER,
                    UnitType.EXPLORER)) {
                empire.addUnit(units.create(type, spawn, rules, faction));
            }
            players.add(new MultiplayerPlayerState(member.sessionId(), member.playerId(),
                    member.username(), empire));
        }
        return new MultiplayerGameState("audit-large-match", "audit-large", map,
                players, Instant.now());
    }

    // PHASE 3 - Added in Phase 3
    private static Set<String> phase3Threads() {
        Set<String> result = new HashSet<>();
        // PHASE 3 - Added in Phase 3
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String name = thread.getName();
            if (thread.isAlive() && (name.startsWith("phase3-")
                    || name.startsWith("lobby-")
                    || name.startsWith("multiplayer-match-")
                    || name.equals("authoritative-state-broadcast"))) {
                result.add(name);
            }
        }
        return result;
    }

    // PHASE 3 - Added in Phase 3
    private static void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        // PHASE 3 - Added in Phase 3
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        check(condition.getAsBoolean(), "Timed out waiting for " + description);
    }

    // PHASE 3 - Added in Phase 3
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
