package civilization.application.persistence;

import java.util.Objects;
import java.util.Optional;

public record SaveEntry(SaveSlot slot, Status status, Optional<SaveMetadata> metadata,
        Optional<String> error) {
    public enum Status { EMPTY, VALID, CORRUPTED, INCOMPATIBLE }
    public SaveEntry {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(status, "status");
        metadata = Objects.requireNonNull(metadata, "metadata");
        error = Objects.requireNonNull(error, "error");
    }
    public static SaveEntry empty(SaveSlot slot) {
        return new SaveEntry(slot, Status.EMPTY, Optional.empty(), Optional.empty());
    }
}
