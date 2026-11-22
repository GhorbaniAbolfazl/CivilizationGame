package civilization.phase3;

import civilization.client.controller.ClientSessionController;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.net.ClientMessageListener;
import civilization.client.net.NetworkManager;
import civilization.client.net.NetworkConnectionState;
import civilization.network.transport.ConnectionId;
import civilization.network.transport.HeartbeatListener;
import civilization.network.transport.HeartbeatLivenessTracker;
import civilization.network.transport.ServerConnectionListener;
import civilization.network.transport.TcpMessageConnection;
import civilization.network.transport.TcpServer;
import civilization.network.transport.UdpHeartbeatEndpoint;
import civilization.protocol.*;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.ProtocolException;
import civilization.server.dispatch.RequestDispatcher;
import civilization.server.match.SerializedServerState;
import civilization.server.session.SessionRegistry;

import javax.swing.SwingUtilities;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

// PHASE 3 - Added in Phase 3
public final class Phase3ArchitectureFoundationTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3ArchitectureFoundationTestSuite() {
    }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        jsonMessagesRoundTripWithoutDomainSerialization();
        rawTcpCarriesCorrelatedRequestAndResponse();
        tcpServerTracksMultipleClientsAndCleansUpFailures();
        tcpRequestTimeoutRemovesPendingRequest();
        rawUdpCarriesNonAuthoritativeHeartbeat();
        heartbeatTrackerDetectsOnlyRegisteredStaleSessions();
        sessionBindingRejectsStaleConnectionDisconnects();
        serializedStatePreventsConcurrentMutationRaces();
        dispatcherValidatesSessionOutsideTransport();
        controllerDependsOnCommandPortRatherThanNetworkManager();
        swingDispatcherMarshalsWorkOntoEdt();
        System.out.println("Phase3ArchitectureFoundationTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void jsonMessagesRoundTripWithoutDomainSerialization() {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        MessageMetadata metadata = new MessageMetadata(1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2030-01-02T03:04:05Z"));
        UUID replyTo = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Instant createdAt = Instant.parse("2030-01-02T03:04:06Z");
        ResourceBundleDto offered = new ResourceBundleDto(Map.of("WOOD", 10L));
        ResourceBundleDto requested = new ResourceBundleDto(Map.of("STONE", 4L));
        LobbySnapshotDto lobby = new LobbySnapshotDto("lobby-1", "player-1", "map-1",
                "OPEN", List.of(new LobbyMemberDto(
                        "player-1", "Ada", createdAt, true, true)));
        GameSnapshotDto game = new GameSnapshotDto("match-1", 3L, "player-1", "ACTIVE",
                Map.of("visibleHexes", "12", "viewer", "player-1"));
        // PHASE 3 - Added in Phase 3
        List<ProtocolMessage> messages = List.of(
                new HelloRequest(metadata, "Ada \"North\"\nLine"),
                new ResumeSessionRequest(metadata, "session-1", "resume-secret"),
                new DisconnectRequest(metadata, "leaving"),
                new CreateLobbyRequest(metadata),
                new JoinLobbyRequest(metadata, "lobby-1"),
                new LeaveLobbyRequest(metadata, "lobby-1"),
                new SetReadyRequest(metadata, "lobby-1", true),
                new SelectMapRequest(metadata, "lobby-1", "map-1"),
                new StartGameRequest(metadata, "lobby-1"),
                new SendChatRequest(metadata, "lobby-1", "Hello everyone"),
                new MoveRequest(metadata, "match-1", "unit-1", new HexCoordinate(4, -2), 7L),
                new BuildRequest(metadata, "match-1", "builder-1", "HALL_TOWN",
                        new HexCoordinate(5, -2), 7L),
                new CraftItemRequest(metadata, "match-1", "apothecary-1", "HEALING", 2, 7L),
                new UseItemRequest(metadata, "match-1", "item-1", "unit-1", null, 7L),
                new AttackRequest(metadata, "match-1", List.of("unit-1", "unit-2"),
                        "target-1", 7L),
                new CreateTradeOfferRequest(metadata, "match-1", "player-2", offered,
                        requested, 7L),
                new AcceptTradeOfferRequest(metadata, "match-1", "offer-1", 7L),
                new RejectTradeOfferRequest(metadata, "match-1", "offer-1", 7L),
                new CancelTradeOfferRequest(metadata, "match-1", "offer-1", 7L),
                new DeclareWarRequest(metadata, "match-1", "player-2", 7L),
                new AllianceRequest(metadata, "match-1", "player-2", 7L),
                new AllianceDecisionRequest(metadata, "match-1", "alliance-1",
                        AllianceDecision.ACCEPT, 7L),
                new BreakAllianceRequest(metadata, "match-1", "player-2", 7L),
                new EndTurnRequest(metadata, "match-1", 7L),
                new WelcomeResponse(metadata, replyTo, "session-1", "player-1",
                        "resume-secret", 5051),
                new ResumeAcceptedResponse(metadata, replyTo, "session-1", "player-1",
                        5051, 7L),
                new RequestAcceptedResponse(metadata, replyTo, 8L),
                new ErrorResponse(metadata, replyTo, "NOT_OWNER",
                        "Player does not own asset", false, 7L, false),
                new LobbyStatePush(metadata, 3L, lobby),
                new GameStartedPush(metadata, "lobby-1", "match-1", "map-1", 1L),
                new GameStatePush(metadata, 7L, game),
                new ChatMessagePush(metadata, "chat-1", "match-1", "player-1", "Ada",
                        "Ready", createdAt),
                new NotificationPush(metadata, "WAR_DECLARED", "War has been declared", 8L),
                new BattleReportPush(metadata, 8L, "battle-1", "player-1", "player-2",
                        "target-1", "ATTACKER_WON", Map.of("unit-1", 3, "target-1", 0)),
                new ConnectionStatusPush(metadata, "player-2", ConnectionStatus.DISCONNECTED, 8L),
                new TurnChangedPush(metadata, "match-1", 8L, 4L, "player-2", "ACTIVE",
                        Instant.parse("2030-01-02T03:05:00Z")),
                new DiplomacyStatePush(metadata, "match-1", 8L, "player-1", "player-2", "WAR"),
                new AllianceRequestPush(metadata, "match-1", 8L, "alliance-1",
                        "player-1", "player-2", "PENDING", createdAt),
                new TradeOfferPush(metadata, "match-1", 8L, "offer-1", "player-1",
                        "player-2", offered, requested, "OPEN", createdAt),
                new ServerShutdownPush(metadata, "Maintenance", createdAt),
                new HeartbeatPing(metadata, "session-1", 9L),
                new HeartbeatPong(metadata, "session-1", 9L));
        // PHASE 3 - Added in Phase 3
        for (ProtocolMessage message : messages) {
            String json = serializer.serialize(message);
            check(json.contains("\"type\":\"" + message.type().name() + "\"")
                            && serializer.deserialize(json).equals(message),
                    "JSON round trip failed for " + message.type());
        }
        check(messages.size() == MessageType.values().length
                        && serializer.registry().messageTypes().size() == MessageType.values().length,
                "Round-trip catalog does not cover every registered message type");

        String helloJson = serializer.serialize(messages.get(0));
        expectProtocolFailure(() -> serializer.deserialize("{"),
                ProtocolException.Code.MALFORMED_JSON);
        expectProtocolFailure(() -> serializer.deserialize("[]"),
                ProtocolException.Code.MALFORMED_JSON);
        expectProtocolFailure(() -> serializer.deserialize(
                        helloJson.replace("HELLO_REQUEST", "UNKNOWN_TYPE")),
                ProtocolException.Code.UNKNOWN_MESSAGE_TYPE);
        expectProtocolFailure(() -> serializer.deserialize(
                        helloJson.replace("\"protocolVersion\":1", "\"protocolVersion\":99")),
                ProtocolException.Code.UNSUPPORTED_PROTOCOL_VERSION);
        expectProtocolFailure(() -> serializer.deserialize(
                        helloJson.substring(0, helloJson.length() - 1) + ",\"unexpected\":true}"),
                ProtocolException.Code.INVALID_MESSAGE);
        expectProtocolFailure(() -> serializer.deserialize(
                        helloJson.substring(0, helloJson.length() - 1)
                                + ",\"type\":\"HELLO_REQUEST\"}"),
                ProtocolException.Code.MALFORMED_JSON);
        String joinJson = serializer.serialize(new JoinLobbyRequest(metadata, "lobby-1"));
        expectProtocolFailure(() -> serializer.deserialize(
                        joinJson.replace(",\"lobbyId\":\"lobby-1\"", "")),
                ProtocolException.Code.INVALID_MESSAGE);
        String readyJson = serializer.serialize(new SetReadyRequest(metadata, "lobby-1", true));
        expectProtocolFailure(() -> serializer.deserialize(
                        readyJson.replace("\"ready\":true", "\"ready\":\"true\"")),
                ProtocolException.Code.INVALID_MESSAGE);
        String moveJson = serializer.serialize(new MoveRequest(
                metadata, "match-1", "unit-1", new HexCoordinate(4, -2), 7L));
        expectProtocolFailure(() -> serializer.deserialize(
                        moveJson.replace("\"q\":4", "\"q\":4.5")),
                ProtocolException.Code.INVALID_MESSAGE);
    }

    // PHASE 3 - Added in Phase 3
    private static void rawTcpCarriesCorrelatedRequestAndResponse() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch pushReceived = new CountDownLatch(1);
        AtomicReference<String> clientListenerThread = new AtomicReference<>();
        try (TcpServer server = new TcpServer(serializer, new ServerConnectionListener() {
            // PHASE 3 - Added in Phase 3
            @Override
            public void onConnected(TcpMessageConnection connection) {
                connected.countDown();
                // PHASE 3 - Added in Phase 3
                connection.send(new NotificationPush(MessageMetadata.createNow(),
                        "TRANSPORT_TEST", "listener-thread", 1));
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onMessage(TcpMessageConnection connection, ProtocolMessage message) {
                if (message instanceof HelloRequest hello) {
                    connection.send(new WelcomeResponse(MessageMetadata.createNow(),
                            hello.metadata().messageId(), "session-tcp", "player-tcp"));
                }
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onDisconnected(TcpMessageConnection connection, Throwable cause) {
                // The connection is deliberately closed by this test.
            }
        }); NetworkManager client = new NetworkManager(serializer)) {
            server.start(0);
            // PHASE 3 - Added in Phase 3
            client.addListener(new ClientMessageListener() {
                // PHASE 3 - Added in Phase 3
                @Override
                public void onPush(civilization.protocol.PushMessage message) {
                    throw new IllegalStateException("deliberate listener failure");
                }

                // PHASE 3 - Added in Phase 3
                @Override
                public void onConnectionStateChanged(
                        NetworkConnectionState state, Throwable cause) {
                    throw new IllegalStateException("deliberate listener failure");
                }
            });
            // PHASE 3 - Added in Phase 3
            client.addListener(new ClientMessageListener() {
                // PHASE 3 - Added in Phase 3
                @Override
                public void onPush(civilization.protocol.PushMessage message) {
                    clientListenerThread.set(Thread.currentThread().getName());
                    pushReceived.countDown();
                }

                // PHASE 3 - Added in Phase 3
                @Override
                public void onConnectionStateChanged(
                        NetworkConnectionState state, Throwable cause) {
                    // Connection-state observation is not needed in this assertion.
                }
            });
            client.connect("127.0.0.1", server.localPort()).get(3, TimeUnit.SECONDS);
            check(connected.await(3, TimeUnit.SECONDS), "Raw TCP server did not accept client");
            check(pushReceived.await(3, TimeUnit.SECONDS)
                            && clientListenerThread.get().startsWith("phase3-tcp-reader-"),
                    "Client messages did not arrive on the dedicated TCP listener thread");
            HelloRequest hello = new HelloRequest(MessageMetadata.createNow(), "network-user");
            var response = client.send(hello).get(3, TimeUnit.SECONDS);
            check(response instanceof WelcomeResponse welcome
                            && welcome.replyTo().equals(hello.metadata().messageId())
                            && welcome.playerId().equals("player-tcp"),
                    "Raw TCP request/response correlation failed");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void tcpServerTracksMultipleClientsAndCleansUpFailures() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        int clientCount = 4;
        CountDownLatch connected = new CountDownLatch(clientCount);
        CountDownLatch messages = new CountDownLatch(clientCount);
        CountDownLatch disconnected = new CountDownLatch(clientCount);
        Set<ConnectionId> connectionIds = ConcurrentHashMap.newKeySet();
        Set<String> serverReaderThreads = ConcurrentHashMap.newKeySet();
        List<NetworkManager> clients = new ArrayList<>();
        TcpServer server = new TcpServer(serializer, new ServerConnectionListener() {
            // PHASE 3 - Added in Phase 3
            @Override
            public void onConnected(TcpMessageConnection connection) {
                connectionIds.add(connection.connectionId());
                connected.countDown();
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onMessage(TcpMessageConnection connection, ProtocolMessage message) {
                serverReaderThreads.add(Thread.currentThread().getName());
                messages.countDown();
                if (message instanceof HelloRequest hello) {
                    connection.send(new WelcomeResponse(MessageMetadata.createNow(),
                            hello.metadata().messageId(), "multi-session", "multi-player"));
                }
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onDisconnected(TcpMessageConnection connection, Throwable cause) {
                disconnected.countDown();
            }
        });
        try {
            server.start(0);
            List<CompletableFuture<Void>> connects = new ArrayList<>();
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < clientCount; index++) {
                NetworkManager client = new NetworkManager(serializer);
                clients.add(client);
                connects.add(client.connect("127.0.0.1", server.localPort()));
            }
            CompletableFuture.allOf(connects.toArray(CompletableFuture[]::new))
                    .get(3, TimeUnit.SECONDS);
            check(connected.await(3, TimeUnit.SECONDS),
                    "TCP server did not accept all simultaneous clients");
            check(server.connectionCount() == clientCount
                            && server.connectionSnapshot().size() == clientCount
                            && connectionIds.size() == clientCount,
                    "TCP server connection registry lost or duplicated a client");

            List<CompletableFuture<?>> responses = new ArrayList<>();
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < clientCount; index++) {
                responses.add(clients.get(index).send(new HelloRequest(
                        MessageMetadata.createNow(), "client-" + index)));
            }
            CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new))
                    .get(3, TimeUnit.SECONDS);
            check(messages.await(3, TimeUnit.SECONDS)
                            && serverReaderThreads.size() == clientCount
                            && serverReaderThreads.stream().allMatch(
                                    name -> name.startsWith("phase3-tcp-reader-")),
                    "Server did not use an independent reader thread per connection");

            check(clients.get(0).closeAndAwait(Duration.ofSeconds(2)),
                    "Client transport did not shut down within its bound");
            check(waitUntil(() -> server.connectionCount() == clientCount - 1,
                            Duration.ofSeconds(2)),
                    "Server did not detect and remove the failed client connection");
            check(server.closeAndAwait(Duration.ofSeconds(2)),
                    "TCP server did not shut down within its bound");
            check(disconnected.await(2, TimeUnit.SECONDS) && server.connectionCount() == 0,
                    "TCP server did not clean all tracked connections on shutdown");
        } finally {
            server.close();
            // PHASE 3 - Added in Phase 3
            for (NetworkManager client : clients) client.close();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void tcpRequestTimeoutRemovesPendingRequest() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        try (TcpServer server = new TcpServer(serializer, new ServerConnectionListener() {
            // PHASE 3 - Added in Phase 3
            @Override
            public void onConnected(TcpMessageConnection connection) {
                // The server deliberately leaves the request unanswered.
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onMessage(TcpMessageConnection connection, ProtocolMessage message) {
                // The server deliberately leaves the request unanswered.
            }

            // PHASE 3 - Added in Phase 3
            @Override
            public void onDisconnected(TcpMessageConnection connection, Throwable cause) {
                // The test closes the connection after the timeout assertion.
            }
        }); NetworkManager client = new NetworkManager(serializer)) {
            server.start(0);
            client.connect("127.0.0.1", server.localPort()).get(3, TimeUnit.SECONDS);
            CompletableFuture<?> response = client.send(
                    new HelloRequest(MessageMetadata.createNow(), "timeout-client"),
                    Duration.ofMillis(100));
            boolean timedOut = false;
            try {
                response.get(2, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException expected) {
                timedOut = expected.getCause() instanceof SocketTimeoutException;
            }
            check(timedOut && client.pendingRequestCount() == 0,
                    "Timed-out request was not failed and removed from pending state");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void rawUdpCarriesNonAuthoritativeHeartbeat() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        CountDownLatch pongReceived = new CountDownLatch(1);
        AtomicReference<UdpHeartbeatEndpoint> serverReference = new AtomicReference<>();
        try (UdpHeartbeatEndpoint server = new UdpHeartbeatEndpoint(serializer,
                new HeartbeatListener() {
                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onHeartbeat(
                            HeartbeatMessage message, SocketAddress remoteAddress) {
                        if (message instanceof HeartbeatPing ping) {
                            try {
                                serverReference.get().send(new HeartbeatPong(
                                        MessageMetadata.createNow(), ping.sessionId(),
                                        ping.sequence()), remoteAddress);
                            } catch (java.io.IOException failure) {
                                throw new AssertionError(failure);
                            }
                        }
                    }

                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onTransportFailure(Throwable cause) {
                        throw new AssertionError(cause);
                    }
                }); UdpHeartbeatEndpoint client = new UdpHeartbeatEndpoint(serializer,
                new HeartbeatListener() {
                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onHeartbeat(
                            HeartbeatMessage message, SocketAddress remoteAddress) {
                        if (message instanceof HeartbeatPong pong && pong.sequence() == 4) {
                            pongReceived.countDown();
                        }
                    }

                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onTransportFailure(Throwable cause) {
                        throw new AssertionError(cause);
                    }
                })) {
            serverReference.set(server);
            server.start(0);
            client.start(0);
            client.send(new HeartbeatPing(MessageMetadata.createNow(), "udp-session", 4),
                    new InetSocketAddress("127.0.0.1", server.localPort()));
            check(pongReceived.await(3, TimeUnit.SECONDS),
                    "Raw UDP heartbeat did not complete");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void heartbeatTrackerDetectsOnlyRegisteredStaleSessions() {
        AtomicLong nanoTime = new AtomicLong();
        HeartbeatLivenessTracker tracker = new HeartbeatLivenessTracker(nanoTime::get);
        tracker.register("active-session");
        tracker.register("silent-session");
        nanoTime.set(5L);
        check(tracker.record("active-session", 1L),
                "Registered heartbeat was not recorded");
        check(!tracker.record("active-session", 1L)
                        && !tracker.record("unknown-session", 1L),
                "Duplicate or unregistered heartbeat changed liveness state");
        nanoTime.set(11L);
        check(tracker.staleSessions(Duration.ofNanos(10L))
                        .equals(Set.of("silent-session")),
                "Heartbeat timeout did not isolate the silent session");
        nanoTime.set(16L);
        check(tracker.staleSessions(Duration.ofNanos(10L))
                        .equals(Set.of("active-session", "silent-session")),
                "Heartbeat timeout did not eventually mark all silent sessions stale");
        check(tracker.unregister("silent-session") && tracker.trackedSessionCount() == 1,
                "Heartbeat tracker did not clean up a disconnected session");
    }

    // PHASE 3 - Added in Phase 3
    private static void sessionBindingRejectsStaleConnectionDisconnects() {
        SessionRegistry sessions = new SessionRegistry();
        var session = sessions.create("connection-owner");
        ConnectionId oldConnection = ConnectionId.create();
        ConnectionId replacement = ConnectionId.create();
        long oldGeneration = session.bindConnection(oldConnection);
        long replacementGeneration = session.bindConnection(replacement);
        check(replacementGeneration > oldGeneration
                        && !session.unbindConnection(oldConnection)
                        && session.activeConnection().orElseThrow().equals(replacement),
                "A stale disconnect removed the replacement session connection");
        check(session.unbindConnection(replacement)
                        && session.status()
                        == civilization.server.session.SessionStatus.DISCONNECTED,
                "Current connection disconnect did not update the session lifecycle");
    }

    // PHASE 3 - Added in Phase 3
    private static void serializedStatePreventsConcurrentMutationRaces() {
        int[] counter = {0};
        Set<String> mutationThreads = ConcurrentHashMap.newKeySet();
        try (SerializedServerState<int[]> state = new SerializedServerState<>(
                counter, "phase3-test-command-lane")) {
            List<CompletableFuture<Integer>> operations = new ArrayList<>();
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < 500; index++) {
                operations.add(state.submit(value -> {
                    mutationThreads.add(Thread.currentThread().getName());
                    return ++value[0];
                }));
            }
            CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).join();
            int finalValue = state.submit(value -> value[0]).join();
            check(finalValue == 500, "Serialized server state lost concurrent commands");
            check(mutationThreads.size() == 1,
                    "Authoritative state was mutated by more than one thread");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void dispatcherValidatesSessionOutsideTransport() {
        SessionRegistry sessions = new SessionRegistry();
        var session = sessions.create("dispatcher-user");
        RequestDispatcher dispatcher = new RequestDispatcher(sessions);
        dispatcher.register(MessageType.HELLO_REQUEST, (trustedSession, request) ->
                CompletableFuture.completedFuture(new WelcomeResponse(
                        MessageMetadata.createNow(), request.metadata().messageId(),
                        trustedSession.sessionId().toString(),
                        trustedSession.playerId().toString())));
        HelloRequest request = new HelloRequest(MessageMetadata.createNow(), "ignored-as-actor");
        var accepted = dispatcher.dispatch(session.sessionId(), request).join();
        check(accepted instanceof WelcomeResponse welcome
                        && welcome.playerId().equals(session.playerId().toString()),
                "Dispatcher did not use trusted session identity");
        var rejected = dispatcher.dispatch(
                new civilization.server.session.SessionId(UUID.randomUUID()), request).join();
        check(rejected instanceof ErrorResponse error
                        && error.code().equals("INVALID_SESSION"),
                "Dispatcher did not reject unknown session before handler");
    }

    // PHASE 3 - Added in Phase 3
    private static void controllerDependsOnCommandPortRatherThanNetworkManager() {
        AtomicReference<HelloRequest> captured = new AtomicReference<>();
        ClientSessionController controller = new ClientSessionController(request -> {
            captured.set((HelloRequest) request);
            return CompletableFuture.completedFuture(new WelcomeResponse(
                    MessageMetadata.createNow(), request.metadata().messageId(),
                    "port-session", "port-player"));
        });
        var response = controller.requestSession("port-user").join();
        check(captured.get() != null && captured.get().username().equals("port-user")
                        && response instanceof WelcomeResponse,
                "Client controller did not use its command port boundary");
    }

    // PHASE 3 - Added in Phase 3
    private static void swingDispatcherMarshalsWorkOntoEdt() throws Exception {
        SwingUiEventDispatcher dispatcher = new SwingUiEventDispatcher();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean ranOnEdt = new AtomicBoolean();
        Thread caller = new Thread(() -> dispatcher.dispatch(() -> {
            ranOnEdt.set(SwingUtilities.isEventDispatchThread());
            completed.countDown();
        }), "phase3-test-listener-thread");
        caller.start();
        caller.join();
        check(completed.await(3, TimeUnit.SECONDS) && ranOnEdt.get(),
                "Client listener update was not marshalled onto Swing EDT");
    }

    // PHASE 3 - Added in Phase 3
    private static boolean waitUntil(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        // PHASE 3 - Added in Phase 3
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        return condition.getAsBoolean();
    }

    // PHASE 3 - Added in Phase 3
    private static void expectFailure(Runnable action, String message) {
        boolean failed = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            failed = true;
        }
        check(failed, message);
    }

    // PHASE 3 - Added in Phase 3
    private static void expectProtocolFailure(
            Runnable action, ProtocolException.Code expectedCode) {
        ProtocolException observed = null;
        try {
            action.run();
        } catch (ProtocolException failure) {
            observed = failure;
        }
        check(observed != null && observed.code() == expectedCode,
                "Expected protocol failure " + expectedCode + " but observed "
                        + (observed == null ? "success" : observed.code()));
    }

    // PHASE 3 - Added in Phase 3
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
