package civilization.network.transport;

import civilization.protocol.ProtocolMessage;
import civilization.protocol.json.MessageSerializer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class TcpServer implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_MAX_CONNECTIONS = 128;
    // PHASE 3 - Added in Phase 3
    private final MessageSerializer serializer;
    // PHASE 3 - Added in Phase 3
    private final ServerConnectionListener listener;
    // PHASE 3 - Added in Phase 3
    private final int maxConnections;
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<ConnectionId, TcpMessageConnection> connections =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean running = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private volatile ServerSocket serverSocket;
    // PHASE 3 - Added in Phase 3
    private volatile Thread acceptThread;

    // PHASE 3 - Added in Phase 3
    public TcpServer(MessageSerializer serializer, ServerConnectionListener listener) {
        this(serializer, listener, DEFAULT_MAX_CONNECTIONS);
    }

    // PHASE 3 - Added in Phase 3
    public TcpServer(MessageSerializer serializer, ServerConnectionListener listener,
                     int maxConnections) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.listener = Objects.requireNonNull(listener, "listener");
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Maximum connections must be positive");
        }
        this.maxConnections = maxConnections;
    }

    // PHASE 3 - Added in Phase 3
    public synchronized void start(int port) throws IOException {
        if (running.get()) throw new IllegalStateException("TCP server is already running");
        serverSocket = new ServerSocket(port);
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "phase3-tcp-acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    // PHASE 3 - Added in Phase 3
    public int localPort() {
        ServerSocket current = serverSocket;
        if (current == null || !running.get()) {
            throw new IllegalStateException("TCP server is not running");
        }
        return current.getLocalPort();
    }

    // PHASE 3 - Added in Phase 3
    public int connectionCount() {
        return connections.size();
    }

    // PHASE 3 - Added in Phase 3
    public Optional<TcpMessageConnection> findConnection(ConnectionId connectionId) {
        return Optional.ofNullable(connections.get(
                Objects.requireNonNull(connectionId, "connectionId")));
    }

    // PHASE 3 - Added in Phase 3
    public Map<ConnectionId, TcpMessageConnection> connectionSnapshot() {
        return Map.copyOf(connections);
    }

    // PHASE 3 - Added in Phase 3
    private void acceptLoop() {
        // PHASE 3 - Added in Phase 3
        while (running.get()) {
            Socket acceptedSocket = null;
            try {
                acceptedSocket = serverSocket.accept();
                // PHASE 3 - Added in Phase 3
                if (!running.get()) {
                    acceptedSocket.close();
                    return;
                }
                // PHASE 3 - Added in Phase 3
                if (connections.size() >= maxConnections) {
                    acceptedSocket.close();
                    acceptedSocket = null;
                    continue;
                }
                acceptedSocket.setTcpNoDelay(true);
                TcpMessageConnection connection =
                        new TcpMessageConnection(acceptedSocket, serializer);
                acceptedSocket = null;
                connections.put(connection.connectionId(), connection);
                // PHASE 3 - Added in Phase 3
                if (!running.get()) {
                    connections.remove(connection.connectionId(), connection);
                    connection.close();
                    return;
                }
                connection.start(new ConnectionListener() {
                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onMessage(ProtocolMessage message) {
                        listener.onMessage(connection, message);
                    }

                    // PHASE 3 - Added in Phase 3
                    @Override
                    public void onDisconnected(Throwable cause) {
                        connections.remove(connection.connectionId(), connection);
                        listener.onDisconnected(connection, cause);
                    }
                });
                // PHASE 3 - Added in Phase 3
                try {
                    listener.onConnected(connection);
                } catch (RuntimeException consumerFailure) {
                    connection.close();
                }
            } catch (SocketException closed) {
                if (running.get()) close();
            } catch (IOException failure) {
                if (running.get()) close();
            } finally {
                // PHASE 3 - Added in Phase 3
                if (acceptedSocket != null) {
                    try {
                        acceptedSocket.close();
                    } catch (IOException ignored) {
                        // The accept/setup failure remains the useful diagnostic.
                    }
                }
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean closeAndAwait(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        List<TcpMessageConnection> closingConnections = initiateShutdown();
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean stopped = awaitThread(acceptThread, deadline);
        // PHASE 3 - Added in Phase 3
        for (TcpMessageConnection connection : closingConnections) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return false;
            stopped &= connection.awaitClosed(Duration.ofNanos(remaining));
        }
        return stopped;
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        initiateShutdown();
    }

    // PHASE 3 - Added in Phase 3
    private synchronized List<TcpMessageConnection> initiateShutdown() {
        List<TcpMessageConnection> closingConnections = List.copyOf(connections.values());
        if (!running.compareAndSet(true, false)) return closingConnections;
        ServerSocket current = serverSocket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // PHASE 3 - Added in Phase 3
                // Shutdown remains idempotent if the listening socket already failed.
            }
        }
        // PHASE 3 - Added in Phase 3
        for (TcpMessageConnection connection : closingConnections) connection.close();
        Thread currentAcceptThread = acceptThread;
        if (currentAcceptThread != null && currentAcceptThread != Thread.currentThread()) {
            currentAcceptThread.interrupt();
        }
        return closingConnections;
    }

    // PHASE 3 - Added in Phase 3
    private static boolean awaitThread(Thread thread, long deadlineNanos)
            throws InterruptedException {
        if (thread == null || thread == Thread.currentThread()) return true;
        // PHASE 3 - Added in Phase 3
        while (thread.isAlive()) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L) return false;
            long millis = Math.max(1L, Math.min(
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining), 100L));
            thread.join(millis);
        }
        return true;
    }
}
