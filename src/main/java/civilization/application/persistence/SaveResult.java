package civilization.application.persistence;

import java.util.Objects;
import java.util.Optional;

public record SaveResult(Status status, Optional<SaveMetadata> metadata, Optional<String> error) {
    public enum Status { SAVED, DECLINED, FAILED }
    public SaveResult {
        Objects.requireNonNull(status, "status");
        metadata = Objects.requireNonNull(metadata, "metadata");
        error = Objects.requireNonNull(error, "error");
    }
    public static SaveResult saved(SaveMetadata metadata) {
        return new SaveResult(Status.SAVED, Optional.of(metadata), Optional.empty());
    }
    public static SaveResult declined() {
        return new SaveResult(Status.DECLINED, Optional.empty(), Optional.empty());
    }
    public static SaveResult failed(String error) {
        return new SaveResult(Status.FAILED, Optional.empty(), Optional.of(error));
    }
}
