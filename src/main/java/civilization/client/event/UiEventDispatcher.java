package civilization.client.event;

// PHASE 3 - Added in Phase 3
@FunctionalInterface
public interface UiEventDispatcher {
    // PHASE 3 - Added in Phase 3
    void dispatch(Runnable task);
}
