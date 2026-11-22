package civilization.model.capacity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Named capacity pools with source-addressable modifiers for Hall/building/technology effects. */
public final class CapacityState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<CapacityKey, Integer> baseLimits = new LinkedHashMap<>();
    private final Map<CapacityKey, Map<String, Integer>> modifiers = new HashMap<>();

    public void define(CapacityKey key, int baseLimit) {
        Objects.requireNonNull(key, "key");
        if (baseLimit < 0) {
            throw new IllegalArgumentException("Base capacity cannot be negative");
        }
        if (baseLimits.putIfAbsent(key, baseLimit) != null) {
            throw new IllegalStateException("Capacity is already defined: " + key.value());
        }
    }

    public void setModifier(CapacityKey key, String sourceId, int amount) {
        requireDefined(key);
        sourceId = Objects.requireNonNull(sourceId, "sourceId").trim();
        if (sourceId.isEmpty()) {
            throw new IllegalArgumentException("Capacity modifier source cannot be blank");
        }
        Map<String, Integer> bySource = modifiers.computeIfAbsent(key, ignored -> new HashMap<>());
        if (amount == 0) {
            bySource.remove(sourceId);
        } else {
            bySource.put(sourceId, amount);
        }
        if (limit(key) < 0) {
            bySource.remove(sourceId);
            throw new IllegalArgumentException("Capacity modifiers cannot make the limit negative");
        }
    }

    public int limit(CapacityKey key) {
        int base = requireDefined(key);
        int result = base;
        for (int modifier : modifiers.getOrDefault(key, Map.of()).values()) {
            result = Math.addExact(result, modifier);
        }
        return result;
    }

    public boolean allows(CapacityKey key, int currentCount, int additionalCount) {
        if (currentCount < 0 || additionalCount < 0) {
            throw new IllegalArgumentException("Capacity counts cannot be negative");
        }
        return Math.addExact(currentCount, additionalCount) <= limit(key);
    }

    public Map<String, Integer> modifiers(CapacityKey key) {
        requireDefined(key);
        return Map.copyOf(modifiers.getOrDefault(key, Map.of()));
    }

    private int requireDefined(CapacityKey key) {
        Integer base = baseLimits.get(Objects.requireNonNull(key, "key"));
        if (base == null) {
            throw new IllegalArgumentException("Unknown capacity: " + key.value());
        }
        return base;
    }
}
