package civilization.view.animation;

import civilization.application.event.UnitMovedEvent;
import civilization.model.unit.UnitId;
import civilization.view.render.HexLayout;
import civilization.view.render.WorldPoint;

import javax.swing.Timer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Swing-timer animation state. It never receives or mutates GameState. */
public final class AnimationManager {
    public static final long DEFAULT_MOVE_DURATION_NANOS = 280_000_000L;

    private final HexLayout layout;
    private final Runnable repaintRequest;
    private final LongSupplier nanoTime;
    private final long moveDurationNanos;
    private final Map<UnitId, UnitMoveAnimation> activeMoves = new LinkedHashMap<>();
    private final Timer timer;
    private Runnable animationStateChanged = () -> { };

    public AnimationManager(HexLayout layout, Runnable repaintRequest) {
        this(layout, repaintRequest, System::nanoTime, DEFAULT_MOVE_DURATION_NANOS, 16);
    }

    public AnimationManager(
            HexLayout layout,
            Runnable repaintRequest,
            LongSupplier nanoTime,
            long moveDurationNanos,
            int frameDelayMillis) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.repaintRequest = Objects.requireNonNull(repaintRequest, "repaintRequest");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (moveDurationNanos <= 0 || frameDelayMillis <= 0) {
            throw new IllegalArgumentException("Animation timing must be positive");
        }
        this.moveDurationNanos = moveDurationNanos;
        timer = new Timer(frameDelayMillis, event -> tick());
        timer.setCoalesce(true);
    }

    public void start(UnitMovedEvent event) {
        Objects.requireNonNull(event, "event");
        if (activeMoves.containsKey(event.unitId())) {
            throw new IllegalStateException("Unit is already moving visually");
        }
        long now = nanoTime.getAsLong();
        activeMoves.put(event.unitId(), new UnitMoveAnimation(
                event.unitId(),
                layout.center(event.from()),
                layout.center(event.to()),
                now,
                moveDurationNanos,
                event.stateVersion()));
        if (!timer.isRunning()) {
            timer.start();
        }
        animationStateChanged.run();
        repaintRequest.run();
    }

    public void startIfCurrent(UnitMovedEvent event, long displayedStateVersion) {
        Objects.requireNonNull(event, "event");
        if (event.stateVersion() != displayedStateVersion) {
            throw new IllegalArgumentException("Movement event does not match the displayed state version");
        }
        start(event);
    }

    public Optional<WorldPoint> visualPosition(UnitId unitId) {
        UnitMoveAnimation move = activeMoves.get(Objects.requireNonNull(unitId, "unitId"));
        return move == null ? Optional.empty() : Optional.of(move.positionAt(nanoTime.getAsLong()));
    }

    public boolean isAnimating(UnitId unitId) {
        return activeMoves.containsKey(Objects.requireNonNull(unitId, "unitId"));
    }

    public boolean hasActiveAnimations() {
        return !activeMoves.isEmpty();
    }

    public boolean acceptsInput() {
        return activeMoves.isEmpty();
    }

    public void setAnimationStateChanged(Runnable animationStateChanged) {
        this.animationStateChanged = Objects.requireNonNull(
                animationStateChanged, "animationStateChanged");
    }

    public void cancelAll() {
        boolean changed = !activeMoves.isEmpty();
        activeMoves.clear();
        timer.stop();
        if (changed) {
            animationStateChanged.run();
        }
        repaintRequest.run();
    }

    /** Public for deterministic tests; normal execution reaches it through the Swing Timer. */
    public void tick() {
        long now = nanoTime.getAsLong();
        boolean wasAnimating = !activeMoves.isEmpty();
        activeMoves.values().removeIf(animation -> animation.isCompleteAt(now));
        if (activeMoves.isEmpty()) {
            timer.stop();
        }
        if (wasAnimating && activeMoves.isEmpty()) {
            animationStateChanged.run();
        }
        repaintRequest.run();
    }
}
