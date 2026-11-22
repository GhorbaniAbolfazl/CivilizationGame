package civilization.server.lobby;

import civilization.network.transport.ConnectionId;
import civilization.network.transport.ServerConnectionListener;
import civilization.network.transport.TcpMessageConnection;
import civilization.protocol.ErrorResponse;
import civilization.protocol.DisconnectRequest;
import civilization.protocol.HelloRequest;
import civilization.protocol.ResumeSessionRequest;
import civilization.protocol.ResumeAcceptedResponse;
import civilization.protocol.HeartbeatPong;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ProtocolMessage;
import civilization.protocol.RequestMessage;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.MessageSerializer;
import civilization.server.net.ServerHeartbeatMonitor;
import civilization.server.dispatch.RequestDispatcher;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import java.util.Objects;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class LobbyServerNetworkAdapter implements ServerConnectionListener, AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_AUTHENTICATION_TIMEOUT = Duration.ofSeconds(5);
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_HEARTBEAT_SUSPECT_AFTER = Duration.ofSeconds(5);
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_TCP_PROBE_TIMEOUT = Duration.ofSeconds(2);
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_HEARTBEAT_SCAN_INTERVAL = Duration.ofSeconds(1);
    // PHASE 3 - Added in Phase 3
    private final SessionRegistry sessions;
    // PHASE 3 - Added in Phase 3
    private final RequestDispatcher dispatcher;
    // PHASE 3 - Added in Phase 3
    private final LobbyServerService lobbies;
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<ConnectionId, TcpMessageConnection> connections = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<ConnectionId, SessionId> authenticated = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final MessageSerializer serializer;
    // PHASE 3 - Added in Phase 3
    private final Duration authenticationTimeout;
    // PHASE 3 - Added in Phase 3
    private final ScheduledExecutorService authenticationScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "phase3-authentication-timeout");
                thread.setDaemon(true);
                return thread;
            });
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<ConnectionId, ScheduledFuture<?>> authenticationDeadlines =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean closed = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private volatile ServerHeartbeatMonitor heartbeatMonitor;

    // PHASE 3 - Added in Phase 3
    public LobbyServerNetworkAdapter(SessionRegistry sessions) {
        this(sessions, new JsonMessageSerializer(), DEFAULT_AUTHENTICATION_TIMEOUT);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyServerNetworkAdapter(SessionRegistry sessions, MessageSerializer serializer) {
        this(sessions, serializer, DEFAULT_AUTHENTICATION_TIMEOUT);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyServerNetworkAdapter(SessionRegistry sessions, MessageSerializer serializer,
                                     Duration authenticationTimeout) {
        this.sessions = Objects.requireNonNull(sessions); this.serializer = Objects.requireNonNull(serializer);
        this.authenticationTimeout = Objects.requireNonNull(authenticationTimeout, "authenticationTimeout");
        if (authenticationTimeout.isZero() || authenticationTimeout.isNegative()) {
            throw new IllegalArgumentException("Authentication timeout must be positive");
        }
        dispatcher = new RequestDispatcher(sessions);
        lobbies = new LobbyServerService(this::push); new LobbyRequestHandlers(lobbies).registerWith(dispatcher);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyServerService lobbyService() { return lobbies; }
    // PHASE 3 - Added in Phase 3
    public int connectionCount() { return connections.size(); }
    // PHASE 3 - Added in Phase 3
    public int authenticatedConnectionCount() { return authenticated.size(); }
    // PHASE 3 - Added in Phase 3
    public int trackedHeartbeatCount() { ServerHeartbeatMonitor monitor = heartbeatMonitor; return monitor == null ? 0 : monitor.trackedSessionCount(); }
    // PHASE 3 - Added in Phase 3
    @Override public void onConnected(TcpMessageConnection connection) {
        connections.put(connection.connectionId(), connection);
        // PHASE 3 - Added in Phase 3
        try {
            connection.setReadTimeout(authenticationTimeout);
            authenticationDeadlines.put(connection.connectionId(),
                    authenticationScheduler.schedule(() -> {
                        // PHASE 3 - Added in Phase 3
                        if (!authenticated.containsKey(connection.connectionId())) {
                            connection.close();
                        }
                    }, authenticationTimeout.toNanos(), TimeUnit.NANOSECONDS));
        } catch (java.net.SocketException | RuntimeException failure) {
            connection.close();
        }
    }
    // PHASE 3 - Added in Phase 3
    @Override public void onMessage(TcpMessageConnection connection, ProtocolMessage message) {
        SessionId sessionId = authenticated.get(connection.connectionId());
        // PHASE 3 - Added in Phase 3
        if (sessionId == null) { authenticate(connection, message); return; }
        // PHASE 3 - Added in Phase 3
        if (message instanceof DisconnectRequest) { connection.close(); return; }
        // PHASE 3 - Added in Phase 3
        if (message instanceof HeartbeatPong pong) {
            ServerHeartbeatMonitor monitor = heartbeatMonitor;
            if (monitor != null) monitor.acceptTcpPong(sessionId, pong);
            return;
        }
        if (!(message instanceof RequestMessage request)) return;
        dispatcher.dispatch(sessionId, request).whenComplete((response, failure) -> {
            if (!connection.isOpen()) return;
            // PHASE 3 - Added in Phase 3
            if (failure == null) sendSafely(connection, response);
            else sendSafely(connection, new ErrorResponse(MessageMetadata.createNow(), request.metadata().messageId(), "REQUEST_REJECTED", failure.getMessage() == null ? "Request rejected" : failure.getMessage(), false));
        });
    }
    // PHASE 3 - Added in Phase 3
    private void authenticate(TcpMessageConnection connection, ProtocolMessage message) {
        if (message instanceof HelloRequest hello) { authenticateNew(connection, hello); return; }
        if (message instanceof ResumeSessionRequest resume) { authenticateResume(connection, resume); return; }
        rejectAuthentication(connection, message.metadata().messageId(),
                new IllegalArgumentException("Authentication message required"));
    }
    // PHASE 3 - Added in Phase 3
    private void authenticateNew(TcpMessageConnection connection, HelloRequest hello) {
        ClientSession session;
        try {
            session = sessions.create(hello.username()); session.bindConnection(connection.connectionId());
            authenticated.put(connection.connectionId(), session.sessionId());
            cancelAuthenticationDeadline(connection.connectionId());
            connection.setReadTimeout(Duration.ZERO); registerHeartbeat(session.sessionId());
        }
        catch (RuntimeException | java.io.IOException failure) { rejectAuthentication(connection, hello.metadata().messageId(), failure); return; }
        sendSafely(connection, new WelcomeResponse(MessageMetadata.createNow(), hello.metadata().messageId(), session.sessionId().toString(), session.playerId().toString(), session.resumeSecret(), heartbeatPort()));
    }
    // PHASE 3 - Added in Phase 3
    private void authenticateResume(TcpMessageConnection connection, ResumeSessionRequest resume) {
        ClientSession session;
        try {
            session = sessions.resume(resume.sessionId(), resume.resumeSecret(), connection.connectionId());
            authenticated.put(connection.connectionId(), session.sessionId());
            cancelAuthenticationDeadline(connection.connectionId());
            connection.setReadTimeout(Duration.ZERO); registerHeartbeat(session.sessionId());
        }
        catch (RuntimeException | java.io.IOException failure) { rejectAuthentication(connection, resume.metadata().messageId(), failure); return; }
        lobbies.reconnect(session).whenComplete((stateVersion, failure) -> {
            if (failure != null) { connection.close(); return; }
            sendSafely(connection, new ResumeAcceptedResponse(MessageMetadata.createNow(), resume.metadata().messageId(), session.sessionId().toString(), session.playerId().toString(), heartbeatPort(), stateVersion));
        });
    }
    // PHASE 3 - Added in Phase 3
    private void rejectAuthentication(TcpMessageConnection connection, java.util.UUID replyTo, Throwable failure) {
        String code = failure instanceof civilization.server.session.SessionException sessionFailure ? sessionFailure.code() : "CONNECTION_REJECTED";
        // PHASE 3 - Added in Phase 3
        try {
            connection.sendAndClose(new ErrorResponse(MessageMetadata.createNow(), replyTo, code,
                    failure.getMessage() == null ? code : failure.getMessage(), false));
        } catch (RuntimeException ignored) {
            connection.close();
        }
    }
    // PHASE 3 - Added in Phase 3
    @Override public void onDisconnected(TcpMessageConnection connection, Throwable cause) {
        cancelAuthenticationDeadline(connection.connectionId());
        connections.remove(connection.connectionId(), connection); SessionId sessionId = authenticated.remove(connection.connectionId());
        // PHASE 3 - Added in Phase 3
        if (sessionId != null) {
            ServerHeartbeatMonitor monitor = heartbeatMonitor; if (monitor != null) monitor.unregister(sessionId);
            if (sessions.disconnect(sessionId, connection.connectionId()) && !closed.get()) lobbies.disconnect(sessionId).exceptionally(failure -> null);
        }
    }
    // PHASE 3 - Added in Phase 3
    private void push(SessionId recipient, civilization.protocol.PushMessage message) {
        ClientSession session = sessions.find(recipient).orElseThrow(() -> new IllegalStateException("Unknown push recipient"));
        TcpMessageConnection connection = session.activeConnection().map(connections::get).orElse(null);
        if (connection == null || !connection.isOpen()) throw new IllegalStateException("Recipient is disconnected");
        connection.send(message);
    }
    // PHASE 3 - Added in Phase 3
    public synchronized int startHeartbeat(int port) throws java.io.IOException {
        return startHeartbeat(port, DEFAULT_HEARTBEAT_SUSPECT_AFTER, DEFAULT_TCP_PROBE_TIMEOUT, DEFAULT_HEARTBEAT_SCAN_INTERVAL);
    }
    // PHASE 3 - Added in Phase 3
    public synchronized int startHeartbeat(int port, Duration suspectAfter, Duration probeTimeout,
                                           Duration scanInterval) throws java.io.IOException {
        if (heartbeatMonitor != null) throw new IllegalStateException("Heartbeat monitor is already running");
        ServerHeartbeatMonitor monitor = new ServerHeartbeatMonitor(serializer, this::connectionFor,
                () -> {
                    // PHASE 3 - Added in Phase 3
                    sessions.expireReconnectWindows();
                    lobbies.purgeExpiredReconnects(sessions);
                    sessions.purgeClosedSessions();
                    dispatcher.purgeSecurityStateForMissingSessions();
                }, suspectAfter, probeTimeout);
        // PHASE 3 - Added in Phase 3
        try { monitor.start(port, scanInterval); }
        catch (java.io.IOException | RuntimeException failure) { monitor.close(); throw failure; }
        heartbeatMonitor = monitor;
        // PHASE 3 - Added in Phase 3
        authenticated.values().forEach(monitor::register);
        return monitor.localPort();
    }
    // PHASE 3 - Added in Phase 3
    private TcpMessageConnection connectionFor(SessionId sessionId) {
        return sessions.find(sessionId).flatMap(ClientSession::activeConnection).map(connections::get).orElse(null);
    }
    // PHASE 3 - Added in Phase 3
    private int heartbeatPort() { ServerHeartbeatMonitor monitor = heartbeatMonitor; return monitor == null ? 0 : monitor.localPort(); }
    // PHASE 3 - Added in Phase 3
    private void registerHeartbeat(SessionId sessionId) { ServerHeartbeatMonitor monitor = heartbeatMonitor; if (monitor != null) monitor.register(sessionId); }
    // PHASE 3 - Added in Phase 3
    private void cancelAuthenticationDeadline(ConnectionId connectionId) {
        ScheduledFuture<?> deadline = authenticationDeadlines.remove(connectionId);
        if (deadline != null) deadline.cancel(false);
    }
    // PHASE 3 - Added in Phase 3
    private static void sendSafely(TcpMessageConnection connection, ProtocolMessage message) {
        try { if (connection.isOpen()) connection.send(message); } catch (RuntimeException ignored) { connection.close(); }
    }
    // PHASE 3 - Added in Phase 3
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ServerHeartbeatMonitor monitor = heartbeatMonitor; heartbeatMonitor = null; if (monitor != null) monitor.close();
        connections.values().forEach(TcpMessageConnection::close); connections.clear(); authenticated.clear(); lobbies.close();
        // PHASE 3 - Added in Phase 3
        authenticationDeadlines.values().forEach(deadline -> deadline.cancel(false));
        authenticationDeadlines.clear(); authenticationScheduler.shutdownNow();
        // PHASE 3 - Added in Phase 3
        dispatcher.clearSecurityState();
    }
}
