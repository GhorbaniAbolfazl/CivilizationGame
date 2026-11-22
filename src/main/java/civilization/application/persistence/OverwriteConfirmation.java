package civilization.application.persistence;

@FunctionalInterface
public interface OverwriteConfirmation {
    boolean confirm(SaveSlot slot, SaveMetadata existing, SaveMetadata replacement);
}
