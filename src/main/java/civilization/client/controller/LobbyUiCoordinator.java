package civilization.client.controller;

import civilization.client.state.ClientStateStore;
import civilization.protocol.GameStartedPush;
import civilization.view.lobby.LobbyPanel;
import java.util.Objects;
import java.util.function.Consumer;

// PHASE 3 - Added in Phase 3
public final class LobbyUiCoordinator {
    // PHASE 3 - Added in Phase 3
    public LobbyUiCoordinator(ClientStateStore store, LobbyPanel panel, Consumer<GameStartedPush> gameTransition) {
        Objects.requireNonNull(store); Objects.requireNonNull(panel); Objects.requireNonNull(gameTransition);
        store.addLobbyListener(panel::apply);
        // PHASE 3 - Added in Phase 3
        store.addChatListener(message -> store.lobby()
                .filter(lobby -> lobby.snapshot().lobbyId().equals(message.scopeId()))
                .ifPresent(ignored -> panel.append(message)));
        // PHASE 3 - Added in Phase 3
        store.addNotificationListener(notification -> panel.notifyUser(notification.message()));
        // PHASE 3 - Added in Phase 3
        java.util.concurrent.atomic.AtomicReference<GameStartedPush> pending = new java.util.concurrent.atomic.AtomicReference<>();
        store.addGameStartedListener(pending::set);
        // PHASE 3 - Added in Phase 3
        store.addGameStateListener(state -> {
            GameStartedPush start = pending.get();
            if (start != null && start.matchId().equals(state.snapshot().matchId())) gameTransition.accept(start);
        });
        store.lobby().ifPresent(panel::apply);
        // PHASE 3 - Added in Phase 3
        store.gameStarted().ifPresent(pending::set);
        store.gameState().ifPresent(state -> { GameStartedPush start = pending.get(); if (start != null && start.matchId().equals(state.snapshot().matchId())) gameTransition.accept(start); });
    }
}
