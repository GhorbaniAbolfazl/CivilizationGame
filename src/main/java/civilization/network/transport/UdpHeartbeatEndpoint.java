package civilization.network.transport;

import civilization.protocol.HeartbeatMessage;
import civilization.protocol.ProtocolMessage;
import civilization.protocol.json.MessageSerializer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class UdpHeartbeatEndpoint implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final int MAX_DATAGRAM_BYTES = 4096;

    // PHASE 3 - Added in Phase 3
    private final MessageSerializer serializer;
    // PHASE 3 - Added in Phase 3
    private final HeartbeatListener listener;
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean running = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean started = new AtomicBoolean();
    // PHASE 3 - Added in Phase 3
    private volatile DatagramSocket socket;
    // PHASE 3 - Added in Phase 3
    private volatile Thread receiverThread;

    // PHASE 3 - Added in Phase 3
    public UdpHeartbeatEndpoint(MessageSerializer serializer, HeartbeatListener listener) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    // PHASE 3 - Added in Phase 3
    public synchronized void start(int port) throws SocketException {
        if (started.get()) throw new IllegalStateException("UDP endpoint is already started");
        DatagramSocket created = new DatagramSocket(port);
        socket = created;
        started.set(true);
        running.set(true);
        receiverThread = new Thread(this::receiveLoop, "phase3-udp-heartbeat");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    // PHASE 3 - Added in Phase 3
    public int localPort() {
        DatagramSocket current = socket;
        if (current == null || !running.get()) {
            throw new IllegalStateException("UDP endpoint is not running");
        }
        return current.getLocalPort();
    }

    // PHASE 3 - Added in Phase 3
    public void send(HeartbeatMessage message, SocketAddress remoteAddress) throws IOException {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        DatagramSocket current = socket;
        if (current == null || !running.get()) {
            throw new IllegalStateException("UDP endpoint is not running");
        }
        byte[] bytes = serializer.serialize(message).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_DATAGRAM_BYTES) {
            throw new IOException("Invalid UDP heartbeat size: " + bytes.length);
        }
        current.send(new DatagramPacket(bytes, bytes.length, remoteAddress));
    }

    // PHASE 3 - Added in Phase 3
    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        // PHASE 3 - Added in Phase 3
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                String json = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8);
                ProtocolMessage decoded = serializer.deserialize(json);
                if (decoded instanceof HeartbeatMessage heartbeat) {
                    listener.onHeartbeat(heartbeat, packet.getSocketAddress());
                }
            } catch (SocketException closed) {
                if (running.get()) notifyFailure(closed);
                return;
            } catch (IOException | RuntimeException failure) {
                if (running.get()) notifyFailure(failure);
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private void notifyFailure(Throwable failure) {
        try {
            listener.onTransportFailure(failure);
        } catch (RuntimeException ignored) {
            // A consumer callback cannot terminate the UDP receiver unexpectedly.
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean closeAndAwait(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        close();
        Thread receiver = receiverThread;
        if (receiver == null || receiver == Thread.currentThread()) return true;
        long deadline = System.nanoTime() + timeout.toNanos();
        // PHASE 3 - Added in Phase 3
        while (receiver.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return false;
            long millis = Math.max(1L, Math.min(
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining), 100L));
            receiver.join(millis);
        }
        return true;
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public synchronized void close() {
        if (!running.compareAndSet(true, false)) return;
        DatagramSocket current = socket;
        if (current != null) current.close();
        Thread receiver = receiverThread;
        if (receiver != null && receiver != Thread.currentThread()) receiver.interrupt();
    }
}
