package civilization.model.random;

import java.util.Objects;

public record GameplayRandomSnapshot(String algorithm, int version, long state) implements java.io.Serializable {
    public GameplayRandomSnapshot {
        algorithm = Objects.requireNonNull(algorithm, "algorithm").trim();
        if (algorithm.isEmpty() || version <= 0) {
            throw new IllegalArgumentException("Random snapshot algorithm/version is invalid");
        }
    }
}
