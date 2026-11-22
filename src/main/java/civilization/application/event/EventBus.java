package civilization.application.event;

public interface EventBus {
    <T extends GameEvent> Subscription subscribe(Class<T> eventType, GameEventListener<? super T> listener);
    EventDispatchResult publish(GameEvent event);

    static EventBus noOp() { return NoOpEventBus.INSTANCE; }
}
