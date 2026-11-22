package civilization.server.bootstrap;

import civilization.network.transport.TcpServer;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.MessageSerializer;
import civilization.server.lobby.LobbyServerNetworkAdapter;
import civilization.server.session.SessionRegistry;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

// PHASE 3 - Added in Phase 3
public final class CivilizationServerApplication {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_TCP_PORT = 7777;
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_UDP_PORT = 7778;
    // PHASE 3 - Added in Phase 3
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    // PHASE 3 - Added in Phase 3
    private CivilizationServerApplication() {
    }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        int tcpPort = parsePort(args, 0, DEFAULT_TCP_PORT, "TCP");
        int udpPort = parsePort(args, 1, DEFAULT_UDP_PORT, "UDP");
        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: CivilizationServerApplication [tcp-port] [udp-port]");
        }

        MessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        CountDownLatch shutdownRequested = new CountDownLatch(1);
        // PHASE 3 - Added in Phase 3
        try (LobbyServerNetworkAdapter adapter =
                     new LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter)) {
            int boundUdpPort = adapter.startHeartbeat(udpPort);
            server.start(tcpPort);
            Thread shutdownHook = new Thread(
                    () -> requestShutdown(server, adapter, shutdownRequested),
                    "phase3-server-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            System.out.printf("Civilization server listening on TCP %d and UDP %d%n",
                    server.localPort(), boundUdpPort);
            // PHASE 3 - Added in Phase 3
            try {
                shutdownRequested.await();
            } finally {
                // PHASE 3 - Added in Phase 3
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown is already executing the hook.
                }
                server.closeAndAwait(SHUTDOWN_TIMEOUT);
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void requestShutdown(TcpServer server,
                                        LobbyServerNetworkAdapter adapter,
                                        CountDownLatch shutdownRequested) {
        server.close();
        adapter.close();
        shutdownRequested.countDown();
    }

    // PHASE 3 - Added in Phase 3
    private static int parsePort(String[] args, int index, int fallback, String name) {
        if (args.length <= index) return fallback;
        int port;
        // PHASE 3 - Added in Phase 3
        try {
            port = Integer.parseInt(args[index]);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " port must be an integer", failure);
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException(name + " port must be between 0 and 65535");
        }
        return port;
    }
}
