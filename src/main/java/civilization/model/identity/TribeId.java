package civilization.model.identity;

import java.util.Objects;

public record TribeId(String value) implements Comparable<TribeId>, java.io.Serializable {
    public TribeId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Tribe ID cannot be blank");
        }
    }

    public FactionId asFactionId() {
        return new FactionId("tribe:" + value);
    }

    @Override
    public int compareTo(TribeId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
