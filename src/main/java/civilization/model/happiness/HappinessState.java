package civilization.model.happiness;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Accumulated, idempotent event ledger; conditional sources are added in later slices. */
public final class HappinessState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Integer> appliedEvents = new LinkedHashMap<>();
    private int accumulatedValue;

    public HappinessState() { }

    /** Persistence constructor; the ledger is authoritative and prevents load-time replay. */
    public HappinessState(int accumulatedValue, Map<String, Integer> appliedEvents) {
        this.appliedEvents.putAll(Map.copyOf(Objects.requireNonNull(appliedEvents, "appliedEvents")));
        int ledgerTotal = this.appliedEvents.values().stream().mapToInt(Integer::intValue).sum();
        if (ledgerTotal != accumulatedValue) {
            throw new IllegalArgumentException("Accumulated Happiness must equal its event ledger");
        }
        this.accumulatedValue = accumulatedValue;
    }

    public int accumulatedValue() { return accumulatedValue; }

    public boolean applyOnce(String eventId, int delta) {
        eventId = Objects.requireNonNull(eventId, "eventId").trim();
        if (eventId.isEmpty()) throw new IllegalArgumentException("Happiness event ID cannot be blank");
        if (appliedEvents.containsKey(eventId)) return false;
        accumulatedValue = Math.addExact(accumulatedValue, delta);
        appliedEvents.put(eventId, delta);
        return true;
    }

    public Map<String, Integer> appliedEvents() {
        return Collections.unmodifiableMap(appliedEvents);
    }
}
