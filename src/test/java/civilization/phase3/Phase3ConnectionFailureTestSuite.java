package civilization.phase3;

import civilization.client.net.ClientMessageListener;
import civilization.client.net.NetworkConnectionState;
import civilization.client.net.NetworkManager;
import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientStateStore;
import civilization.model.unit.Unit;
import civilization.network.transport.ConnectionId;
import civilization.network.transport.TcpFrameCodec;
import civilization.network.transport.TcpServer;
import civilization.protocol.ConnectionStatus;
import civilization.protocol.ConnectionStatusPush;
import civilization.protocol.GameStatePush;
import civilization.protocol.HelloRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.ResponseMessage;
import civilization.protocol.ResumeAcceptedResponse;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.server.lobby.LobbyServerNetworkAdapter;
import civilization.server.lobby.LobbyServerService;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.server.session.SessionStatus;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

// PHASE 3 - Added in Phase 3
public final class Phase3ConnectionFailureTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3ConnectionFailureTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        disconnectDuringOwnAndOtherTurnAndRepeatedReconnect();
        lobbyDisconnectAndReconnectPreserveIdentity();
        heartbeatTimeoutAndBrokenStreamCleanUpWithoutServerCrash();
        reconnectWindowExpiresDeterministically();
        System.out.println("Phase3ConnectionFailureTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void disconnectDuringOwnAndOtherTurnAndRepeatedReconnect() throws Exception {
        try (NetworkFixture fixture = new NetworkFixture()) {
            ConnectedClient hostConnection = fixture.connect("Turn Host");
            ConnectedClient guestConnection = fixture.connect("Turn Guest");
            ClientSession host = hostConnection.session(); ClientSession guest = guestConnection.session();
            ClientStateStore guestStore = new ClientStateStore(new SwingUiEventDispatcher());
            guestConnection.manager().addListener(new ClientMessageRouter(guestStore, new SwingUiEventDispatcher()));
            CountDownLatch uiNotice = new CountDownLatch(1); AtomicBoolean noticeOnEdt = new AtomicBoolean();
            // PHASE 3 - Added in Phase 3
            guestStore.addNotificationListener(notification -> {
                if (notification.code().equals("PLAYER_DISCONNECTED")) { noticeOnEdt.set(SwingUtilities.isEventDispatchThread()); uiNotice.countDown(); }
            });
            String matchId = fixture.startMatch(host, guest);
            List<String> originalHostUnits = fixture.adapter.lobbyService().match(matchId).project(state -> state.requirePlayer(host.playerId()).empire().units().stream().map(unit -> unit.id().value().toString()).toList()).join();
            String hiddenGuestHall = fixture.adapter.lobbyService().match(matchId).project(state -> state.requirePlayer(guest.playerId()).empire().townHall().id().value().toString()).join();
            fixture.adapter.lobbyService().changeGameState(host, matchId, 1, state -> {
                state.requirePlayer(host.playerId()).empire().units().iterator().next().actionPoints().spend(1); return null;
            }).join();
            hostConnection.manager().closeAndAwait(Duration.ofSeconds(2));
            await(() -> fixture.adapter.connectionCount() == 1 && !fixture.connected(matchId, host), "own-turn disconnect cleanup");
            var afterOwnTurnDisconnect = fixture.adapter.lobbyService().match(matchId).project(state -> state).join();
            assert afterOwnTurnDisconnect.activePlayerId().equals(guest.playerId());
            assert afterOwnTurnDisconnect.turn().turnNumber() == 2;
            assert afterOwnTurnDisconnect.stateVersion() == 3;
            assert afterOwnTurnDisconnect.requirePlayer(host.playerId()).empire().units().stream()
                    .map(Unit::actionPoints).allMatch(points -> points.current() == points.capacity());
            await(() -> guestConnection.capture().hasStatus(host.playerId().toString(), ConnectionStatus.DISCONNECTED), "disconnect notification");
            assert uiNotice.await(2, TimeUnit.SECONDS) && noticeOnEdt.get();

            ConnectedClient resumedOnce = fixture.resume(hostConnection.welcome());
            assert resumedOnce.response() instanceof ResumeAcceptedResponse accepted
                    && accepted.playerId().equals(host.playerId().toString());
            await(() -> fixture.connected(matchId, host) && resumedOnce.capture().latestState() != null, "first resume state");
            GameStatePush resumedState = resumedOnce.capture().latestState();
            assert resumedState.snapshot().viewerPlayerId().equals(host.playerId().toString());
            assert resumedState.stateVersion() == 4;
            assert resumedState.snapshot().visibleBuildings().stream().noneMatch(building -> building.buildingId().equals(hiddenGuestHall));
            assert fixture.adapter.lobbyService().match(matchId).project(state -> state.requirePlayer(host.playerId()).empire().units().stream().map(unit -> unit.id().value().toString()).toList()).join().equals(originalHostUnits);
            assert fixture.sessions.snapshot().size() == 2;
            assert fixture.adapter.lobbyService().match(matchId).project(state -> state.players().size()).join() == 2;

            long turnNumber = fixture.adapter.lobbyService().match(matchId).project(state -> state.turn().turnNumber()).join();
            resumedOnce.manager().closeAndAwait(Duration.ofSeconds(2));
            await(() -> !fixture.connected(matchId, host), "other-turn disconnect cleanup");
            var afterOtherTurnDisconnect = fixture.adapter.lobbyService().match(matchId).project(state -> state).join();
            assert afterOtherTurnDisconnect.activePlayerId().equals(guest.playerId());
            assert afterOtherTurnDisconnect.turn().turnNumber() == turnNumber;

            ConnectedClient resumedTwice = fixture.resume(hostConnection.welcome());
            await(() -> fixture.connected(matchId, host) && resumedTwice.capture().latestState() != null, "second resume state");
            assert resumedTwice.session() == host;
            assert hostConnection.welcome().playerId().equals(host.playerId().toString());
            assert fixture.sessions.snapshot().size() == 2;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void lobbyDisconnectAndReconnectPreserveIdentity() throws Exception {
        try (NetworkFixture fixture = new NetworkFixture()) {
            ConnectedClient hostConnection = fixture.connect("Lobby Host");
            ConnectedClient guestConnection = fixture.connect("Lobby Guest");
            ClientSession host = hostConnection.session(); ClientSession guest = guestConnection.session();
            String lobbyId = fixture.adapter.lobbyService().create(host).join().view().lobbyId().toString();
            fixture.adapter.lobbyService().join(guest, lobbyId).join();
            guestConnection.manager().closeAndAwait(Duration.ofSeconds(2));
            await(() -> fixture.adapter.lobbyService().view(lobbyId).join().members().size() == 1, "lobby disconnect");
            assert fixture.adapter.lobbyService().view(lobbyId).join().members().get(0).playerId().equals(host.playerId());
            await(() -> hostConnection.capture().hasStatus(guest.playerId().toString(), ConnectionStatus.DISCONNECTED), "lobby disconnect notice");

            ConnectedClient resumed = fixture.resume(guestConnection.welcome());
            await(() -> fixture.adapter.lobbyService().view(lobbyId).join().members().size() == 2, "lobby reconnect");
            assert resumed.session() == guest;
            assert fixture.adapter.lobbyService().view(lobbyId).join().members().stream()
                    .filter(member -> member.playerId().equals(guest.playerId())).count() == 1;
            assert fixture.sessions.snapshot().size() == 2;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void heartbeatTimeoutAndBrokenStreamCleanUpWithoutServerCrash() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (LobbyServerNetworkAdapter adapter = new LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter)) {
            adapter.startHeartbeat(0, Duration.ofMillis(35), Duration.ofMillis(35), Duration.ofMillis(10));
            server.start(0);
            RawClient silent = rawHello(server.localPort(), serializer, "Silent Client");
            await(() -> sessions.find(SessionId.parse(silent.welcome().sessionId())).orElseThrow().status() == SessionStatus.DISCONNECTED,
                    "heartbeat timeout disconnect");
            await(() -> server.connectionCount() == 0 && adapter.connectionCount() == 0
                    && adapter.authenticatedConnectionCount() == 0 && adapter.trackedHeartbeatCount() == 0,
                    "heartbeat cleanup");
            silent.socket().close();

            Socket broken = new Socket("127.0.0.1", server.localPort());
            DataOutputStream output = new DataOutputStream(broken.getOutputStream());
            DataInputStream input = new DataInputStream(broken.getInputStream());
            HelloRequest hello = new HelloRequest(MessageMetadata.createNow(), "Broken Stream");
            TcpFrameCodec.write(output, serializer.serialize(hello), TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
            WelcomeResponse brokenWelcome = (WelcomeResponse) serializer.deserialize(TcpFrameCodec.read(input, TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES));
            broken.setSoLinger(true, 0); broken.close();
            await(() -> sessions.find(SessionId.parse(brokenWelcome.sessionId())).orElseThrow().status() == SessionStatus.DISCONNECTED,
                    "broken stream disconnect");
            try (NetworkManager survivor = new NetworkManager(serializer)) {
                survivor.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
                assert survivor.send(new HelloRequest(MessageMetadata.createNow(), "Survivor")).get(2, TimeUnit.SECONDS) instanceof WelcomeResponse;
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void reconnectWindowExpiresDeterministically() {
        MutableClock clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
        SessionRegistry sessions = new SessionRegistry(clock, Duration.ofSeconds(5));
        ClientSession session = sessions.create("Expiry Player"); ConnectionId first = ConnectionId.create(); session.bindConnection(first);
        try (LobbyServerService service = new LobbyServerService((recipient, message) -> {})) {
            service.create(session).join(); assert sessions.disconnect(session.sessionId(), first); service.disconnect(session.sessionId()).join();
            assert service.resumableLobbyCount() == 1;
            clock.advance(Duration.ofSeconds(6)); assert sessions.expireReconnectWindows() == 1;
            assert session.status() == SessionStatus.CLOSED; assert service.purgeExpiredReconnects(sessions) == 1;
            try {
                sessions.resume(session.sessionId().toString(), session.resumeSecret(), ConnectionId.create());
                throw new AssertionError("Expired session resumed");
            } catch (civilization.server.session.SessionException expected) {
                assert expected.code().equals("RESUME_WINDOW_EXPIRED");
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static RawClient rawHello(int port, JsonMessageSerializer serializer, String username) throws Exception {
        Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(2_000);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream()); DataInputStream input = new DataInputStream(socket.getInputStream());
        TcpFrameCodec.write(output, serializer.serialize(new HelloRequest(MessageMetadata.createNow(), username)), TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
        WelcomeResponse response = (WelcomeResponse) serializer.deserialize(TcpFrameCodec.read(input, TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES));
        return new RawClient(socket, response);
    }

    // PHASE 3 - Added in Phase 3
    private static void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        // PHASE 3 - Added in Phase 3
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Timed out waiting for " + description);
            Thread.sleep(10L);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static final class Capture implements ClientMessageListener {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<PushMessage> pushes = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        @Override public void onPush(PushMessage message) { pushes.add(message); }
        // PHASE 3 - Added in Phase 3
        @Override public void onConnectionStateChanged(NetworkConnectionState state, Throwable cause) {}
        // PHASE 3 - Added in Phase 3
        private boolean hasStatus(String playerId, ConnectionStatus status) { return pushes.stream().anyMatch(push -> push instanceof ConnectionStatusPush connection && connection.playerId().equals(playerId) && connection.status() == status); }
        // PHASE 3 - Added in Phase 3
        private GameStatePush latestState() { return (GameStatePush) pushes.stream().filter(GameStatePush.class::isInstance).reduce((first, second) -> second).orElse(null); }
    }

    // PHASE 3 - Added in Phase 3
    private record ConnectedClient(NetworkManager manager, Capture capture, WelcomeResponse welcome,
                                   ClientSession session, ResponseMessage response) {}
    // PHASE 3 - Added in Phase 3
    private record RawClient(Socket socket, WelcomeResponse welcome) {}

    // PHASE 3 - Added in Phase 3
    private static final class NetworkFixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final JsonMessageSerializer serializer = new JsonMessageSerializer();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry();
        // PHASE 3 - Added in Phase 3
        private final LobbyServerNetworkAdapter adapter = new LobbyServerNetworkAdapter(sessions, serializer);
        // PHASE 3 - Added in Phase 3
        private final TcpServer server = new TcpServer(serializer, adapter);
        // PHASE 3 - Added in Phase 3
        private final List<NetworkManager> clients = new CopyOnWriteArrayList<>();

        // PHASE 3 - Added in Phase 3
        private NetworkFixture() throws Exception {
            adapter.startHeartbeat(0, Duration.ofMillis(100), Duration.ofMillis(100), Duration.ofMillis(20)); server.start(0);
        }

        // PHASE 3 - Added in Phase 3
        private ConnectedClient connect(String username) throws Exception {
            NetworkManager manager = new NetworkManager(serializer); Capture capture = new Capture(); manager.addListener(capture); clients.add(manager);
            manager.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
            WelcomeResponse welcome = (WelcomeResponse) manager.send(new HelloRequest(MessageMetadata.createNow(), username)).get(2, TimeUnit.SECONDS);
            return new ConnectedClient(manager, capture, welcome, sessions.find(SessionId.parse(welcome.sessionId())).orElseThrow(), welcome);
        }

        // PHASE 3 - Added in Phase 3
        private ConnectedClient resume(WelcomeResponse original) throws Exception {
            NetworkManager manager = new NetworkManager(serializer); Capture capture = new Capture(); manager.addListener(capture); clients.add(manager);
            ResponseMessage response = manager.reconnect("127.0.0.1", server.localPort(), original.sessionId(), original.resumeSecret()).get(2, TimeUnit.SECONDS);
            ClientSession session = sessions.find(SessionId.parse(original.sessionId())).orElseThrow();
            return new ConnectedClient(manager, capture, original, session, response);
        }

        // PHASE 3 - Added in Phase 3
        private String startMatch(ClientSession host, ClientSession guest) {
            String lobby = adapter.lobbyService().create(host).join().view().lobbyId().toString();
            adapter.lobbyService().join(guest, lobby).join(); adapter.lobbyService().selectMap(host, lobby, "emerald-plains").join();
            adapter.lobbyService().ready(host, lobby, true).join(); adapter.lobbyService().ready(guest, lobby, true).join();
            return adapter.lobbyService().start(host, lobby).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private boolean connected(String matchId, ClientSession session) {
            return adapter.lobbyService().match(matchId).project(state -> state.connectedPlayers().contains(session.playerId())).join();
        }

        // PHASE 3 - Added in Phase 3
        @Override public void close() {
            clients.forEach(NetworkManager::close);
            try { server.closeAndAwait(Duration.ofSeconds(2)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            adapter.close();
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
