package civilization.application.event;

@FunctionalInterface
public interface GameEventListener<T extends GameEvent> {
    void onEvent(T event);
}
