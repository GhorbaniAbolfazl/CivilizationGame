package civilization.application.turn;

import civilization.application.event.DisasterResolvedEvent;
import civilization.application.event.EventBus;
import civilization.application.service.DisasterResolver;
import civilization.application.service.DisasterSelector;
import civilization.application.service.DisasterNotificationProjector;
import java.util.Objects;

/** One deterministic chance and complete resolution at the start of every new turn. */
public final class DisasterTurnStep implements TurnStep {
    private final DisasterSelector selector;
    private final DisasterResolver resolver;
    private final EventBus events;
    private final DisasterNotificationProjector notifications;

    public DisasterTurnStep(EventBus events) {
        this(new DisasterSelector(), new DisasterResolver(), events);
    }

    public DisasterTurnStep(DisasterSelector selector, DisasterResolver resolver, EventBus events) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.events = Objects.requireNonNull(events, "events");
        this.notifications = new DisasterNotificationProjector();
    }

    @Override public String id() { return "NATURAL_DISASTER"; }

    @Override public void execute(TurnContext context) {
        selector.select(context.state()).ifPresent(selection -> {
            var result = resolver.resolve(context.state(), selection);
            events.publish(new DisasterResolvedEvent(result));
            events.publish(notifications.project(context.state(), result));
        });
    }
}
