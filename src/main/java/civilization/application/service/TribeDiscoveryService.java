package civilization.application.service;

import civilization.application.event.EventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.notification.NotificationCategory;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TribeDiscoveryService {
    private final EventBus eventBus;

    public TribeDiscoveryService() { this(EventBus.noOp()); }
    public TribeDiscoveryService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public List<TribeId> refresh(GameState state) {
        List<TribeId> discovered = new ArrayList<>();
        for (var tribe : state.world().tribes().all()) {
            if (!tribe.isDefeated()
                    && state.player().visibility().isCurrentlyVisible(tribe.camp().position())
                    && tribe.discover()) {
                discovered.add(tribe.id());
                eventBus.publish(new TribeNotificationEvent(tribe.id(), state.turn().turnNumber(),
                        NotificationCategory.DIPLOMACY,
                        "Discovered " + tribe.name(), Optional.of(tribe.camp().position())));
            }
        }
        return List.copyOf(discovered);
    }

    public boolean canInteract(GameState state, TribeId tribeId) {
        var tribe = state.world().tribes().require(tribeId);
        return tribe.isDiscovered() && !tribe.isDefeated()
                && state.player().visibility().isCurrentlyVisible(tribe.camp().position());
    }
}
