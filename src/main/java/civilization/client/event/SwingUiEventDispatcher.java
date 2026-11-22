package civilization.client.event;

import javax.swing.SwingUtilities;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class SwingUiEventDispatcher implements UiEventDispatcher {
    // PHASE 3 - Added in Phase 3
    @Override
    public void dispatch(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }
}
