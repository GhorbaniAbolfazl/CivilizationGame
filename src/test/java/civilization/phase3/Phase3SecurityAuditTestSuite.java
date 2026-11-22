package civilization.phase3;

import civilization.application.factory.UnitFactory;
import civilization.client.net.NetworkManager;
import civilization.model.economy.ResourceType;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitType;
import civilization.network.transport.ConnectionId;
import civilization.network.transport.ServerConnectionListener;
import civilization.network.transport.TcpMessageConnection;
import civilization.network.transport.TcpServer;
import civilization.protocol.AllianceDecision;
import civilization.protocol.ErrorResponse;
import civilization.protocol.HeartbeatPing;
import civilization.protocol.HelloRequest;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.MessageType;
import civilization.protocol.MoveRequest;
import civilization.protocol.ProtocolMessage;
import civilization.protocol.RequestAcceptedResponse;
import civilization.protocol.ResumeSessionRequest;
import civilization.protocol.ResourceBundleDto;
import civilization.protocol.ResponseMessage;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.combat.PvpCombatService;
import civilization.server.diplomacy.AllianceRequestStatus;
import civilization.server.diplomacy.PlayerDiplomacyService;
import civilization.server.dispatch.RequestDispatcher;
import civilization.server.lobby.LobbyMember;
import civilization.server.map.PredefinedMapCatalog;
import civilization.server.map.PredefinedMapLoader;
import civilization.server.match.MatchException;
import civilization.server.match.MultiplayerGameInitializer;
import civilization.server.match.MultiplayerGameState;
import civilization.server.security.ServerRequestGuard;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.server.session.SessionStatus;
import civilization.server.trade.PlayerTradeService;
import civilization.server.trade.PlayerTradeStatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

// PHASE 3 - Added in Phase 3
public final class Phase3SecurityAuditTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final Instant NOW = Instant.parse("2032-04-05T06:07:08Z");

    // PHASE 3 - Added in Phase 3
    private Phase3SecurityAuditTestSuite() { }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        duplicateMessageIdsExecuteOnlyOnce();
        unscopedMoveRequestIsRejectedByServerAuthority();
        requestFloodsAreRateLimitedBeforeHandlerExecution();
        tradeResultsCannotChangeAfterCommit();
        allianceResultsCannotChangeAfterCommit();
        hiddenCombatTargetsDoNotCreateAValidationOracle();
        spoofedUdpCannotKeepAnUnresponsiveTcpSessionAlive();
        tcpConnectionFloodIsBounded();
        unauthenticatedConnectionsCannotSpamOrHoldSlots();
        sessionCreationFloodIsBounded();
        expiredSessionsArePurged();
        System.out.println("Phase3SecurityAuditTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void duplicateMessageIdsExecuteOnlyOnce() {
        SessionRegistry sessions = new SessionRegistry();
        ClientSession session = sessions.create("Replay target");
        AtomicInteger executions = new AtomicInteger();
        RequestDispatcher dispatcher = new RequestDispatcher(sessions,
                new ServerRequestGuard());
        dispatcher.register(MessageType.HELLO_REQUEST, (trusted, request) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(new RequestAcceptedResponse(
                    MessageMetadata.createNow(), request.metadata().messageId(), 1L));
        });
        HelloRequest request = new HelloRequest(MessageMetadata.createNow(), "ignored");
        ResponseMessage first = dispatcher.dispatch(session.sessionId(), request).join();
        ResponseMessage replay = dispatcher.dispatch(session.sessionId(), request).join();
        assert first instanceof RequestAcceptedResponse;
        assert replay instanceof ErrorResponse error
                && error.code().equals("DUPLICATE_REQUEST");
        assert executions.get() == 1 : "A replayed request reached the handler twice";
    }

    // PHASE 3 - Added in Phase 3
    private static void unscopedMoveRequestIsRejectedByServerAuthority() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (civilization.server.lobby.LobbyServerNetworkAdapter adapter =
                     new civilization.server.lobby.LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter);
             NetworkManager attacker = new NetworkManager(serializer)) {
            server.start(0);
            attacker.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
            attacker.send(new HelloRequest(MessageMetadata.createNow(), "Move attacker"))
                    .get(2, TimeUnit.SECONDS);
            ResponseMessage response = attacker.send(new MoveRequest(MessageMetadata.createNow(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    new HexCoordinate(0, 0), 0L)).get(2, TimeUnit.SECONDS);
            assert response instanceof ErrorResponse error
                    && error.code().equals("NOT_MATCH_PLAYER")
                    : "Movement must be registered but rejected outside the authenticated match";
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void requestFloodsAreRateLimitedBeforeHandlerExecution() {
        AtomicLong nanoTime = new AtomicLong();
        SessionRegistry sessions = new SessionRegistry();
        ClientSession session = sessions.create("Flood target");
        AtomicInteger executions = new AtomicInteger();
        ServerRequestGuard guard = new ServerRequestGuard(2, Duration.ofSeconds(1),
                32, Duration.ofMinutes(1), nanoTime::get);
        RequestDispatcher dispatcher = new RequestDispatcher(sessions, guard);
        dispatcher.register(MessageType.HELLO_REQUEST, (trusted, request) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture(new RequestAcceptedResponse(
                    MessageMetadata.createNow(), request.metadata().messageId(), 1L));
        });
        assert dispatcher.dispatch(session.sessionId(), hello()).join()
                instanceof RequestAcceptedResponse;
        assert dispatcher.dispatch(session.sessionId(), hello()).join()
                instanceof RequestAcceptedResponse;
        ResponseMessage rejected = dispatcher.dispatch(session.sessionId(), hello()).join();
        assert rejected instanceof ErrorResponse error && error.code().equals("RATE_LIMITED")
                && error.retryable();
        assert executions.get() == 2 : "Rate-limited request reached the handler";
        nanoTime.set(Duration.ofSeconds(1).toNanos());
        assert dispatcher.dispatch(session.sessionId(), hello()).join()
                instanceof RequestAcceptedResponse;
    }

    // PHASE 3 - Added in Phase 3
    private static void tradeResultsCannotChangeAfterCommit() {
        GameFixture fixture = new GameFixture();
        PlayerTradeService trades = new PlayerTradeService(
                Clock.fixed(NOW, ZoneOffset.UTC));
        int senderFood = fixture.game.requirePlayer(fixture.host.playerId()).empire()
                .townHall().stockpile().amountOf(ResourceType.FOOD);
        int recipientWood = fixture.game.requirePlayer(fixture.guest.playerId()).empire()
                .townHall().stockpile().amountOf(ResourceType.WOOD);
        var created = trades.create(fixture.game, fixture.host.playerId(),
                fixture.guest.playerId().toString(), bundle("FOOD", 1),
                bundle("WOOD", 1));
        var accepted = trades.accept(fixture.game, fixture.guest.playerId(),
                created.offer().offerId());
        assert created.offer().status() == PlayerTradeStatus.PENDING
                : "Earlier trade result was mutated before asynchronous delivery";
        assert accepted.offer().status() == PlayerTradeStatus.ACCEPTED;
        assert fixture.game.requirePlayer(fixture.host.playerId()).empire().townHall()
                .stockpile().amountOf(ResourceType.FOOD) == senderFood - 1;
        assert fixture.game.requirePlayer(fixture.guest.playerId()).empire().townHall()
                .stockpile().amountOf(ResourceType.WOOD) == recipientWood - 1;
    }

    // PHASE 3 - Added in Phase 3
    private static void allianceResultsCannotChangeAfterCommit() {
        GameFixture fixture = new GameFixture();
        PlayerDiplomacyService diplomacy = new PlayerDiplomacyService(
                Clock.fixed(NOW, ZoneOffset.UTC));
        var requested = diplomacy.requestAlliance(fixture.game, fixture.host.playerId(),
                fixture.guest.playerId().toString());
        var accepted = diplomacy.decideAlliance(fixture.game, fixture.guest.playerId(),
                requested.allianceRequest().requestId(), AllianceDecision.ACCEPT);
        assert requested.allianceRequest().status() == AllianceRequestStatus.PENDING
                : "Earlier alliance result was mutated before asynchronous delivery";
        assert accepted.allianceRequest().status() == AllianceRequestStatus.ACCEPTED;
    }

    // PHASE 3 - Added in Phase 3
    private static void hiddenCombatTargetsDoNotCreateAValidationOracle() {
        GameFixture fixture = new GameFixture();
        var rules = DefaultRulesetFactory.create();
        UnitFactory units = new UnitFactory();
        var hostEmpire = fixture.game.requirePlayer(fixture.host.playerId()).empire();
        var guestEmpire = fixture.game.requirePlayer(fixture.guest.playerId()).empire();
        // PHASE 3 - Added in Phase 3
        List.copyOf(hostEmpire.units()).forEach(unit -> hostEmpire.removeUnit(unit.id()));
        List.copyOf(guestEmpire.units()).forEach(unit -> guestEmpire.removeUnit(unit.id()));
        HexCoord hostPosition = hostEmpire.townHall().position();
        HexCoord guestPosition = guestEmpire.townHall().position();
        Unit attacker = units.create(UnitType.SWORDSMAN, hostPosition, rules,
                hostEmpire.factionId());
        Unit hiddenTarget = units.create(UnitType.SWORDSMAN, guestPosition, rules,
                guestEmpire.factionId());
        hostEmpire.addUnit(attacker);
        guestEmpire.addUnit(hiddenTarget);
        hostEmpire.visibility().replaceCurrentlyVisible(java.util.Set.of(hostPosition));
        try {
            new PvpCombatService().attack(fixture.game, fixture.host.playerId(),
                    List.of(attacker.id().value().toString()),
                    hiddenTarget.id().value().toString());
            throw new AssertionError("Hidden target attack was accepted");
        } catch (MatchException expected) {
            assert expected.code().equals("TARGET_UNAVAILABLE")
                    : "Hidden target existence leaked through " + expected.code();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void spoofedUdpCannotKeepAnUnresponsiveTcpSessionAlive() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (civilization.server.lobby.LobbyServerNetworkAdapter adapter =
                     new civilization.server.lobby.LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter);
             DatagramSocket udp = new DatagramSocket()) {
            int heartbeatPort = adapter.startHeartbeat(0, Duration.ofMillis(50),
                    Duration.ofMillis(50), Duration.ofMillis(10));
            server.start(0);
            try (Socket silentTcp = new Socket("127.0.0.1", server.localPort())) {
                silentTcp.setSoTimeout(2_000);
                DataOutputStream output = new DataOutputStream(silentTcp.getOutputStream());
                DataInputStream input = new DataInputStream(silentTcp.getInputStream());
                HelloRequest hello = new HelloRequest(MessageMetadata.createNow(), "UDP spoofer");
                civilization.network.transport.TcpFrameCodec.write(output,
                        serializer.serialize(hello),
                        civilization.network.transport.TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
                WelcomeResponse welcome = (WelcomeResponse) serializer.deserialize(
                        civilization.network.transport.TcpFrameCodec.read(input,
                                civilization.network.transport.TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES));
                long deadline = System.nanoTime() + Duration.ofMillis(350).toNanos();
                long sequence = 1L;
                // PHASE 3 - Added in Phase 3
                while (System.nanoTime() < deadline
                        && sessions.find(SessionId.parse(welcome.sessionId())).orElseThrow()
                        .status() == SessionStatus.CONNECTED) {
                    byte[] payload = serializer.serialize(new HeartbeatPing(
                            MessageMetadata.createNow(), welcome.sessionId(), sequence++))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    udp.send(new DatagramPacket(payload, payload.length,
                            new InetSocketAddress("127.0.0.1", heartbeatPort)));
                    Thread.sleep(10L);
                }
                await(() -> sessions.find(SessionId.parse(welcome.sessionId()))
                                .map(value -> value.status() != SessionStatus.CONNECTED)
                                .orElse(true),
                        "TCP timeout despite forged UDP liveness");
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void tcpConnectionFloodIsBounded() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        ServerConnectionListener listener = new ServerConnectionListener() {
            // PHASE 3 - Added in Phase 3
            @Override public void onConnected(TcpMessageConnection connection) { }
            // PHASE 3 - Added in Phase 3
            @Override public void onMessage(TcpMessageConnection connection,
                                            ProtocolMessage message) { }
            // PHASE 3 - Added in Phase 3
            @Override public void onDisconnected(TcpMessageConnection connection,
                                                 Throwable cause) { }
        };
        try (TcpServer server = new TcpServer(serializer, listener, 2);
             Socket first = new Socket(); Socket second = new Socket(); Socket rejected = new Socket()) {
            server.start(0);
            first.connect(new InetSocketAddress("127.0.0.1", server.localPort()));
            second.connect(new InetSocketAddress("127.0.0.1", server.localPort()));
            rejected.connect(new InetSocketAddress("127.0.0.1", server.localPort()));
            await(() -> server.connectionCount() == 2, "bounded TCP connection count");
            Thread.sleep(50L);
            assert server.connectionCount() == 2
                    : "Connection flood exceeded the configured server bound";
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void expiredSessionsArePurged() {
        MutableClock clock = new MutableClock(NOW);
        SessionRegistry sessions = new SessionRegistry(clock, Duration.ofSeconds(2));
        ClientSession session = sessions.create("Expired attacker");
        ServerRequestGuard guard = new ServerRequestGuard();
        RequestDispatcher dispatcher = new RequestDispatcher(sessions, guard);
        dispatcher.register(MessageType.HELLO_REQUEST, (trusted, request) ->
                CompletableFuture.completedFuture(new RequestAcceptedResponse(
                        MessageMetadata.createNow(), request.metadata().messageId(), 1L)));
        dispatcher.dispatch(session.sessionId(), hello()).join();
        assert guard.trackedSessionCount() == 1;
        ConnectionId connection = ConnectionId.create();
        session.bindConnection(connection);
        assert sessions.disconnect(session.sessionId(), connection);
        clock.advance(Duration.ofSeconds(3));
        assert sessions.expireReconnectWindows() == 1;
        assert sessions.purgeClosedSessions() == 1;
        assert sessions.snapshot().isEmpty() : "Expired session remained in server memory";
        assert dispatcher.purgeSecurityStateForMissingSessions() == 1
                && guard.trackedSessionCount() == 0
                : "Expired session retained replay/rate security state";
    }

    // PHASE 3 - Added in Phase 3
    private static void sessionCreationFloodIsBounded() {
        SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30), 2);
        sessions.create("First");
        sessions.create("Second");
        try {
            sessions.create("Flood");
            throw new AssertionError("Session flood exceeded the configured capacity");
        } catch (civilization.server.session.SessionException expected) {
            assert expected.code().equals("SERVER_CAPACITY");
        }
        assert sessions.snapshot().size() == 2;
    }

    // PHASE 3 - Added in Phase 3
    private static void unauthenticatedConnectionsCannotSpamOrHoldSlots() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (civilization.server.lobby.LobbyServerNetworkAdapter adapter =
                             new civilization.server.lobby.LobbyServerNetworkAdapter(sessions, serializer,
                             Duration.ofMillis(150));
             TcpServer server = new TcpServer(serializer, adapter)) {
            server.start(0);
            try (Socket silent = new Socket("127.0.0.1", server.localPort())) {
                assert silent.isConnected();
                await(() -> adapter.connectionCount() == 1,
                        "unauthenticated connection registration");
                await(() -> adapter.connectionCount() == 0,
                        "unauthenticated handshake timeout");
            }
            try (Socket guessing = new Socket("127.0.0.1", server.localPort())) {
                guessing.setSoTimeout(2_000);
                DataOutputStream output = new DataOutputStream(guessing.getOutputStream());
                DataInputStream input = new DataInputStream(guessing.getInputStream());
                ResumeSessionRequest invalid = new ResumeSessionRequest(
                        MessageMetadata.createNow(), UUID.randomUUID().toString(),
                        "invalid-resume-secret");
                civilization.network.transport.TcpFrameCodec.write(output,
                        serializer.serialize(invalid),
                        civilization.network.transport.TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
                ProtocolMessage response = serializer.deserialize(
                        civilization.network.transport.TcpFrameCodec.read(input,
                                civilization.network.transport.TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES));
                assert response instanceof ErrorResponse error
                        && error.code().equals("UNKNOWN_SESSION");
                await(() -> adapter.connectionCount() == 0,
                        "failed authentication connection cleanup");
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static HelloRequest hello() {
        return new HelloRequest(MessageMetadata.createNow(), "ignored");
    }

    // PHASE 3 - Added in Phase 3
    private static ResourceBundleDto bundle(String resource, long amount) {
        return new ResourceBundleDto(Map.of(resource, amount));
    }

    // PHASE 3 - Added in Phase 3
    private static void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        // PHASE 3 - Added in Phase 3
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for " + description);
            }
            Thread.sleep(10L);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static final class GameFixture {
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(
                Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Host");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Guest");
        // PHASE 3 - Added in Phase 3
        private final MultiplayerGameState game;

        // PHASE 3 - Added in Phase 3
        private GameFixture() {
            var definition = PredefinedMapCatalog.standard().require("emerald-plains");
            game = new MultiplayerGameInitializer(new PredefinedMapLoader(),
                    DefaultRulesetFactory.create(), new UnitFactory(),
                    Clock.fixed(NOW, ZoneOffset.UTC)).initialize(UUID.randomUUID().toString(),
                    definition, List.of(member(host), member(guest)));
        }

        // PHASE 3 - Added in Phase 3
        private static LobbyMember member(ClientSession session) {
            return new LobbyMember(session.sessionId(), session.playerId(), session.username(),
                    session.createdAt(), true, true);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static final class MutableClock extends Clock {
        // PHASE 3 - Added in Phase 3
        private Instant instant;

        // PHASE 3 - Added in Phase 3
        private MutableClock(Instant instant) { this.instant = instant; }

        // PHASE 3 - Added in Phase 3
        private void advance(Duration duration) { instant = instant.plus(duration); }

        // PHASE 3 - Added in Phase 3
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        // PHASE 3 - Added in Phase 3
        @Override public Clock withZone(ZoneId zone) { return this; }

        // PHASE 3 - Added in Phase 3
        @Override public Instant instant() { return instant; }
    }
}
