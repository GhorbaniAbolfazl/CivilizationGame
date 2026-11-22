package civilization.client.controller;

import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.net.NetworkConnectionState;
import civilization.client.net.NetworkManager;
import civilization.client.state.ClientStateStore;
import civilization.protocol.ErrorResponse;
import civilization.protocol.ResponseMessage;
import civilization.protocol.ResumeAcceptedResponse;
import civilization.protocol.WelcomeResponse;
import civilization.view.lobby.LobbyPanel;
import civilization.view.lobby.MultiplayerConnectPanel;
import civilization.view.shell.MainFrame;

import javax.swing.SwingUtilities;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// PHASE 3 - Added in Phase 3
public final class MultiplayerClientCoordinator implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final MainFrame frame;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerConnectPanel connectPanel;
    // PHASE 3 - Added in Phase 3
    private NetworkManager network;
    // PHASE 3 - Added in Phase 3
    private LobbyPanel lobbyPanel;
    // PHASE 3 - Added in Phase 3
    private LobbyController lobbyController;
    // PHASE 3 - Added in Phase 3
    private LobbyToGameCoordinator gameCoordinator;
    // PHASE 3 - Added in Phase 3
    private String serverHost;
    // PHASE 3 - Added in Phase 3
    private int serverPort;
    // PHASE 3 - Added in Phase 3
    private String sessionId;
    // PHASE 3 - Added in Phase 3
    private String resumeSecret;
    // PHASE 3 - Added in Phase 3
    private int udpPort;
    // PHASE 3 - Added in Phase 3
    private boolean reconnecting;

    // PHASE 3 - Added in Phase 3
    public MultiplayerClientCoordinator(MainFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        connectPanel = frame.multiplayerConnect();
        connectPanel.setCreateAction(() -> connect(true));
        connectPanel.setJoinAction(() -> connect(false));
        connectPanel.setBackAction(this::returnToMenu);
    }

    // PHASE 3 - Added in Phase 3
    public void show() {
        requireEdt();
        connectPanel.setBusy(false, "Enter a server address and player name.");
        frame.showCard(MainFrame.MULTIPLAYER_CONNECT);
    }

    // PHASE 3 - Added in Phase 3
    private void connect(boolean createLobby) {
        requireEdt();
        if (connectPanel.username().isBlank() || connectPanel.host().isBlank()) {
            connectPanel.setBusy(false, "Username and server host are required.");
            return;
        }
        closeNetwork();
        serverHost = connectPanel.host(); serverPort = connectPanel.port();
        connectPanel.setBusy(true, "Connecting without blocking the UI…");
        NetworkManager manager = new NetworkManager();
        network = manager;
        SwingUiEventDispatcher dispatcher = new SwingUiEventDispatcher();
        ClientStateStore store = new ClientStateStore(dispatcher);
        ClientMessageRouter router = new ClientMessageRouter(store, dispatcher);
        manager.addListener(router);
        router.addConnectionListener(this::onConnectionState);
        ClientSessionController sessions = new ClientSessionController(manager);
        // PHASE 3 - Added in Phase 3
        manager.connect(serverHost, serverPort)
                .thenCompose(ignored -> sessions.requestSession(connectPanel.username()))
                .thenCompose(response -> authenticate(response, store, createLobby))
                .whenComplete((response, failure) -> {
                    if (failure != null) dispatch(() -> {
                        closeNetwork();
                        connectPanel.setBusy(false, "Connection failed: " + rootMessage(failure));
                    });
                    // PHASE 3 - Added in Phase 3
                    else if (response instanceof ErrorResponse error) dispatch(() -> {
                        closeNetwork();
                        // PHASE 3 - Added in Phase 3
                        connectPanel.setBusy(false, error.message());
                    });
                });
    }

    // PHASE 3 - Added in Phase 3
    private CompletableFuture<ResponseMessage> authenticate(ResponseMessage response,
                                                              ClientStateStore store,
                                                              boolean createLobby) {
        if (response instanceof ErrorResponse error) {
            return CompletableFuture.failedFuture(new IllegalStateException(error.message()));
        }
        if (!(response instanceof WelcomeResponse welcome)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Server did not return a welcome response"));
        }
        sessionId = welcome.sessionId(); resumeSecret = welcome.resumeSecret(); udpPort = welcome.udpPort();
        startHeartbeat();
        // PHASE 3 - Added in Phase 3
        return onEdt(() -> installLobby(store, welcome.playerId()))
                .thenCompose(ignored -> createLobby ? lobbyController.create()
                        : lobbyController.join(connectPanel.lobbyId()));
    }

    // PHASE 3 - Added in Phase 3
    private void installLobby(ClientStateStore store, String playerId) {
        requireEdt();
        lobbyController = new LobbyController(network);
        ChatController chat = new ChatController(network);
        lobbyPanel = new LobbyPanel(playerId,
                (id, ready) -> submit("Ready update", () -> lobbyController.setReady(id, ready)),
                (id, map) -> submit("Map selection", () -> lobbyController.selectMap(id, map)),
                id -> submit("Game Start", () -> lobbyController.start(id)),
                (id, content) -> submit("Lobby chat", () -> lobbyController.chat(id, content)));
        lobbyPanel.setReconnectAction(this::reconnect);
        lobbyPanel.setLeaveAction(this::leaveLobby);
        new LobbyUiCoordinator(store, lobbyPanel, ignored -> { });
        // PHASE 3 - Added in Phase 3
        gameCoordinator = new LobbyToGameCoordinator(store,
                new MainFrameGameplayNavigator(frame), new MultiplayerGameplayPresenter(),
                new MultiplayerTurnController(network), chat,
                new MultiplayerTradeController(network),
                new MultiplayerDiplomacyController(network),
                new MultiplayerHallTownController(network),
                new MultiplayerItemController(network),
                new MultiplayerCombatController(network),
                new MultiplayerMovementController(network),
                new MultiplayerConstructionController(network));
        frame.installLobby(lobbyPanel); frame.showCard(MainFrame.LOBBY);
        lobbyPanel.setConnectionState("CONNECTED", false);
    }

    // PHASE 3 - Added in Phase 3
    private void submit(String operation, Supplier<CompletableFuture<ResponseMessage>> action) {
        CompletableFuture<ResponseMessage> future;
        try { future = action.get(); }
        catch (RuntimeException failure) { showError(operation, failure); return; }
        // PHASE 3 - Added in Phase 3
        future.whenComplete((response, failure) -> dispatch(() -> {
            if (failure != null) showError(operation, failure);
            else if (response instanceof ErrorResponse error && lobbyPanel != null) {
                // PHASE 3 - Added in Phase 3
                lobbyPanel.notifyUser(error.message());
            }
        }));
    }

    // PHASE 3 - Added in Phase 3
    private void reconnect() {
        requireEdt();
        if (network == null || sessionId == null || resumeSecret == null) return;
        reconnecting = true;
        updateConnectionUi("RECONNECTING", false);
        // PHASE 3 - Added in Phase 3
        network.reconnect(serverHost, serverPort, sessionId, resumeSecret)
                .whenComplete((response, failure) -> {
                    if (failure != null) dispatch(() -> {
                        reconnecting = false;
                        updateConnectionUi("DISCONNECTED: " + rootMessage(failure), true);
                    });
                    else if (response instanceof ResumeAcceptedResponse resumed) {
                        udpPort = resumed.udpPort(); startHeartbeat();
                        dispatch(() -> { reconnecting = false; updateConnectionUi("CONNECTED", false); });
                    } else if (response instanceof ErrorResponse error) {
                        dispatch(() -> { reconnecting = false; updateConnectionUi("RECONNECT REJECTED: " + error.message(), true); });
                    }
                });
    }

    // PHASE 3 - Added in Phase 3
    private void startHeartbeat() {
        if (udpPort <= 0 || network == null || sessionId == null) return;
        try {
            network.startHeartbeat(new InetSocketAddress(serverHost, udpPort), sessionId,
                    Duration.ofSeconds(1));
        } catch (java.io.IOException | RuntimeException ignored) {
            // PHASE 3 - Added in Phase 3
            // UDP is liveness-only; TCP remains authoritative and usable.
        }
    }

    // PHASE 3 - Added in Phase 3
    private void onConnectionState(NetworkConnectionState state, Throwable cause) {
        String label = state.name() + (cause == null ? "" : ": " + rootMessage(cause));
        if (state == NetworkConnectionState.CONNECTED && reconnecting) label = "AUTHENTICATING";
        updateConnectionUi(label, state == NetworkConnectionState.DISCONNECTED
                && sessionId != null && resumeSecret != null);
    }

    // PHASE 3 - Added in Phase 3
    private void updateConnectionUi(String label, boolean mayReconnect) {
        requireEdt();
        boolean available = label.equals("CONNECTED");
        if (lobbyPanel != null) lobbyPanel.setConnectionState(label, available, mayReconnect);
        if (gameCoordinator != null) {
            gameCoordinator.setNetworkAvailable(available, label,
                    mayReconnect ? this::reconnect : null);
        }
    }

    // PHASE 3 - Added in Phase 3
    private void leaveLobby() {
        requireEdt();
        String lobbyId = lobbyPanel == null ? null : lobbyPanel.lobbyId();
        if (lobbyController != null && lobbyId != null) {
            lobbyController.leave(lobbyId).whenComplete((ignored, failure) -> {
                NetworkManager existing = network;
                if (existing != null) existing.close();
            });
            network = null;
        }
        lobbyPanel = null; lobbyController = null; gameCoordinator = null;
        frame.closeLobby(); frame.showCard(MainFrame.MENU);
    }

    // PHASE 3 - Added in Phase 3
    private void showError(String operation, Throwable failure) {
        requireEdt();
        String message = operation + " failed: " + rootMessage(failure);
        if (lobbyPanel != null) lobbyPanel.notifyUser(message);
        else connectPanel.setBusy(false, message);
    }

    // PHASE 3 - Added in Phase 3
    private void returnToMenu() {
        requireEdt(); closeNetwork(); connectPanel.setBusy(false,
                "Enter a server address and player name.");
        frame.closeLobby(); frame.showCard(MainFrame.MENU);
    }

    // PHASE 3 - Added in Phase 3
    private void closeNetwork() {
        NetworkManager existing = network; network = null;
        if (existing != null) existing.close();
        lobbyPanel = null; lobbyController = null; gameCoordinator = null;
        sessionId = null; resumeSecret = null; udpPort = 0; reconnecting = false;
    }

    // PHASE 3 - Added in Phase 3
    private static CompletableFuture<Void> onEdt(Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        dispatch(() -> {
            try { action.run(); result.complete(null); }
            catch (RuntimeException failure) { result.completeExceptionally(failure); }
        });
        return result;
    }

    // PHASE 3 - Added in Phase 3
    private static void dispatch(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action);
    }

    // PHASE 3 - Added in Phase 3
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    // PHASE 3 - Added in Phase 3
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("UI coordination must run on EDT");
    }

    // PHASE 3 - Added in Phase 3
    @Override public void close() { closeNetwork(); }
}
