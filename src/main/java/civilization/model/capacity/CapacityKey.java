package civilization.model.capacity;

import java.util.Objects;

public record CapacityKey(String value) implements java.io.Serializable {
    public static final CapacityKey TOTAL_UNITS = new CapacityKey("total-units");
    public static final CapacityKey MILITARY_UNITS = new CapacityKey("military-units");

    public CapacityKey {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Capacity key cannot be blank");
        }
    }
}
