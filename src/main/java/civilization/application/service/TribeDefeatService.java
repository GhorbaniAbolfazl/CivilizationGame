package civilization.application.service;

import civilization.application.event.EventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.notification.NotificationCategory;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import civilization.model.map.TerrainType;
import civilization.rules.tribe.TribeStrategyCatalog;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies all aggregate cleanup exactly once when a camp reaches zero HP. */
public final class TribeDefeatService {
    private final TribeStrategyCatalog strategies;
    private final EventBus events;

    public TribeDefeatService() { this(new TribeStrategyCatalog(), EventBus.noOp()); }
    public TribeDefeatService(TribeStrategyCatalog strategies, EventBus events) {
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.events = Objects.requireNonNull(events, "events");
    }

    public void defeat(GameState state, TribeId id) {
        var tribe = state.world().tribes().require(id);
        if (tribe.isDefeated()) return;
        if (!tribe.camp().health().isDepleted()) {
            throw new IllegalStateException("Tribe camp still has HP");
        }
        state.world().missions().unresolvedForTribe(id).ifPresent(mission ->
                mission.cancel(state.turn().turnNumber()));
        for (var unit : state.world().units().all().stream()
                .filter(candidate -> candidate.ownerId().equals(id.asFactionId())).toList()) {
            state.world().units().remove(unit.id());
        }
        Set<civilization.model.map.HexCoord> captured = tribe.territory().stream()
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .collect(Collectors.toSet());
        captured.forEach(state.world().territories()::relinquish);
        state.player().border().claimAll(captured, state.map());
        state.player().townHall().stockpile().credit(
                strategies.require(tribe.type()).conquestLoot());
        tribe.markDefeated();
        events.publish(new TribeNotificationEvent(id, state.turn().turnNumber(),
                NotificationCategory.COMBAT,
                tribe.name() + " camp was captured and converted to an Outpost",
                Optional.of(tribe.camp().position())));
    }
}
