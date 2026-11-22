package civilization.phase3;

import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.net.ClientMessageListener;
import civilization.client.net.NetworkConnectionState;
import civilization.client.net.NetworkManager;
import civilization.client.state.ClientStateStore;
import civilization.network.transport.TcpFrameCodec;
import civilization.network.transport.TcpServer;
import civilization.protocol.ChatMessagePush;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.HelloRequest;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.RequestAcceptedResponse;
import civilization.protocol.SendChatRequest;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.ProtocolException;
import civilization.server.chat.ChatException;
import civilization.server.chat.PublicChatService;
import civilization.server.lobby.LobbyServerNetworkAdapter;
import civilization.server.lobby.LobbyServerService;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.view.game.GameChatPanel;

import javax.swing.SwingUtilities;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

// PHASE 3 - Added in Phase 3
public final class Phase3TextChatTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3TextChatTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        lobbyAndGameChatTravelThroughTcpInRealTime();
        chatIsIndependentOfTurnAndGameplayLane();
        messageValidationAndBoundedHistory();
        clientDispatchesChatToSwingEdt();
        malformedChatClosesOnlyItsConnection();
        System.out.println("Phase3TextChatTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void lobbyAndGameChatTravelThroughTcpInRealTime() throws Exception {
        try (NetworkFixture fixture = new NetworkFixture()) {
            ConnectedClient hostConnection = fixture.connect("Host User");
            ConnectedClient guestConnection = fixture.connect("Guest User");
            ClientSession host = hostConnection.session();
            ClientSession guest = guestConnection.session();
            String lobbyId = fixture.adapter.lobbyService().create(host).join()
                    .view().lobbyId().toString();
            fixture.adapter.lobbyService().join(guest, lobbyId).join();

            Instant before = Instant.now();
            var response = guestConnection.manager().send(new SendChatRequest(
                    MessageMetadata.createNow(), lobbyId, "hello lobby")).get(2, TimeUnit.SECONDS);
            Instant after = Instant.now();
            assert response instanceof RequestAcceptedResponse;
            await(() -> hostConnection.capture().chatCount(lobbyId) == 1
                    && guestConnection.capture().chatCount(lobbyId) == 1, "lobby chat broadcast");
            ChatMessagePush lobbyMessage = hostConnection.capture().latestChat(lobbyId);
            assert lobbyMessage.senderPlayerId().equals(guest.playerId().toString());
            assert lobbyMessage.senderUsername().equals("Guest User");
            assert lobbyMessage.content().equals("hello lobby");
            assert !lobbyMessage.createdAt().isBefore(before)
                    && !lobbyMessage.createdAt().isAfter(after);
            assert hostConnection.capture().listenerThread().startsWith("phase3-tcp-reader-");

            String matchId = fixture.startMatch(host, guest, lobbyId);
            MatchFacts beforeChat = fixture.facts(matchId, guest);
            response = guestConnection.manager().send(new SendChatRequest(
                    MessageMetadata.createNow(), matchId, "out-of-turn game chat"))
                    .get(2, TimeUnit.SECONDS);
            assert response instanceof RequestAcceptedResponse;
            await(() -> hostConnection.capture().chatCount(matchId) == 1
                    && guestConnection.capture().chatCount(matchId) == 1, "game chat broadcast");
            assert fixture.facts(matchId, guest).equals(beforeChat);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void chatIsIndependentOfTurnAndGameplayLane() throws Exception {
        SessionRegistry sessions = new SessionRegistry();
        ClientSession host = sessions.create("Host");
        ClientSession guest = sessions.create("Guest");
        CountDownLatch chatDeliveryEntered = new CountDownLatch(1);
        CountDownLatch releaseChat = new CountDownLatch(1);
        // PHASE 3 - Added in Phase 3
        try (LobbyServerService service = new LobbyServerService((recipient, message) -> {
            if (message instanceof ChatMessagePush) {
                chatDeliveryEntered.countDown();
                try { releaseChat.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            }
        })) {
            String lobby = service.create(host).join().view().lobbyId().toString();
            service.join(guest, lobby).join();
            service.selectMap(host, lobby, "emerald-plains").join();
            service.ready(host, lobby, true).join();
            service.ready(guest, lobby, true).join();
            String match = service.start(host, lobby).join().launch().matchId();
            CompletableFuture<ChatMessagePush> chat = service.chat(
                    guest, match, "still responsive");
            assert chatDeliveryEntered.await(1, TimeUnit.SECONDS);
            // PHASE 3 - Added in Phase 3
            var turn = service.endTurn(host, new EndTurnRequest(
                    MessageMetadata.createNow(), match, 1L)).get(1, TimeUnit.SECONDS);
            assert turn.activePlayerId().equals(guest.playerId());
            releaseChat.countDown();
            assert chat.get(1, TimeUnit.SECONDS).content().equals("still responsive");
        } finally {
            releaseChat.countDown();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void messageValidationAndBoundedHistory() {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SendChatRequest valid = new SendChatRequest(
                MessageMetadata.createNow(), "scope-1", "ok");
        String json = serializer.serialize(valid);
        String oversized = "x".repeat(PublicChatService.MAX_MESSAGE_CHARACTERS + 1);
        expectProtocolFailure(() -> serializer.deserialize(
                json.replace("\"content\":\"ok\"", "\"content\":\"" + oversized + "\"")));
        expectProtocolFailure(() -> serializer.deserialize(
                json.replace("\"content\":\"ok\"", "\"content\":{}")));
        try {
            new SendChatRequest(MessageMetadata.createNow(), "scope-1", " ");
            throw new AssertionError("Blank chat was accepted");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains("1-1000");
        }

        ClientSession sender = new SessionRegistry().create("History User");
        try (PublicChatService chat = new PublicChatService((recipient, message) -> {},
                Clock.fixed(Instant.parse("2030-01-02T03:04:05.123456Z"), ZoneOffset.UTC))) {
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < PublicChatService.MAX_HISTORY_MESSAGES + 5; index++) {
                chat.publish(sender, "scope-1", "message-" + index,
                        List.of(sender.sessionId())).join();
            }
            List<ChatMessagePush> history = chat.history("scope-1").join();
            assert history.size() == PublicChatService.MAX_HISTORY_MESSAGES;
            assert history.get(0).content().equals("message-5");
            assert history.get(history.size() - 1).createdAt()
                    .equals(Instant.parse("2030-01-02T03:04:05.123456Z"));
            try {
                chat.publish(sender, "scope-1", oversized,
                        List.of(sender.sessionId()));
                throw new AssertionError("Oversized server chat was accepted");
            } catch (ChatException expected) {
                assert expected.code().equals("INVALID_CHAT_MESSAGE");
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void clientDispatchesChatToSwingEdt() throws Exception {
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher());
        ClientMessageRouter router = new ClientMessageRouter(store, new SwingUiEventDispatcher());
        AtomicReference<GameChatPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(new GameChatPanel()));
        CountDownLatch displayed = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        // PHASE 3 - Added in Phase 3
        store.addChatListener(message -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            panel.get().append(message);
            displayed.countDown();
        });
        ChatMessagePush message = new ChatMessagePush(MessageMetadata.createNow(), "chat-1",
                "match-1", "player-1", "Ada", "hello UI",
                Instant.parse("2030-01-02T03:04:05.123456Z"));
        Thread listener = new Thread(() -> router.onPush(message), "test-client-listener");
        listener.start();
        listener.join(1_000L);
        assert displayed.await(1, TimeUnit.SECONDS) && onEdt.get();
        assert panel.get().messageText().contains(
                "[2030-01-02T03:04:05.123456Z] Ada: hello UI");
    }

    // PHASE 3 - Added in Phase 3
    private static void malformedChatClosesOnlyItsConnection() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (LobbyServerNetworkAdapter adapter = new LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter)) {
            server.start(0);
            try (Socket malformed = new Socket("127.0.0.1", server.localPort())) {
                malformed.setSoTimeout(2_000);
                DataOutputStream output = new DataOutputStream(malformed.getOutputStream());
                DataInputStream input = new DataInputStream(malformed.getInputStream());
                TcpFrameCodec.write(output, serializer.serialize(new HelloRequest(
                        MessageMetadata.createNow(), "Malformed User")),
                        TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
                serializer.deserialize(TcpFrameCodec.read(input,
                        TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES));
                String invalid = serializer.serialize(new SendChatRequest(
                        MessageMetadata.createNow(), "scope-1", "valid"))
                        .replace("\"content\":\"valid\"", "\"content\":[]");
                TcpFrameCodec.write(output, invalid, TcpFrameCodec.DEFAULT_MAX_FRAME_BYTES);
                await(() -> adapter.connectionCount() == 0, "malformed chat cleanup");
            }
            try (NetworkManager survivor = new NetworkManager(serializer)) {
                survivor.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
                assert survivor.send(new HelloRequest(MessageMetadata.createNow(), "Survivor"))
                        .get(2, TimeUnit.SECONDS) instanceof WelcomeResponse;
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void expectProtocolFailure(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid chat JSON was accepted"); }
        catch (ProtocolException expected) {
            assert expected.code() == ProtocolException.Code.INVALID_MESSAGE;
        }
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
    private record MatchFacts(long stateVersion, long turnNumber, String activePlayerId,
                              Map<String, Integer> actionPoints) {}

    // PHASE 3 - Added in Phase 3
    private static final class Capture implements ClientMessageListener {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<PushMessage> pushes = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final AtomicReference<String> listenerThread = new AtomicReference<>("");
        // PHASE 3 - Added in Phase 3
        @Override public void onPush(PushMessage message) {
            if (message instanceof ChatMessagePush) listenerThread.set(Thread.currentThread().getName());
            pushes.add(message);
        }
        // PHASE 3 - Added in Phase 3
        @Override public void onConnectionStateChanged(NetworkConnectionState state, Throwable cause) {}
        // PHASE 3 - Added in Phase 3
        private long chatCount(String scope) { return pushes.stream().filter(push -> push instanceof ChatMessagePush chat && chat.scopeId().equals(scope)).count(); }
        // PHASE 3 - Added in Phase 3
        private ChatMessagePush latestChat(String scope) { return (ChatMessagePush) pushes.stream().filter(push -> push instanceof ChatMessagePush chat && chat.scopeId().equals(scope)).reduce((first, second) -> second).orElseThrow(); }
        // PHASE 3 - Added in Phase 3
        private String listenerThread() { return listenerThread.get(); }
    }

    // PHASE 3 - Added in Phase 3
    private record ConnectedClient(NetworkManager manager, Capture capture,
                                   ClientSession session) {}

    // PHASE 3 - Added in Phase 3
    private static final class NetworkFixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final JsonMessageSerializer serializer = new JsonMessageSerializer();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry();
        // PHASE 3 - Added in Phase 3
        private final LobbyServerNetworkAdapter adapter =
                new LobbyServerNetworkAdapter(sessions, serializer);
        // PHASE 3 - Added in Phase 3
        private final TcpServer server = new TcpServer(serializer, adapter);
        // PHASE 3 - Added in Phase 3
        private final List<NetworkManager> clients = new CopyOnWriteArrayList<>();

        // PHASE 3 - Added in Phase 3
        private NetworkFixture() throws Exception { server.start(0); }

        // PHASE 3 - Added in Phase 3
        private ConnectedClient connect(String username) throws Exception {
            NetworkManager manager = new NetworkManager(serializer);
            Capture capture = new Capture();
            manager.addListener(capture);
            clients.add(manager);
            manager.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
            WelcomeResponse welcome = (WelcomeResponse) manager.send(new HelloRequest(
                    MessageMetadata.createNow(), username)).get(2, TimeUnit.SECONDS);
            return new ConnectedClient(manager, capture, sessions.find(
                    SessionId.parse(welcome.sessionId())).orElseThrow());
        }

        // PHASE 3 - Added in Phase 3
        private String startMatch(ClientSession host, ClientSession guest, String lobby) {
            adapter.lobbyService().selectMap(host, lobby, "emerald-plains").join();
            adapter.lobbyService().ready(host, lobby, true).join();
            adapter.lobbyService().ready(guest, lobby, true).join();
            return adapter.lobbyService().start(host, lobby).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private MatchFacts facts(String matchId, ClientSession player) {
            return adapter.lobbyService().match(matchId).project(state -> new MatchFacts(
                    state.stateVersion(), state.turn().turnNumber(),
                    state.activePlayerId().toString(), state.requirePlayer(player.playerId())
                    .empire().units().stream().collect(java.util.stream.Collectors.toMap(
                            unit -> unit.id().value().toString(),
                            unit -> unit.actionPoints().current())))).join();
        }

        // PHASE 3 - Added in Phase 3
        @Override public void close() {
            clients.forEach(NetworkManager::close);
            try { server.closeAndAwait(Duration.ofSeconds(2)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            adapter.close();
        }
    }
}
