package civilization.client.net;

import civilization.client.ClientCommandPort;
import civilization.network.transport.ConnectionListener;
import civilization.network.transport.HeartbeatListener;
import civilization.network.transport.TcpMessageConnection;
import civilization.network.transport.UdpHeartbeatEndpoint;
import civilization.protocol.HeartbeatMessage;
import civilization.protocol.HeartbeatPing;
import civilization.protocol.HeartbeatPong;
import civilization.protocol.ResumeSessionRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ProtocolMessage;
import civilization.protocol.PushMessage;
import civilization.protocol.RequestMessage;
import civilization.protocol.ResponseMessage;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.MessageSerializer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// PHASE 3 - Added in Phase 3
public final class NetworkManager implements ClientCommandPort, AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    // PHASE 3 - Added in Phase 3
    private final MessageSerializer serializer;
    // PHASE 3 - Added in Phase 3
    private final Duration connectTimeout;
    // PHASE 3 - Added in Phase 3
    private final Duration requestTimeout;
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<java.util.UUID, CompletableFuture<ResponseMessage>> pending =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<ClientMessageListener> listeners =
            new CopyOnWriteArrayList<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<NetworkConnectionState> state =
            new AtomicReference<>(NetworkConnectionState.DISCONNECTED);
    // PHASE 3 - Added in Phase 3
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "phase3-client-network-worker");
                thread.setDaemon(true);
                return thread;
            });
    // PHASE 3 - Added in Phase 3
    private final AtomicLong heartbeatSequence = new AtomicLong();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<TcpMessageConnection> tcpConnection = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private volatile UdpHeartbeatEndpoint udpHeartbeat;
    // PHASE 3 - Added in Phase 3
    private volatile ScheduledFuture<?> heartbeatTask;

    // PHASE 3 - Added in Phase 3
    public NetworkManager() {
        this(new JsonMessageSerializer());
    }

    // PHASE 3 - Added in Phase 3
    public NetworkManager(MessageSerializer serializer) {
        this(serializer, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    // PHASE 3 - Added in Phase 3
    public NetworkManager(
            MessageSerializer serializer, Duration connectTimeout, Duration requestTimeout) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> connect(String host, int port) {
        return connect(host, port, connectTimeout);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<Void> connect(String host, int port, Duration timeout) {
        Objects.requireNonNull(host, "host");
        Duration trustedTimeout = requirePositive(timeout, "timeout");
        if (port < 1 || port > 65_535) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("TCP port must be between 1 and 65535"));
        }
        if (!state.compareAndSet(NetworkConnectionState.DISCONNECTED,
                NetworkConnectionState.CONNECTING)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Network manager is not disconnected"));
        }
        publishConnectionState(NetworkConnectionState.CONNECTING, null);
        return CompletableFuture.runAsync(() -> openTcp(host, port, trustedTimeout), worker);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> reconnect(String host, int port, String sessionId,
                                                         String resumeSecret) {
        Objects.requireNonNull(sessionId, "sessionId"); Objects.requireNonNull(resumeSecret, "resumeSecret");
        return connect(host, port).thenCompose(ignored -> send(new ResumeSessionRequest(
                MessageMetadata.createNow(), sessionId, resumeSecret)));
    }

    // PHASE 3 - Added in Phase 3
    private void openTcp(String host, int port, Duration timeout) {
        Socket socket = new Socket();
        TcpMessageConnection established = null;
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis(timeout));
            socket.setTcpNoDelay(true);
            established = new TcpMessageConnection(socket, serializer);
            TcpMessageConnection connection = established;
            if (!tcpConnection.compareAndSet(null, connection)) {
                throw new IllegalStateException("A TCP connection is already installed");
            }
            connection.start(new ConnectionListener() {
                // PHASE 3 - Added in Phase 3
                @Override
                public void onMessage(ProtocolMessage message) {
                    receive(message);
                }

                // PHASE 3 - Added in Phase 3
                @Override
                public void onDisconnected(Throwable cause) {
                    handleDisconnect(connection, cause);
                }
            });
            // PHASE 3 - Added in Phase 3
            if (!state.compareAndSet(NetworkConnectionState.CONNECTING,
                    NetworkConnectionState.CONNECTED)) {
                throw new IOException("TCP connection was cancelled before startup completed");
            }
            publishConnectionState(NetworkConnectionState.CONNECTED, null);
        } catch (IOException | RuntimeException failure) {
            // PHASE 3 - Added in Phase 3
            if (established != null) {
                tcpConnection.compareAndSet(established, null);
                established.close();
            } else {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // The connection failure remains the useful diagnostic.
                }
            }
            if (state.compareAndSet(NetworkConnectionState.CONNECTING,
                    NetworkConnectionState.DISCONNECTED)) {
                publishConnectionState(NetworkConnectionState.DISCONNECTED, failure);
            }
            throw new CompletionException(failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public CompletableFuture<ResponseMessage> send(RequestMessage request) {
        return send(request, requestTimeout);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ResponseMessage> send(RequestMessage request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        Duration trustedTimeout = requirePositive(timeout, "timeout");
        TcpMessageConnection connection = tcpConnection.get();
        if (state.get() != NetworkConnectionState.CONNECTED
                || connection == null || !connection.isOpen()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Client is not connected"));
        }
        CompletableFuture<ResponseMessage> response = new CompletableFuture<>();
        CompletableFuture<ResponseMessage> previous = pending.putIfAbsent(
                request.metadata().messageId(), response);
        if (previous != null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Request message ID is already pending"));
        }
        try {
            ScheduledFuture<?> timeoutTask = worker.schedule(() -> {
                // PHASE 3 - Added in Phase 3
                if (pending.remove(request.metadata().messageId(), response)) {
                    response.completeExceptionally(new SocketTimeoutException(
                            "Timed out waiting for response to "
                                    + request.metadata().messageId()));
                }
            }, trustedTimeout.toNanos(), TimeUnit.NANOSECONDS);
            response.whenComplete((ignoredResponse, ignoredFailure) -> timeoutTask.cancel(false));
            connection.send(request);
            return response;
        } catch (RuntimeException failure) {
            pending.remove(request.metadata().messageId(), response);
            response.completeExceptionally(failure);
            return response;
        }
    }

    // PHASE 3 - Added in Phase 3
    public void addListener(ClientMessageListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // PHASE 3 - Added in Phase 3
    public void removeListener(ClientMessageListener listener) {
        listeners.remove(listener);
    }

    // PHASE 3 - Added in Phase 3
    public NetworkConnectionState connectionState() {
        return state.get();
    }

    // PHASE 3 - Added in Phase 3
    public int pendingRequestCount() {
        return pending.size();
    }

    // PHASE 3 - Added in Phase 3
    public synchronized void startHeartbeat(
            InetSocketAddress serverAddress, String sessionId, Duration interval)
            throws IOException {
        Objects.requireNonNull(serverAddress, "serverAddress");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive");
        }
        stopHeartbeat();
        UdpHeartbeatEndpoint endpoint = new UdpHeartbeatEndpoint(serializer,
                new HeartbeatListener() {
                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onHeartbeat(HeartbeatMessage message, SocketAddress remoteAddress) {
                        // UDP heartbeat reception is intentionally non-authoritative.
                    }

                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onTransportFailure(Throwable cause) {
                        // UDP failure does not change TCP session or game correctness.
                    }
        });
        endpoint.start(0);
        udpHeartbeat = endpoint;
        // PHASE 3 - Added in Phase 3
        long intervalMillis = Math.max(1L, interval.toMillis());
        heartbeatTask = worker.scheduleAtFixedRate(() -> {
            try {
                endpoint.send(new HeartbeatPing(MessageMetadata.createNow(), sessionId,
                        heartbeatSequence.getAndIncrement()), serverAddress);
            } catch (IOException | RuntimeException ignored) {
                // PHASE 3 - Added in Phase 3
                // TCP remains the reliable authority and decides disconnect state.
            }
        }, 0L, intervalMillis, TimeUnit.MILLISECONDS);
    }

    // PHASE 3 - Added in Phase 3
    public synchronized void stopHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        heartbeatTask = null;
        if (task != null) task.cancel(true);
        UdpHeartbeatEndpoint endpoint = udpHeartbeat;
        udpHeartbeat = null;
        if (endpoint != null) endpoint.close();
    }

    // PHASE 3 - Added in Phase 3
    private void receive(ProtocolMessage message) {
        if (message instanceof HeartbeatPing ping) {
            TcpMessageConnection connection = tcpConnection.get();
            // PHASE 3 - Added in Phase 3
            if (connection != null && connection.isOpen()) {
                try { connection.send(new HeartbeatPong(MessageMetadata.createNow(), ping.sessionId(), ping.sequence())); }
                catch (RuntimeException ignored) { connection.close(); }
            }
        } else if (message instanceof ResponseMessage response) {
            CompletableFuture<ResponseMessage> future = pending.remove(response.replyTo());
            if (future != null) future.complete(response);
        } else if (message instanceof PushMessage push) {
            // PHASE 3 - Added in Phase 3
            for (ClientMessageListener listener : listeners) {
                try {
                    listener.onPush(push);
                } catch (RuntimeException ignored) {
                    // One consumer cannot terminate the dedicated TCP listener thread.
                }
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private void handleDisconnect(TcpMessageConnection source, Throwable cause) {
        if (!tcpConnection.compareAndSet(source, null)) return;
        // PHASE 3 - Added in Phase 3
        while (true) {
            NetworkConnectionState current = state.get();
            if (current == NetworkConnectionState.CLOSED) return;
            if (state.compareAndSet(current, NetworkConnectionState.DISCONNECTED)) break;
        }
        stopHeartbeat();
        // PHASE 3 - Added in Phase 3
        for (CompletableFuture<ResponseMessage> future : pending.values()) {
            future.completeExceptionally(cause);
        }
        pending.clear();
        publishConnectionState(NetworkConnectionState.DISCONNECTED, cause);
    }

    // PHASE 3 - Added in Phase 3
    private void publishConnectionState(NetworkConnectionState next, Throwable cause) {
        // PHASE 3 - Added in Phase 3
        for (ClientMessageListener listener : listeners) {
            try {
                listener.onConnectionStateChanged(next, cause);
            } catch (RuntimeException ignored) {
                // One consumer cannot corrupt the network lifecycle.
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        NetworkConnectionState previous = state.getAndSet(NetworkConnectionState.CLOSED);
        if (previous == NetworkConnectionState.CLOSED) return;
        stopHeartbeat();
        TcpMessageConnection connection = tcpConnection.getAndSet(null);
        if (connection != null) connection.close();
        IOException closed = new IOException("Network manager closed");
        // PHASE 3 - Added in Phase 3
        for (CompletableFuture<ResponseMessage> future : pending.values()) {
            future.completeExceptionally(closed);
        }
        pending.clear();
        worker.shutdownNow();
        publishConnectionState(NetworkConnectionState.CLOSED, null);
    }

    // PHASE 3 - Added in Phase 3
    public boolean closeAndAwait(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        TcpMessageConnection connection = tcpConnection.get();
        long deadline = System.nanoTime() + timeout.toNanos();
        close();
        boolean connectionClosed = connection == null
                || connection.awaitClosed(Duration.ofNanos(
                        Math.max(0L, deadline - System.nanoTime())));
        long remaining = Math.max(0L, deadline - System.nanoTime());
        boolean workerStopped = worker.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        return connectionClosed && workerStopped;
    }

    // PHASE 3 - Added in Phase 3
    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    // PHASE 3 - Added in Phase 3
    private static int timeoutMillis(Duration timeout) {
        long millis = Math.max(1L, timeout.toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
