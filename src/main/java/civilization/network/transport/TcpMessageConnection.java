package civilization.network.transport;

import civilization.protocol.ProtocolMessage;
import civilization.protocol.json.MessageSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class TcpMessageConnection implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final ConnectionId connectionId = ConnectionId.create();
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_OUTBOUND_CAPACITY = 256;

    // PHASE 3 - Added in Phase 3
    private final Socket socket;
    // PHASE 3 - Added in Phase 3
    private final MessageSerializer serializer;
    // PHASE 3 - Added in Phase 3
    private final int maxFrameBytes;
    // PHASE 3 - Added in Phase 3
    private final BlockingQueue<ProtocolMessage> outbound;
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean open = new AtomicBoolean(true);
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean started = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean closeAfterFlush = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private volatile Thread readerThread;
    // PHASE 3 - Added in Phase 3
    private volatile Thread writerThread;
    // PHASE 3 - Added in Phase 3
    private volatile ConnectionListener listener;

    // PHASE 3 - Added in Phase 3
    public TcpMessageConnection(Socket socket, MessageSerializer serializer) throws IOException {
        this(socket, serializer, TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES,
                DEFAULT_OUTBOUND_CAPACITY);
    }

    // PHASE 3 - Added in Phase 3
    public TcpMessageConnection(
            Socket socket,
            MessageSerializer serializer,
            int maxFrameBytes,
            int outboundCapacity) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        if (!socket.isConnected() || socket.isClosed()) {
            throw new IOException("TCP socket must be connected and open");
        }
        if (maxFrameBytes <= 0 || outboundCapacity <= 0) {
            throw new IllegalArgumentException("Transport limits must be positive");
        }
        this.maxFrameBytes = maxFrameBytes;
        this.outbound = new ArrayBlockingQueue<>(outboundCapacity);
    }

    // PHASE 3 - Added in Phase 3
    public void start(ConnectionListener connectionListener) {
        Objects.requireNonNull(connectionListener, "connectionListener");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("TCP connection is already started");
        }
        listener = connectionListener;
        readerThread = new Thread(this::readLoop,
                "phase3-tcp-reader-" + socket.getRemoteSocketAddress());
        writerThread = new Thread(this::writeLoop,
                "phase3-tcp-writer-" + socket.getRemoteSocketAddress());
        readerThread.setDaemon(true);
        writerThread.setDaemon(true);
        readerThread.start();
        writerThread.start();
    }

    // PHASE 3 - Added in Phase 3
    public void send(ProtocolMessage message) {
        Objects.requireNonNull(message, "message");
        // PHASE 3 - Added in Phase 3
        if (!open.get() || closeAfterFlush.get()) {
            throw new IllegalStateException("TCP connection is closed");
        }
        if (!outbound.offer(message)) {
            closeWithCause(new IOException("TCP outbound queue is full"));
            throw new IllegalStateException("TCP outbound queue is full");
        }
    }

    // PHASE 3 - Added in Phase 3
    public void sendAndClose(ProtocolMessage message) {
        Objects.requireNonNull(message, "message");
        if (!open.get() || !closeAfterFlush.compareAndSet(false, true)) {
            throw new IllegalStateException("TCP connection is closed");
        }
        if (!outbound.offer(message)) {
            closeWithCause(new IOException("TCP outbound queue is full"));
            throw new IllegalStateException("TCP outbound queue is full");
        }
    }

    // PHASE 3 - Added in Phase 3
    public void setReadTimeout(Duration timeout) throws SocketException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        long milliseconds = timeout.toMillis();
        if (!timeout.isZero() && milliseconds == 0L) milliseconds = 1L;
        socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, milliseconds));
    }

    // PHASE 3 - Added in Phase 3
    public boolean isOpen() {
        return open.get();
    }

    // PHASE 3 - Added in Phase 3
    public ConnectionId connectionId() {
        return connectionId;
    }

    // PHASE 3 - Added in Phase 3
    public java.net.SocketAddress remoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    // PHASE 3 - Added in Phase 3
    private void readLoop() {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()))) {
            // PHASE 3 - Added in Phase 3
            while (open.get()) {
                String json = TcpFrameCodec.read(input, maxFrameBytes);
                ProtocolMessage message = serializer.deserialize(json);
                listener.onMessage(message);
            }
        } catch (EOFException | SocketException disconnected) {
            closeWithCause(disconnected);
        } catch (IOException | RuntimeException failure) {
            closeWithCause(failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private void writeLoop() {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream()))) {
            // PHASE 3 - Added in Phase 3
            while (open.get()) {
                ProtocolMessage message = outbound.take();
                TcpFrameCodec.write(output, serializer.serialize(message), maxFrameBytes);
                // PHASE 3 - Added in Phase 3
                if (closeAfterFlush.get() && outbound.isEmpty()) {
                    closeWithCause(new IOException("TCP connection closed after final response"));
                    return;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closeWithCause(interrupted);
        } catch (IOException | RuntimeException failure) {
            closeWithCause(failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private void closeWithCause(Throwable cause) {
        if (!open.compareAndSet(true, false)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
            // PHASE 3 - Added in Phase 3
            // The original disconnect cause remains the useful diagnostic.
        }
        Thread reader = readerThread;
        Thread writer = writerThread;
        if (reader != null && reader != Thread.currentThread()) reader.interrupt();
        if (writer != null && writer != Thread.currentThread()) writer.interrupt();
        ConnectionListener currentListener = listener;
        // PHASE 3 - Added in Phase 3
        if (currentListener != null) {
            try {
                currentListener.onDisconnected(cause);
            } catch (RuntimeException ignored) {
                // A consumer callback cannot prevent transport cleanup.
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean awaitClosed(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        long deadline = System.nanoTime() + timeout.toNanos();
        // PHASE 3 - Added in Phase 3
        if (!joinUntil(readerThread, deadline)) return false;
        return joinUntil(writerThread, deadline);
    }

    // PHASE 3 - Added in Phase 3
    public boolean closeAndAwait(Duration timeout) throws InterruptedException {
        close();
        return awaitClosed(timeout);
    }

    // PHASE 3 - Added in Phase 3
    private static boolean joinUntil(Thread thread, long deadlineNanos)
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

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        closeWithCause(new IOException("TCP connection closed"));
    }
}
