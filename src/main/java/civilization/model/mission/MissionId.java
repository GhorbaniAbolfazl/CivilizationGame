package civilization.model.mission;

import java.util.Objects;

public record MissionId(String value) implements Comparable<MissionId>, java.io.Serializable {
    public MissionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Mission ID cannot be blank");
    }
    @Override public int compareTo(MissionId other) { return value.compareTo(other.value); }
}
