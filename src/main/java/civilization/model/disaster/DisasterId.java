package civilization.model.disaster;

import java.util.Objects;

public record DisasterId(String value) implements Comparable<DisasterId>, java.io.Serializable {
    public DisasterId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Disaster ID cannot be blank");
    }
    @Override public int compareTo(DisasterId other) { return value.compareTo(other.value); }
}
