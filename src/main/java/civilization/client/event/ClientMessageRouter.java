package civilization.client.event;

import civilization.client.net.ClientMessageListener;
import civilization.client.net.NetworkConnectionState;
import civilization.client.state.ClientStateStore;
import civilization.protocol.PushMessage;
import civilization.protocol.ChatMessagePush;
import civilization.protocol.GameStartedPush;
import civilization.protocol.GameStatePush;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.ConnectionStatusPush;
import civilization.protocol.NotificationPush;
import civilization.protocol.BattleReportPush;
import civilization.protocol.TradeOfferPush;
import civilization.protocol.AllianceRequestPush;
import civilization.protocol.DiplomacyStatePush;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

// PHASE 3 - Added in Phase 3
public final class ClientMessageRouter implements ClientMessageListener {
    // PHASE 3 - Added in Phase 3
    private final ClientStateStore stateStore;
    // PHASE 3 - Added in Phase 3
    private final UiEventDispatcher uiDispatcher;
    // PHASE 3 - Added in Phase 3
    private final CopyOnWriteArrayList<BiConsumer<NetworkConnectionState, Throwable>>
            connectionListeners = new CopyOnWriteArrayList<>();

    // PHASE 3 - Added in Phase 3
    public ClientMessageRouter(ClientStateStore stateStore, UiEventDispatcher uiDispatcher) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void onPush(PushMessage message) {
        // PHASE 3 - Added in Phase 3
        if (message instanceof LobbyStatePush update) stateStore.apply(update);
        else if (message instanceof ChatMessagePush update) stateStore.apply(update);
        else if (message instanceof GameStartedPush update) stateStore.apply(update);
        else if (message instanceof GameStatePush update) stateStore.apply(update);
        else if (message instanceof ConnectionStatusPush update) stateStore.apply(update);
        else if (message instanceof NotificationPush update) stateStore.apply(update);
        else if (message instanceof BattleReportPush update) stateStore.apply(update);
        else if (message instanceof TradeOfferPush update) stateStore.apply(update);
        else if (message instanceof DiplomacyStatePush update) stateStore.apply(update);
        else if (message instanceof AllianceRequestPush update) stateStore.apply(update);
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void onConnectionStateChanged(NetworkConnectionState state, Throwable cause) {
        uiDispatcher.dispatch(() -> {
            // PHASE 3 - Added in Phase 3
            for (var listener : connectionListeners) listener.accept(state, cause);
        });
    }

    // PHASE 3 - Added in Phase 3
    public void addConnectionListener(
            BiConsumer<NetworkConnectionState, Throwable> listener) {
        connectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
