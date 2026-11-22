package civilization.phase3;

import civilization.client.ClientCommandPort;
import civilization.client.controller.*;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientStateStore;
import civilization.protocol.*;
import civilization.view.game.GamePanel;
import civilization.view.lobby.LobbyPanel;
import civilization.view.lobby.MultiplayerConnectPanel;

import javax.swing.SwingUtilities;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// PHASE 3 - Added in Phase 3
public final class Phase3SwingIntegrationTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3SwingIntegrationTestSuite() { }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        lobbyExplainsDisabledActions(); gameplayWiringIsEdtSafeAndNonBlocking();
        connectionFormExplainsJoinRequirement();
        System.out.println("Phase3SwingIntegrationTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void lobbyExplainsDisabledActions() throws Exception {
        AtomicReference<LobbyPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            LobbyPanel panel = new LobbyPanel("guest", (id, ready) -> { },
                    (id, map) -> { }, id -> { }, (id, text) -> { });
            panel.apply(new LobbyStatePush(MessageMetadata.createNow(), 1,
                    new LobbySnapshotDto("lobby", "host", "emerald-plains", "OPEN",
                            Instant.now(), List.of(
                            new LobbyMemberDto("host", "Host", Instant.now(), true, true),
                            new LobbyMemberDto("guest", "Guest", Instant.now(), false, true)),
                            List.of(new MapSummaryDto("emerald-plains", "Emerald Plains", 2, 4)))));
            assert !panel.mapSelector().isEnabled();
            assert panel.mapSelector().getToolTipText().contains("host");
            assert !panel.startButton().isEnabled();
            assert panel.startButton().getToolTipText().contains("host");
            assert !panel.reconnectButton().isVisible();
            panel.setConnectionState("DISCONNECTED", true);
            assert panel.reconnectButton().isVisible();
            assert !panel.readyButton().isEnabled();
            assert panel.readyButton().getToolTipText().contains("Reconnect");
            reference.set(panel);
        });
        assert reference.get() != null;
    }

    // PHASE 3 - Added in Phase 3
    private static void gameplayWiringIsEdtSafeAndNonBlocking() throws Exception {
        CapturingPort port = new CapturingPort();
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher());
        AtomicReference<GamePanel> shown = new AtomicReference<>();
        AtomicBoolean openedOnEdt = new AtomicBoolean();
        LobbyToGameCoordinator coordinator = new LobbyToGameCoordinator(store, panel -> {
            shown.set(panel); openedOnEdt.set(SwingUtilities.isEventDispatchThread());
        }, new MultiplayerGameplayPresenter(), new MultiplayerTurnController(port),
                new ChatController(port), new MultiplayerTradeController(port),
                new MultiplayerDiplomacyController(port), new MultiplayerHallTownController(port),
                new MultiplayerItemController(port), new MultiplayerCombatController(port));
        store.apply(new GameStartedPush(MessageMetadata.createNow(), "lobby", "match",
                "emerald-plains", 1));
        Thread listener = new Thread(() -> store.apply(new GameStatePush(MessageMetadata.createNow(),
                1, snapshot("me"))), "phase3-ui-test-listener");
        listener.start(); listener.join();
        SwingUtilities.invokeAndWait(() -> { });
        GamePanel panel = shown.get();
        assert panel != null && openedOnEdt.get();
        SwingUtilities.invokeAndWait(() -> {
            assert panel.hud().endTurnButton().isEnabled();
            assert panel.multiplayerHallTown().establishButton().getToolTipText() != null;
            assert panel.multiplayerItems().craftButton().getToolTipText() != null;
            assert panel.playerTrade().createButton().getToolTipText() != null;
            panel.combat().multiplayerAttackerList().setSelectedIndex(0);
            long before = System.nanoTime();
            panel.combat().multiplayerAttackButton().doClick();
            long elapsedMillis = (System.nanoTime() - before) / 1_000_000L;
            assert elapsedMillis < 500 : "EDT waited for network response";
        });
        assert port.last.get() instanceof AttackRequest;
        AttackRequest request = (AttackRequest) port.last.get();
        assert request.attackerUnitIds().equals(List.of("00000000-0000-0000-0000-000000000001"));
        ErrorResponse rejected = new ErrorResponse(MessageMetadata.createNow(),
                java.util.UUID.randomUUID(), "NOT_ACTIVE_PLAYER", "It is not your turn", false);
        port.next.set(CompletableFuture.completedFuture(rejected));
        SwingUtilities.invokeAndWait(() -> panel.hud().endTurnButton().doClick());
        SwingUtilities.invokeAndWait(() -> { });
        assert panel.notificationHistory().contains("It is not your turn");
        SwingUtilities.invokeAndWait(() -> {
            coordinator.setNetworkAvailable(false, "DISCONNECTED", () -> { });
            assert !panel.hud().endTurnButton().isEnabled();
            assert panel.hud().endTurnButton().getToolTipText().contains("Reconnect");
            assert !panel.chat().sendButton().isEnabled();
        });
    }

    // PHASE 3 - Added in Phase 3
    private static GameSnapshotDto snapshot(String activePlayer) {
        return new GameSnapshotDto("match", 1, activePlayer, "ACTIVE", "emerald-plains", "me",
                Map.of("FOOD", 20, "WOOD", 20, "STONE", 10, "IRON", 5),
                List.of(new VisibleHexDto(0, 0, true, true, "PLAINS", Map.of()),
                        new VisibleHexDto(1, 0, true, false, "PLAINS", Map.of())),
                List.of(new VisibleUnitDto("00000000-0000-0000-0000-000000000001",
                                "SWORDSMAN", "me", 0, 0, 2),
                        new VisibleUnitDto("00000000-0000-0000-0000-000000000002",
                                "ARCHER", "other", 1, 0, 2)),
                List.of(new VisibleBuildingDto("00000000-0000-0000-0000-000000000003",
                        "TOWN_HALL", "me", 0, 0, "ACTIVE")),
                List.of(), List.of(), Map.of("username", "Me"));
    }

    // PHASE 3 - Added in Phase 3
    private static void connectionFormExplainsJoinRequirement() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MultiplayerConnectPanel panel = new MultiplayerConnectPanel();
            assert !panel.joinButton().isEnabled();
            assert panel.joinButton().getToolTipText().contains("lobby ID");
            assert panel.createButton().isEnabled();
        });
    }

    // PHASE 3 - Added in Phase 3
    private static final class CapturingPort implements ClientCommandPort {
        // PHASE 3 - Added in Phase 3
        private final AtomicReference<RequestMessage> last = new AtomicReference<>();
        // PHASE 3 - Added in Phase 3
        private final AtomicReference<CompletableFuture<ResponseMessage>> next =
                new AtomicReference<>();
        // PHASE 3 - Added in Phase 3
        @Override public CompletableFuture<ResponseMessage> send(RequestMessage request) {
            last.set(request);
            CompletableFuture<ResponseMessage> response = next.getAndSet(null);
            return response == null ? new CompletableFuture<>() : response;
        }
    }
}
